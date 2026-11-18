precision mediump float;
uniform vec3 uEye;
uniform float uLights;
uniform float uNight;
uniform sampler2D uDecals;
varying mediump vec3 vPosition;
varying mediump vec3 vNormal;
varying mediump vec4 vColor;
varying mediump vec2 vTex;
varying mediump vec4 vMaterial;

float softbox(vec3 r, vec3 d, float width) {
    float facing = max(dot(r, d), 0.0);
    return pow(facing, width);
}

void main() {
    if (vColor.a < 0.5) {
        float x = vPosition.x;
        float z = vPosition.z;
        float floorDistance = length(vec2(x * 0.35, z * 0.54));
        float pool = 1.0 - smoothstep(0.15, 4.4, floorDistance);
        float under = (1.0 - smoothstep(0.20, 1.23, abs(z))) *
                      (1.0 - smoothstep(1.15, 2.40, abs(x)));
        float warm = 1.0 - smoothstep(0.15, 4.5, length(vec2(x + 0.9, z + 1.8)));
        vec3 base = vec3(0.063, 0.054, 0.069) + pool * vec3(0.064, 0.055, 0.070) + warm * vec3(0.051, 0.026, 0.017);
        float wheels = 1.0 - smoothstep(0.07, 0.48, length(vec2((abs(x) - 1.31) * 1.3, (abs(z) - .75) * 1.9)));
        base *= 1.0 - max(under * 0.72, wheels * .68);
        // Two dipped beams move with the car, not with the screen.
        float ahead = max(x - 1.75, 0.0);
        float spread = 0.38 + 0.22 * ahead;
        float beam = (1.0 - smoothstep(0.25 * spread, spread, abs(abs(z) - 0.53))) *
                     smoothstep(0.10, 0.72, ahead) * (1.0 - smoothstep(0.75, 4.4, ahead));
        float tail = 1.0 - smoothstep(0.0, 1.28, length(vec2(x + 2.45, (abs(z) - .66) * 1.85)));
        // The lower bumper pockets cast a wide, short fill below the main beams.
        float fogFalloff = 1.0 - smoothstep(0.18, 1.50, length(vec2((x - 2.72) * .81, (abs(z) - .70) * 1.16)));
        float fogLamp = fogFalloff * smoothstep(2.08, 2.48, x);
        base += uLights * (beam * vec3(.26,.30,.37) + fogLamp * vec3(.26,.195,.122) + tail * vec3(.16,.030,.023));
        base = mix(base, vec3(.085, .072, .087), smoothstep(7.2, 16.0, length(vPosition.xz)));
        gl_FragColor = vec4(base * mix(1.0, 0.61, uNight), 1.0);
        return;
    }
    if (vMaterial.w > 0.5) {
        vec4 stamp = texture2D(uDecals, vTex);
        if (stamp.a < 0.37) discard;
        gl_FragColor = vec4(stamp.rgb * mix(1.0, 0.70, uNight), 1.0);
        return;
    }
    vec3 n = normalize(vNormal);
    vec3 eye = normalize(uEye - vPosition);
    vec3 r = reflect(-eye, n);
    vec3 key = vec3(0.41, 0.82, 0.40);
    vec3 rim = vec3(-0.55, 0.60, -0.58);
    float edge = pow(1.0 - clamp(dot(n, eye), 0.0, 1.0), 2.4);
    float keyAmount = max(dot(n, key), 0.0);
    float rimAmount = max(dot(n, rim), 0.0);
    float gloss = vMaterial.x;
    float metal = vMaterial.y;
    float ceiling = smoothstep(.18,.92,r.y);
    float softHorizon = (1.0 - smoothstep(.015,.17,abs(r.y+.10))) * (0.25 + 0.75*abs(r.z));
    float whiteStrip = smoothstep(.21,.36,r.y) * (1.0 - smoothstep(.39,.49,r.y)) * (.31 + .69*abs(r.z));
    float rimStrip = smoothstep(.60,.73,r.y) * (1.0 - smoothstep(.73,.83,r.y)) * (.22 + .78*abs(r.x));
    float broad = softbox(r, vec3(0.45,0.68,0.58), 6.7);
    float spec = softbox(r, key, mix(4.0, 26.0, gloss)) + 0.44 * softbox(r, rim, 15.0);
    float paint = step(.77, metal);
    float lowLight = smoothstep(.26,.80,vPosition.y);
    vec3 surface = vColor.rgb * (.29 + .66 * keyAmount + .25 * rimAmount + .21 * max(n.y, 0.0));
    surface *= mix(1.0, .57 + .53 * lowLight, paint);
    surface += gloss * (.006 + .050 * ceiling + whiteStrip * .195 + rimStrip * .128 + broad * .071 + .045 * edge) *
               mix(vec3(.85, .94, 1.08), vec3(1.31, .67, .66), paint);
    surface += gloss * (.10 * spec * (1.0 + metal) + .015 * softHorizon) *
               mix(vec3(.90, .92, 1.0), vec3(1.22, .64, .56), paint);
    surface *= (.79 + .21 * smoothstep(.11, .75, vPosition.y));
    float lamp = vMaterial.z;
    if (lamp > 0.72) surface = mix(surface, vec3(.91, 0.143, 0.124), uLights * 0.61);
    else if (lamp > .61) surface = mix(surface, vec3(1.0, .91, .69), uLights * .96);
    else if (lamp > .30) surface = mix(surface, vec3(.78, .88, .98), uLights * (.48 + lamp*.68));
    float shade = mix(1.0, 0.65 + uLights * step(0.3, lamp) * 0.14, uNight);
    gl_FragColor = vec4(surface * shade, 1.0);
}
