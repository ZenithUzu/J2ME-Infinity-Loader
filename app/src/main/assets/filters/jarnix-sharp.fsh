#ifdef GL_ES
precision highp float;
#endif
uniform sampler2D sampler0;
uniform vec2 u_texelDelta;
uniform vec2 u_pixelDelta;
varying vec2 v_texcoord0;
void main() {
  vec2 scale = max(floor(u_texelDelta / u_pixelDelta), vec2(1.0));
  vec2 texel = v_texcoord0 / u_texelDelta;
  vec2 range = 0.5 - 0.5 / scale;
  vec2 d = fract(texel) - 0.5;
  vec2 f = (d - clamp(d, -range, range)) * scale + 0.5;
  gl_FragColor = texture2D(sampler0, (floor(texel) + f) * u_texelDelta);
}
