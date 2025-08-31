#include <bgfx_compute.sh>
#include <bgfx_shader.sh>

NUM_THREADS(8, 8, 1)

// Uniforms
uniform vec4 VolumeDimensions; // x: width, y: height, z: depth
uniform vec4 VolumeNearFar;    // x: near, y: far

// Buffers
IMAGE2D_ARRAY_RO_AUTOREG(s_LightingBuffer, rgba16f);
IMAGE2D_ARRAY_WR_AUTOREG(s_ScatteringBuffer, rgba16f);

// Convert logarithmic depth to linear
float logToLinearDepth(float logDepth) {
    return (exp(4.0 * logDepth) - 1.0) / (exp(4.0) - 1.0);
}

struct NoopSampler { int noop; };
struct NoopImage2D { int noop; };
struct NoopImage3D { int noop; };
struct rayQueryKHR { int noop; };
struct accelerationStructureKHR { int noop; };


void Scattering()
{
    int width  = int(VolumeDimensions.x);
    int height = int(VolumeDimensions.y);
    int depth  = int(VolumeDimensions.z);

    int x = int(GlobalInvocationID.x);
    int y = int(GlobalInvocationID.y);
    if (x >= width || y >= height) return;

    float prevDepth = VolumeNearFar.x;
    vec4 accum = vec4(0.0, 0.0, 0.0, 1.0);

    const float scatteringBoost = 12.0; // Godray multiplier

    for (int z = 0; z < depth; ++z)
    {
        // Map slice to nonlinear depth
        float slice = (exp(5.0 * (float(z) + 0.5) / VolumeDimensions.z) - 1.0) * 0.015;
        float currDepth = mix(VolumeNearFar.x, VolumeNearFar.y, slice);
        float stepLength = currDepth - prevDepth;
        prevDepth = currDepth;

        // Load extinction/lighting from lighting buffer
        vec4 lighting = imageLoad(s_LightingBuffer, ivec3(x, y, z));
        float extinction = lighting.w;
        float transmittance = exp(-extinction * stepLength);

        float opticalDepth = (abs(extinction) > 1e-6) 
            ? (1.0 - transmittance) / extinction 
            : stepLength;

        float depthFactor = pow(1.0 - float(z) / float(depth), 3.0);
        vec3 shaftColor = lighting.rgb * scatteringBoost * depthFactor;

        accum.rgb += accum.a * opticalDepth * shaftColor;
        accum.a *= transmittance;

        imageStore(s_ScatteringBuffer, ivec3(x, y, z), accum);
    }
}

NUM_THREADS(8, 8, 1)
void main()
{
uvec3 LocalInvocationID;
uint LocalInvocationIndex;
uvec3 GlobalInvocationID;
uvec3 WorkGroupID;

    LocalInvocationID = gl_LocalInvocationID;
    LocalInvocationIndex = gl_LocalInvocationIndex;
    GlobalInvocationID = gl_GlobalInvocationID;
    WorkGroupID = gl_WorkGroupID;

    Scattering();
}