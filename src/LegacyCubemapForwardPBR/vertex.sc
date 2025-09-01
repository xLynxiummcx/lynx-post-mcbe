$input a_color0,a_position, a_texcoord0
$output v_clipPosition, v_color0, v_texcoord0, v_worldPos
 
uniform highp vec4 ViewportScale;
uniform mat4 CubemapRotation;
uniform vec4 SubPixelOffset;

#include <bgfx_shader.sh>

void main() { 

  vec4 _513 = u_model[0] * (CubemapRotation * vec4(a_position, 1.0));
    mat4 _523 = u_proj;
    _523[2].x += SubPixelOffset.x;
    _523[2].y -= SubPixelOffset.y;
    vec4 _546 = _523 * (u_view * vec4(_513.xyz, 1.0));
    v_clipPosition = _546;
    v_color0 = a_color0;
    v_texcoord0 = a_texcoord0;
    v_worldPos = _513.xyz;
    gl_Position = _546;
}
