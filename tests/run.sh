#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
pkg=app/src/main/java/lk/vezel/console
javac -encoding UTF-8 -d "$classes" "$pkg/TripMeter.java" "$pkg/LightSignal.java" "$pkg/GaugeScale.java" "$pkg/BluetoothAudioMatch.java" "$pkg/VehicleRegistration.java" "$pkg/PowerCue.java" tests/LogicTest.java
java -cp "$classes" lk.vezel.console.LogicTest
python3 tests/check_vehicle.py
