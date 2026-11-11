#ifdef GL_ES
precision highp float;
#endif
uniform sampler2D sampler0;
uniform vec2 u_texelDelta;
uniform vec2 u_pixelDelta;
varying vec2 v_texcoord0;
uniform vec4 u_setting;
void effectMain() {
  vec2 texel = v_texcoord0 / u_texelDelta;
  vec2 px = u_texelDelta / u_pixelDelta;
  vec2 d = abs(fract(texel) - 0.5) * 2.0;
  vec2 edge = smoothstep(1.0 - 1.6 / px, vec2(1.0), d);
  vec3 c = texture2D(sampler0, v_texcoord0).rgb;
  gl_FragColor = vec4(c * (1.0 - 0.28 * max(edge.x, edge.y)), 1.0);
}
void main() { effectMain(); gl_FragColor = mix(gl_FragColor, texture2D(sampler0, v_texcoord0), clamp(u_setting.x, 0.0, 1.0)); }
