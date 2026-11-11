#ifdef GL_ES
precision highp float;
#endif
uniform sampler2D sampler0;
uniform vec2 u_texelDelta;
uniform vec2 u_pixelDelta;
varying vec2 v_texcoord0;
uniform vec4 u_setting;
void effectMain() { vec4 c = texture2D(sampler0, v_texcoord0); gl_FragColor = vec4(c.rgb * vec3(1.0,0.94,0.82), c.a); }
void main() { effectMain(); gl_FragColor = mix(gl_FragColor, texture2D(sampler0, v_texcoord0), clamp(u_setting.x, 0.0, 1.0)); }
