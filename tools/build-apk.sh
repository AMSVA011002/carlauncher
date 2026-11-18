#!/usr/bin/env bash
# Build and verify the sideload APK. Requires JDK 17 and an Android SDK; no keys are committed.
set -euo pipefail
cd "$(dirname "$0")/.."

usage() {
  echo 'Usage: tools/build-apk.sh [--sdk-only]'
  echo 'Default: build with the Gradle wrapper. --sdk-only: use JDK + Android SDK CLI tools directly.'
}

fail() { printf 'APK build: %s\n' "$*" >&2; exit 1; }
case "${1:-}" in
  '') direct=false ;;
  --sdk-only) direct=true ;;
  --help|-h) usage; exit 0 ;;
  *) usage >&2; exit 2 ;;
esac
[[ $# -le 1 ]] || fail 'expected at most one argument'

# Keep SDK and version values in the Android build file for both build modes.
gradle_value() { awk -v key="$1" '$1 == key { gsub(/[\047\042]/, "", $2); print $2; exit }' app/build.gradle; }
app_id=$(gradle_value applicationId)
namespace=$(gradle_value namespace)
version=$(gradle_value versionName)
version_code=$(gradle_value versionCode)
compile_sdk=$(gradle_value compileSdk)
min_sdk=$(gradle_value minSdk)
target_sdk=$(gradle_value targetSdk)
build_tools=$(gradle_value buildToolsVersion)
[[ $app_id && $namespace && $version && $version_code && $compile_sdk && $min_sdk && $target_sdk && $build_tools ]] \
  || fail 'missing Android version or SDK values in app/build.gradle'

sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
[[ -d "$sdk" ]] || fail 'set ANDROID_HOME to the Android SDK directory'
sdk=$(cd "$sdk" && pwd -P)
export ANDROID_HOME="$sdk"
bt="$sdk/build-tools/$build_tools"
for tool in aapt2 apksigner zipalign; do
  [[ -x "$bt/$tool" ]] || fail "missing $tool; install Android SDK Build Tools $build_tools"
done

work=$(mktemp -d)
publish=''
cleanup() { rm -rf -- "$work"; if [[ -n $publish ]]; then rm -rf -- "$publish"; fi; }
trap cleanup EXIT

if "$direct"; then
  android_jar="$sdk/platforms/android-$compile_sdk/android.jar"
  lambda_stubs="$bt/core-lambda-stubs.jar"
  [[ -f "$android_jar" ]] || fail "install Android SDK Platform $compile_sdk"
  [[ -f "$lambda_stubs" && -x "$bt/d8" ]] || fail "incomplete Android SDK Build Tools $build_tools"
  javac=javac
  keytool=keytool
  if [[ -n ${JAVA_HOME:-} ]]; then javac="$JAVA_HOME/bin/javac"; keytool="$JAVA_HOME/bin/keytool"; fi
  command -v "$javac" >/dev/null || fail 'JDK 17 javac is required'
  command -v zip >/dev/null || fail 'zip is required for --sdk-only'
  mkdir -p "$work"/{java,classes,dex}

  # AGP normally sets the package on the merged manifest. Do the same in the CLI build.
  sed "s/<manifest /<manifest package=\"$app_id\" /" app/src/main/AndroidManifest.xml > "$work/AndroidManifest.xml"
  "$bt/aapt2" compile --dir app/src/main/res -o "$work/resources.zip"
  "$bt/aapt2" link --manifest "$work/AndroidManifest.xml" --custom-package "$namespace" \
    -I "$android_jar" --min-sdk-version "$min_sdk" --target-sdk-version "$target_sdk" \
    --version-code "$version_code" --version-name "$version" --java "$work/java" \
    -A app/src/main/assets -A shared -o "$work/unsigned.apk" "$work/resources.zip"
  sources=()
  while IFS= read -r -d '' file; do sources+=("$file"); done < <(find "$work/java" app/src/main/java -name '*.java' -print0)
  "$javac" -source 8 -target 8 -encoding UTF-8 -proc:none -bootclasspath "$lambda_stubs:$android_jar" \
    -d "$work/classes" "${sources[@]}"
  (cd "$work/classes" && zip -qr "$work/bytecode.jar" .)
  "$bt/d8" --release --min-api "$min_sdk" --lib "$android_jar" --output "$work/dex" "$work/bytecode.jar"
  zip -qj "$work/unsigned.apk" "$work/dex"/classes*.dex
  "$bt/zipalign" -f 4 "$work/unsigned.apk" "$work/aligned.apk"

  key=${VEZEL_DEBUG_KEYSTORE:-"$HOME/.android/debug.keystore"}
  if [[ ! -f "$key" ]]; then
    [[ -z ${VEZEL_DEBUG_KEYSTORE:-} ]] || fail 'VEZEL_DEBUG_KEYSTORE does not exist'
    command -v "$keytool" >/dev/null || fail 'JDK 17 keytool is required to generate a development key'
    mkdir -p "$(dirname "$key")"
    (umask 077; "$keytool" -genkeypair -noprompt -storetype JKS -keystore "$key" \
      -alias androiddebugkey -storepass android -keypass android -keyalg RSA -keysize 2048 \
      -validity 10000 -dname 'CN=Android Debug,O=Android,C=US')
  fi
  # The standard Android development-key password is not a release-signing secret.
  apk="$work/signed.apk"
  "$bt/apksigner" sign --ks "$key" --ks-key-alias androiddebugkey --ks-pass pass:android \
    --key-pass pass:android --min-sdk-version "$min_sdk" --v4-signing-enabled false \
    --out "$apk" "$work/aligned.apk"
else
  ./gradlew --no-daemon :app:assembleDebug
  apk=app/build/outputs/apk/debug/app-debug.apk
fi

[[ -f "$apk" ]] || fail 'APK output is missing'
"$bt/zipalign" -c 4 "$apk"
"$bt/apksigner" verify --min-sdk-version "$min_sdk" --verbose --print-certs "$apk"
badging=$("$bt/aapt2" dump badging "$apk")
[[ $badging == *"name='$app_id' versionCode='$version_code' versionName='$version'"* ]] || fail 'APK package or version mismatch'
# SDK releases have used both sdkVersion and minSdkVersion in this diagnostic.
[[ ( $badging == *"sdkVersion:'$min_sdk'"* || $badging == *"minSdkVersion:'$min_sdk'"* ) &&
   $badging == *"targetSdkVersion:'$target_sdk'"* ]] || fail 'APK Android API level mismatch'
[[ $badging == *'DashboardActivity'* && $badging == *"provides-component:'launcher'"* ]] || fail 'Home launcher activity not packaged'
entries=$(unzip -Z1 "$apk")
for entry in classes.dex assets/fonts/instrument.ttf assets/vezel-off.jpg assets/vezel-on.jpg; do
  grep -Fxq "$entry" <<< "$entries" || fail "missing $entry"
done

name=Vezel-Drive-Console.apk
mkdir -p artifacts
publish=$(mktemp -d "$PWD/artifacts/.apk-build.XXXXXX")
cp "$apk" "$publish/$name"
if command -v sha256sum >/dev/null; then
  (cd "$publish" && sha256sum "$name" > "$name.sha256")
else
  (cd "$publish" && shasum -a 256 "$name" > "$name.sha256")
fi
mv -f "$publish/$name" "$publish/$name.sha256" artifacts/
printf '\nBuilt Vezel Drive Console %s (version code %s, Android API %s+)\n' "$version" "$version_code" "$min_sdk"
printf 'APK:      %s/artifacts/%s\nChecksum: %s/artifacts/%s.sha256\n' "$PWD" "$name" "$PWD" "$name"
