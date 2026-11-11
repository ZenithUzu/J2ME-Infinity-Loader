#ifdef GL_ES
precision highp float;
#endif
uniform sampler2D sampler0;
uniform vec2 u_texelDelta;
uniform vec2 u_pixelDelta;
varying vec2 v_texcoord0;
uniform vec4 u_setting;
void effectMain() {
  vec3 c = texture2D(sampler0, v_texcoord0).rgb;
  float rows = u_texelDelta.y / u_pixelDelta.y;
  float y = fract(v_texcoord0.y / u_texelDelta.y);
  float w = clamp(1.0 / rows, 0.08, 0.5);
  float gap = smoothstep(1.0 - w, 1.0, y) + 1.0 - smoothstep(0.0, w, y);
  gl_FragColor = vec4(c * (1.0 - 0.35 * min(gap, 1.0)) * 1.06, 1.0);
}
void main() { effectMain(); gl_FragColor = mix(gl_FragColor, texture2D(sampler0, v_texcoord0), clamp(u_setting.x, 0.0, 1.0)); }
