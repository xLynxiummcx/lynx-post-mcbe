#include <bgfx_compute.sh>
#include <bgfx_shader.sh>


uniform vec4 VolumeDimensions; // x: width, y: height, z: depth
uniform vec4 VolumeNearFar;    // x: near, y: far

IMAGE2D_ARRAY_RO_AUTOREG(s_LightingBuffer, rgba16f);
IMAGE2D_ARRAY_WR_AUTOREG(s_ScatteringBuffer, rgba16f);

NUM_THREADS(8, 8, 1)
void main()
{

}