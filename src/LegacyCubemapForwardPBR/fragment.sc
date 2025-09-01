$input v_clipPosition, v_color0, v_texcoord0, v_worldPos

#include <bgfx_shader.sh>

uniform vec4 PreExposureEnabled;
uniform vec4 BlockBaseAmbientLightColorIntensity;
uniform vec4 SkyAmbientLightColorIntensity;
uniform vec4 DiffuseSpecularEmissiveAmbientTermToggles;
uniform vec4 VolumeScatteringEnabledAndPointLightVolumetricsEnabled;
uniform vec4 VolumeNearFar;
uniform vec4 VolumeDimensions;
uniform vec4 SunDir;
uniform vec4 SkyProbeUVFadeParameters; 

SAMPLER2D_AUTOREG(s_PreviousFrameAverageLuminance);
SAMPLER2D_AUTOREG(s_MatTexture);
SAMPLER2DARRAY_AUTOREG(s_ScatteringBuffer);

void main()
{
    vec4 outColor;
    highp vec4 matColor = texture2D(s_MatTexture, v_texcoord0);

    bool isNight = (SunDir.y <= 0.1);

    vec4 determineCol;
    if (isNight) {
        determineCol = matColor;
    } else {
        determineCol = vec4(0.0, 0.0, 0.0, 0.0);
    }

    outColor = determineCol;

    if (PreExposureEnabled.x > 0.0)
    {
        float avgLum = texture2D(s_PreviousFrameAverageLuminance, vec2(0.5, 0.5)).x;
        outColor.rgb *= (0.18 / avgLum) + 1e-4;
    }

    bgfx_FragData0 = outColor;
}