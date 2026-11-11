#ifdef GL_ES
precision highp float;
#endif
uniform sampler2D sampler0;
uniform vec2 u_texelDelta;
uniform vec2 u_pixelDelta;
varying vec2 v_texcoord0;
uniform vec4 u_setting;
void effectMain() { vec4 c = texture2D(sampler0, v_texcoord0); float l = dot(c.rgb, vec3(0.299,0.587,0.114)); gl_FragColor = vec4(clamp((mix(vec3(l), c.rgb, 1.2) - 0.5) * 1.06 + 0.5, 0.0, 1.0), c.a); }
void main() { effectMain(); gl_FragColor = mix(gl_FragColor, texture2D(sampler0, v_texcoord0), clamp(u_setting.x, 0.0, 1.0)); }
