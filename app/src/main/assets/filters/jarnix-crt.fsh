#ifdef GL_ES
precision highp float;
#endif
uniform sampler2D sampler0;
uniform vec2 u_texelDelta;
uniform vec2 u_pixelDelta;
varying vec2 v_texcoord0;
uniform vec4 u_setting;
uniform vec2 TextureSize;
uniform vec2 InputSize;
void effectMain() {
  // the game may use only part of the texture: curve that part, not the whole texture
  vec2 span = InputSize / TextureSize;
  vec2 uv = v_texcoord0 / span * 2.0 - 1.0;
  uv *= 1.0 + vec2(uv.y * uv.y, uv.x * uv.x) * 0.045;
  vec2 tc = (uv * 0.5 + 0.5) * span;
  if (tc.x < 0.0 || tc.y < 0.0 || tc.x > span.x || tc.y > span.y) { gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0); return; }
  vec3 c = texture2D(sampler0, tc).rgb;
  float scan = 0.72 + 0.28 * sin(fract(tc.y / u_texelDelta.y) * 3.14159);
  float m = mod(gl_FragCoord.x, 3.0);
  vec3 mask = m < 1.0 ? vec3(1.0, 0.82, 0.82) : m < 2.0 ? vec3(0.82, 1.0, 0.82) : vec3(0.82, 0.82, 1.0);
  float vig = 1.0 - 0.25 * dot(uv, uv);
  gl_FragColor = vec4(c * scan * mask * vig * 1.25, 1.0);
}
void main() { effectMain(); gl_FragColor = mix(gl_FragColor, texture2D(sampler0, v_texcoord0), clamp(u_setting.x, 0.0, 1.0)); }
