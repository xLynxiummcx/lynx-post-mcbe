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

vec3 SampleChromaticStable(vec2 uv, float chromaOffset) {
    vec3 col;
    col.r = texture(s_ColorTexture, uv + vec2(chromaOffset, 0.0)).r;
    col.g = texture(s_ColorTexture, uv).g;
    col.b = texture(s_ColorTexture, uv - vec2(chromaOffset, 0.0)).b;
    return col;
}

vec3 RadialBlurStable(vec2 uv, float radius, int samples, float sigma) {
    vec3 sum = vec3(0.0, 0.0, 0.0);
    float weightSum = 0.0;

    for (int i = 0; i < samples; ++i) {
        float a = (float(i) / float(samples)) * 6.2831853; // full circle
        vec2 dir = vec2(cos(a), sin(a));

        for (int j = -2; j <= 2; ++j) {
            float dist = float(j) * radius;
            float w = gaussian(dist, sigma);

            vec2 sampleUV = uv + dir * dist;
            sum += SampleChromaticStable(sampleUV, 0.003) * w;
            weightSum += w;
        }
    }

    return clamp(sum / weightSum, vec3(0.0, 0.0, 0.0), vec3(1.0, 1.0, 1.0));
}

float ComputeAutoExposure(vec2 uv) {
    float avgLum = texture(s_AverageLuminance, vec2(0.5, 0.5)).r;
    avgLum = max(avgLum, 0.0001);

    float targetLum = 0.18;
    float targetExposure = targetLum / avgLum;

    float prevExposure = texture(s_PreExposureLuminance, vec2(0.5, 0.5)).r;

    float adaptationSpeed = 0.02;
    float exposureValue = mix(prevExposure, targetExposure, adaptationSpeed);

    return clamp(exposureValue, 0.8, 8.0);
}

float AutoBlurStrength(vec2 uv) {
    float avgLum = texture(s_AverageLuminance, vec2(0.5, 0.5)).r;
    avgLum = max(avgLum, 0.0001);

    float targetLum = 0.18;
    float targetBlur = targetLum / avgLum;

    float prevBlur = texture(s_PreExposureLuminance, vec2(0.5, 0.5)).r;

    float adaptationSpeed = 0.1;
    float depthStrength = mix(prevBlur, targetBlur, adaptationSpeed);

    return clamp(depthStrength, 0.0003, 0.0042);
}

void main() {
    vec2 uv = v_texcoord0.xy;
    vec2 center = vec2(0.5, 0.5);
    float dist = distance(uv, center);

    float sceneDepth = texture(s_SceneDepth, uv).r;
    float focusDepth = 0.5; 
    float focusRange = 0.05;  
    float coc = abs(sceneDepth - focusDepth) / focusRange;
    coc = clamp(coc, 0.0, 1.0);

    float blurAmount = smoothstep(0.1, 0.5, dist);
    float blurStrength = AutoBlurStrength(uv);
    float blurRadius = blurAmount * blurStrength * coc;
    vec3 fullscene = RadialBlurStable(uv, blurRadius, 15, 0.5);

    float exposureValue = ComputeAutoExposure(uv);
    fullscene *= exposureValue;

    vec3 color = ACESFittedTonemap(fullscene);
    color = AdjustSaturation(color, 1.3);

    float vignette = pow(1.0 - smoothstep(0.25, 0.8, dist), 1.8);
    color *= vignette;

    color = pow(color, vec3(1.0 / 2.2, 1.0 / 2.2, 1.0 / 2.2));
    gl_FragColor = vec4(color, 1.0);
}