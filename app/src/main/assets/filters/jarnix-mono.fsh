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
  vec3 c = texture2D(sampler0, v_texcoord0).rgb;
  float l = dot(c, vec3(0.299, 0.587, 0.114));
  vec3 g = mix(vec3(0.10, 0.19, 0.10), vec3(0.66, 0.76, 0.42), l);
  vec2 d = abs(fract(texel) - 0.5) * 2.0;
  vec2 edge = smoothstep(1.0 - 1.6 / (u_texelDelta / u_pixelDelta), vec2(1.0), d);
  gl_FragColor = vec4(g * (1.0 - 0.2 * max(edge.x, edge.y)), 1.0);
}
void main() { effectMain(); gl_FragColor = mix(gl_FragColor, texture2D(sampler0, v_texcoord0), clamp(u_setting.x, 0.0, 1.0)); }
