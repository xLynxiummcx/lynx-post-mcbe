$input v_texcoord0
#include <bgfx_shader.sh>
#include <config.h>

SAMPLER2D_AUTOREG(s_ColorTexture);
SAMPLER2D_AUTOREG(s_AverageLuminance);
SAMPLER2D_AUTOREG(s_PreExposureLuminance);
SAMPLER2D_AUTOREG(s_RasterizedColor);
uniform highp vec4 ViewportScale;

uniform vec4 FogColor;
uniform vec4 Time;

vec3 RRTAndODTFit(vec3 v) {
    vec3 a = v * (v + 0.0245786) - 0.000090537;
    vec3 b = v * (0.983729 * v + 0.4329510) + 0.238081;
    return a / b;
}

vec3 ACESFittedTonemap(vec3 color) {
    return RRTAndODTFit(color);
}

vec3 AdjustSaturation(vec3 color, float saturation) {
    float avg = (color.r + color.g + color.b) / 3.0;
    return mix(vec3(avg, avg, avg), color, saturation);
}

vec3 SampleChromatic(vec2 uv, float offset) {
    vec2 dir = uv - vec2(0.5, 0.5);
    vec3 col;
    col.r = texture2D(s_ColorTexture, uv + dir * offset).r;
    col.g = texture2D(s_ColorTexture, uv).g;
    col.b = texture2D(s_ColorTexture, uv - dir * offset).b;
    return clamp(col, 0.0, 1.0);
}

float gaussian(float x, float sigma) {
    return exp(-(x * x) / (2.0 * sigma * sigma));
}

vec3 RadialBlur(vec2 uv, float radius, int samples, float sigma) {
    vec3 sum = vec3(0.0, 0.0, 0.0);
    float weightSum = 0.0;

    for (int i = 0; i < samples; ++i) {
        float a = (float(i) / float(samples)) * 6.2831853; // full circle
        vec2 dir = vec2(cos(a), sin(a));

        for (int j = -2; j <= 2; ++j) {
            float dist = float(j) * radius;
            float w = gaussian(dist, sigma);
            sum += SampleChromatic(uv + dir * dist, 0.008) * w;
            weightSum += w;
        }
    }

    return sum / max(weightSum, 0.0001);
}

float ComputeAutoExposure(vec2 uv) {
    float avgLum = texture2D(s_AverageLuminance, vec2(0.5, 0.5)).r;
    avgLum = max(avgLum, 0.0001);

    float targetLum = 0.18;
    float targetExposure = targetLum / avgLum;

    float prevExposure = texture2D(s_PreExposureLuminance, vec2(0.5, 0.5)).r;

    float adaptationSpeed = 0.1;
    float exposureValue = mix(prevExposure, targetExposure, adaptationSpeed);

    return clamp(exposureValue, 0.9, 4.0);
}

float AutoBlurStrength(vec2 uv) {
    float avgLum = texture2D(s_AverageLuminance, vec2(0.5, 0.5)).r;
    avgLum = max(avgLum, 0.0001);

    float targetLum = 0.18;
    float targetBlur = targetLum / avgLum;

    float prevBlur = texture2D(s_PreExposureLuminance, vec2(0.5, 0.5)).r;

    float adaptationSpeed = 0.2; 
    float depthStrength = mix(prevBlur, targetBlur, adaptationSpeed);

    return clamp(depthStrength, 0.00015, 0.0035);
}

float hueFromRGB(vec3 c) {
    float mx = max(c.r, max(c.g, c.b));
    float mn = min(c.r, min(c.g, c.b));
    float d = mx - mn;
    if (d == 0.0) return 0.0;
    if (mx == c.r) return mod((c.g - c.b) / d, 6.0) / 6.0;
    if (mx == c.g) return ((c.b - c.r) / d + 2.0) / 6.0;
    return ((c.r - c.g) / d + 4.0) / 6.0;
}

