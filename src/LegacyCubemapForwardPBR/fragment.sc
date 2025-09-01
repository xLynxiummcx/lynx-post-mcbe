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
   highp vec4 _910 = texture2D(s_MatTexture, v_texcoord0);

bool isNight = SunDir.y <= 0.1;

vec4 determinecol;
if (isNight) {
    determinecol = _910;
} else {
    determinecol = vec4(0.0,0.0,0.0);
}
 outColor = determinecol;

    if (PreExposureEnabled.x > 0.0)
    {
        outColor.rgb = outColor.rgb * ((0.180000007152557373046875 / texture2D(s_PreviousFrameAverageLuminance, vec2_splat(0.5)).x) + 9.9999997473787516355514526367188e-05);
    }
    else
    {
        outColor.rgb = outColor.rgb;
    }
    gl_FragColor = outColor;
}