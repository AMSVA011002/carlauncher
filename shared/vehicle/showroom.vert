attribute vec3 aPosition;
attribute vec3 aNormal;
attribute vec4 aColor;
attribute vec2 aTex;
attribute vec4 aMaterial;
uniform mat4 uVP;
varying mediump vec3 vPosition;
varying mediump vec3 vNormal;
varying mediump vec4 vColor;
varying mediump vec2 vTex;
varying mediump vec4 vMaterial;
void main() {
    vPosition = aPosition;
    vNormal = aNormal;
    vColor = aColor;
    vTex = aTex;
    vMaterial = aMaterial;
    gl_Position = uVP * vec4(aPosition, 1.0);
}
