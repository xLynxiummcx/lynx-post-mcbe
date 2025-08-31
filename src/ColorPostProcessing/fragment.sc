$input v_texcoord0
#include <bgfx_shader.sh>

SAMPLER2D_AUTOREG(s_ColorTexture);
SAMPLER2D_AUTOREG(s_AverageLuminance);
SAMPLER2D_AUTOREG(s_PreExposureLuminance);
SAMPLER2D_AUTOREG(s_RasterizedColor);
SAMPLER2D_AUTOREG(s_SceneDepth);

float gaussian(float x, float sigma) {
    return exp(-(x * x) / (2.0 * sigma * sigma));
}

vec3 RRTAndODTFit(vec3 v) {
    vec3 a = v * (v + vec3(0.0245786, 0.0245786, 0.0245786)) - vec3(0.000090537, 0.000090537, 0.000090537);
    vec3 b = v * (vec3(0.983729, 0.983729, 0.983729) * v + vec3(0.4329510, 0.4329510, 0.4329510)) + vec3(0.238081, 0.238081, 0.238081);
    return a / b;
}

vec3 ACESFittedTonemap(vec3 color) {
    return RRTAndODTFit(color);
}

vec3 AdjustSaturation(vec3 color, float saturation) {
    float avg = (color.r + color.g + color.b) / 3.0;
    return mix(vec3(avg, avg, avg), color, saturation);
}

vec3 SampleChromaticStable(sampler2D tex, vec2 uv, float chromaOffset) {
    vec3 col;
    col.r = texture(tex, vec2(uv.x + chromaOffset, uv.y)).r;
    col.g = texture(tex, vec2(uv.x, uv.y)).g;
    col.b = texture(tex, vec2(uv.x - chromaOffset, uv.y)).b;
    return col;
}

vec3 RadialBlurStable(sampler2D tex, vec2 uv, float radius, int samples, float sigma) {
    vec3 sum = vec3(0.0, 0.0, 0.0);
    float weightSum = 0.0;

    for (int i = 0; i < samples; ++i) {
        float a = (float(i) / float(samples)) * 6.2831853; // full circle
        vec2 dir = vec2(cos(a), sin(a));

        for (int j = -2; j <= 2; ++j) {
            float dist = float(j) * radius;
            float w = gaussian(dist, sigma);

            vec2 sampleUV = vec2(uv.x + dir.x * dist, uv.y + dir.y * dist);
            sum += SampleChromaticStable(tex, sampleUV, 0.003) * w;
            weightSum += w;
        }
    }

    return clamp(sum / weightSum, vec3(0.0, 0.0, 0.0), vec3(1.0, 1.0, 1.0));
}

float ComputeAutoExposure(sampler2D avgLumTex, sampler2D prevExposureTex, vec2 uv) {
    float avgLum = texture(avgLumTex, vec2(0.5, 0.5)).r;
    avgLum = max(avgLum, 0.0001);

    float targetLum = 0.18;
    float targetExposure = targetLum / avgLum;

    float prevExposure = texture(prevExposureTex, vec2(0.5, 0.5)).r;
    float adaptationSpeed = 0.02;
    float exposureValue = mix(prevExposure, targetExposure, adaptationSpeed);

    return clamp(exposureValue, 0.8, 8.0);
}

float AutoBlurStrength(sampler2D avgLumTex, sampler2D prevBlurTex, vec2 uv) {
    float avgLum = texture(avgLumTex, vec2(0.5, 0.5)).r;
    avgLum = max(avgLum, 0.0001);

    float targetLum = 0.18;
    float targetBlur = targetLum / avgLum;

    float prevBlur = texture(prevBlurTex, vec2(0.5, 0.5)).r;
    float adaptationSpeed = 0.1;
    float depthStrength = mix(prevBlur, targetBlur, adaptationSpeed);

    return clamp(depthStrength, 0.0003, 0.0042);
}

void main() {
    vec2 uv = vec2(v_texcoord0.x, v_texcoord0.y);
    vec2 center = vec2(0.5, 0.5);
    float dist = distance(uv, center);

    float sceneDepth = texture(s_SceneDepth, uv).r;
    float focusDepth = 0.5; 
    float focusRange = 0.05;  
    float coc = abs(sceneDepth - focusDepth) / focusRange;
    coc = clamp(coc, 0.0, 1.0);

    float blurAmount = smoothstep(0.1, 0.5, dist);
    float blurStrength = AutoBlurStrength(s_AverageLuminance, s_PreExposureLuminance, uv);
    float blurRadius = blurAmount * blurStrength * coc;
    vec3 fullscene = RadialBlurStable(s_ColorTexture, uv, blurRadius, 15, 0.5);

    float exposureValue = ComputeAutoExposure(s_AverageLuminance, s_PreExposureLuminance, uv);
    fullscene = vec3(fullscene.r * exposureValue, fullscene.g * exposureValue, fullscene.b * exposureValue);

    vec3 color = ACESFittedTonemap(fullscene);
    color = AdjustSaturation(color, 1.3);

    float vignette = pow(1.0 - smoothstep(0.25, 0.8, dist), 1.8);
    color = vec3(color.r * vignette, color.g * vignette, color.b * vignette);

    color = vec3(pow(color.r, 1.0 / 2.2), pow(color.g, 1.0 / 2.2), pow(color.b, 1.0 / 2.2));
    gl_FragColor = vec4(color.r, color.g, color.b, 1.0);
}