vec4 blur(sampler2D tex, vec2 uv, vec2 res, float radius) {
    vec2 texel = 1.0 / res;
    vec4 sum = vec4(0.0, 0.0, 0.0, 0.0);
    float w[9];
    w[0] = 0.05; w[1] = 0.09; w[2] = 0.12; w[3] = 0.15; w[4] = 0.16;
    w[5] = w[3]; w[6] = w[2]; w[7] = w[1]; w[8] = w[0];
    for (int i = -3; i <= 3; i++) {
        sum += texture2D(tex, uv + vec2(texel.x * float(i) * radius, 0.0)) * w[i + 4];
    }
    return sum;
}

// Author: Élie Michel
// License: CC BY 3.0
// July 2017
float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.x, p.y, p.x) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

    float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash12(i + vec2(0.0, 0.0));
    float b = hash12(i + vec2(1.0, 0.0));
    float c = hash12(i + vec2(0.0, 1.0));
    float d = hash12(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

void main() {
    vec2 uv = v_texcoord0.xy;
    vec2 center = vec2(0.5, 0.5);
    float dist = distance(uv, center);
    float time = Time.w;

    float blurAmount = smoothstep(0.1, 0.5, dist);
    float blurStrength = AutoBlurStrength(uv);
    float blurRadius = blurAmount * blurStrength;
    vec3 fullscene = RadialBlur(uv, blurRadius, 5, 0.5);

    float exposureValue = ComputeAutoExposure(uv);
    fullscene *= exposureValue;

    vec3 color = ACESFittedTonemap(fullscene);
    color = AdjustSaturation(color, 1.1);

    float vignette = pow(1.0 - smoothstep(0.25, 0.8, dist), 1.8);
    color *= vignette;

    vec2 c = gl_FragCoord.xy;
    vec2 resolution = u_viewRect.zw;
    vec2 u = c / resolution;
    u = u * ViewportScale.xy;
    vec2 v = (c * 0.1) / resolution.xy*ViewportScale.xy;
    float n = noise(v * 200.0);
    vec4 baseBlur = blur(s_ColorTexture, u, resolution.xy*ViewportScale.xy, 0.75);
    vec4 f = baseBlur;

    for (float r = 4.0; r > 0.0; r--) {
        vec2 x = resolution.xy * r * 0.02;
        vec2 p = 6.2831 * u * x + (n - 0.5) * 2.0;
        p.y += time * 4.0;

        vec2 s = sin(p);
        vec2 v2 = round(u * x - 0.25) / x;
        vec4 d = vec4(noise(v2 * 200.0), noise(v2), noise(v2), noise(v2));

        float t = (s.x + s.y) * max(0.0, 1.0 - fract(0.3 * time * (d.b + 0.1) + d.g) * 2.0);

        if (d.r < (5.0 - r) * 0.08 && t > 0.5) {
            vec3 vn = normalize(-vec3(cos(p.x), cos(p.y), mix(0.2, 2.0, t - 0.5)));
            float blurAmount2 = mix(0.5, 1.8, r / 4.0);
            vec4 dropBlur = blur(s_ColorTexture, u - vn.xy * 0.02, resolution.xy, blurAmount2);
            f = mix(f, dropBlur, 0.9);
        }
    }

    // rain detection
    float targetHue = hueFromRGB(vec3(1.0, 0.0, 1.0));
    float currentHue = hueFromRGB(FogColor.rgb);
    float hueDist = abs(currentHue - targetHue);
    hueDist = min(hueDist, 1.0 - hueDist);
    bool hueDetected = hueDist < 0.03;

    if (hueDetected) {
        color = mix(color, ACESFittedTonemap(f.rgb), 1.0);
    }
    if (hueDetected) {
        color = AdjustSaturation(color, 0.8);
    }

    color = pow(color, vec3(1.0 / 2.2, 1.0 / 2.2, 1.0 / 2.2));
    gl_FragColor = vec4(color, 1.0);
}