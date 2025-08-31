#include <bgfx_compute.sh>
#include <bgfx_shader.sh>


uniform vec4 VolumeDimensions; // x: width, y: height, z: depth
uniform vec4 VolumeNearFar;    // x: near, y: far

IMAGE2D_ARRAY_RO_AUTOREG(s_LightingBuffer, rgba16f);
IMAGE2D_ARRAY_WR_AUTOREG(s_ScatteringBuffer, rgba16f);

NUM_THREADS(8, 8, 1)
void main()
{
  uvec3 id = gl_GlobalInvocationID;
    int x = int(id.x);
    int y = int(id.y);

    int width  = int(VolumeDimensions.x);
    int height = int(VolumeDimensions.y);
    int depth  = int(VolumeDimensions.z);

    if (x >= width || y >= height)
        return;

    float prevDepth = mix(VolumeNearFar.x, VolumeNearFar.y, 0.0);
    vec4 scattering = vec4(0.0, 0.0, 0.0, 1.0); // rgb = light, a = transmittance

    const float scatteringBoost = 12.0; // godray multiplier

    for (int z = 0; z < depth; ++z)
    {
        float linearZ = float(z) + 0.5;
        float slice = (exp(5.0 * linearZ / VolumeDimensions.z) - 1.0) * 0.015;
        float currDepth = mix(VolumeNearFar.x, VolumeNearFar.y, slice);
        float stepLength = currDepth - prevDepth;
        prevDepth = currDepth;

        vec4 lighting = imageLoad(s_LightingBuffer, ivec3(x, y, z));
        float extinction = lighting.w;
        float transmittance = exp(-extinction * stepLength);

        float opticalDepth = (abs(extinction) > 1e-6)
            ? (1.0 - transmittance) / extinction
            : stepLength;

        float depthFactor = pow(1.0 - float(z) / float(depth), 3.0); // cubic falloff
        vec3 shaftColor = lighting.rgb * scatteringBoost * depthFactor;

        scattering.rgb += shaftColor * scattering.a * opticalDepth;
        scattering.a *= transmittance;

        imageStore(s_ScatteringBuffer, ivec3(x, y, z), scattering);
    }
}