$input v_texcoord0 
#include <bgfx_shader.sh>

uniform highp vec4 Time;

SAMPLER2D_AUTOREG(s_BlurPyramidTexture);
SAMPLER2D_AUTOREG(s_AverageLuminance);
SAMPLER2D_AUTOREG(s_HDRi);               // Original HDR image
SAMPLER2D_AUTOREG(s_RasterizedColor);

uniform highp vec4 ViewportScale;
uniform highp vec4 BloomParams;

void main() {
    vec4 color = vec4_splat(0.0);
    const vec3 bloomTint = vec3(1.0, 0.85, 0.6);

#if defined(DFDownSample)
    highp vec2 clampUV = (floor(ViewportScale.zw * ViewportScale.xy) - vec2_splat(0.5)) / ViewportScale.zw;
    highp vec2 offset = (ViewportScale.xy * 1.5) * (vec2_splat(2.0) / ViewportScale.zw);
    color = texture(s_BlurPyramidTexture, min(v_texcoord0.xy, clampUV)) * 0.5
          + texture(s_BlurPyramidTexture, min(v_texcoord0.xy + vec2(offset.x, offset.y), clampUV)) * 0.125
          + texture(s_BlurPyramidTexture, min(v_texcoord0.xy + vec2(-offset.x, offset.y), clampUV)) * 0.125
          + texture(s_BlurPyramidTexture, min(v_texcoord0.xy + vec2(offset.x, -offset.y), clampUV)) * 0.125
          + texture(s_BlurPyramidTexture, min(v_texcoord0.xy + vec2(-offset.x, -offset.y), clampUV)) * 0.125;
#endif

#if defined(DFUpSample)
    highp vec2 clampUV = (floor(ViewportScale.zw * ViewportScale.xy) - vec2_splat(0.5)) / ViewportScale.zw;
    highp vec2 offset = (ViewportScale.xy * 4.0) * (vec2_splat(0.5) / ViewportScale.zw);
    color = texture(s_BlurPyramidTexture, min(v_texcoord0.xy + 0.5 * offset, clampUV)) * 0.166
          + texture(s_BlurPyramidTexture, min(v_texcoord0.xy + vec2(-0.5*offset.x, 0.5*offset.y), clampUV)) * 0.166
          + texture(s_BlurPyramidTexture, min(v_texcoord0.xy + vec2(0.5*offset.x, -0.5*offset.y), clampUV)) * 0.166
          + texture(s_BlurPyramidTexture, min(v_texcoord0.xy + vec2(-0.5*offset.x, -0.5*offset.y), clampUV)) * 0.166
          + texture(s_BlurPyramidTexture, min(v_texcoord0.xy + offset, clampUV)) * 0.083
          + texture(s_BlurPyramidTexture, min(v_texcoord0.xy + vec2(-offset.x, offset.y), clampUV)) * 0.083
          + texture(s_BlurPyramidTexture, min(v_texcoord0.xy + vec2(offset.x, -offset.y), clampUV)) * 0.083
          + texture(s_BlurPyramidTexture, min(v_texcoord0.xy + vec2(-offset.x, -offset.y), clampUV)) * 0.083;
#endif

#if defined(ThresholdedDownSample)
    highp vec2 clampUV = (floor(ViewportScale.zw * ViewportScale.xy) - vec2_splat(0.5)) / ViewportScale.zw;
    highp float threshold = BloomParams.y * texture(s_AverageLuminance, vec2_splat(0.5)).x;
    highp vec2 offset = (ViewportScale.xy * 1.5) * (vec2_splat(2.0) / ViewportScale.zw);
    highp vec4 c0 = texture(s_BlurPyramidTexture, min(v_texcoord0.xy, clampUV));
    highp vec4 c1 = texture(s_BlurPyramidTexture, min(v_texcoord0.xy + vec2(offset.x, offset.y), clampUV));
    highp vec4 c2 = texture(s_BlurPyramidTexture, min(v_texcoord0.xy + vec2(-offset.x, offset.y), clampUV));
    highp vec4 c3 = texture(s_BlurPyramidTexture, min(v_texcoord0.xy + vec2(offset.x, -offset.y), clampUV));
    highp vec4 c4 = texture(s_BlurPyramidTexture, min(v_texcoord0.xy + vec2(-offset.x, -offset.y), clampUV));

    vec3 lumWeights = vec3_splat(0.0);
    lumWeights.r = 0.2126; lumWeights.g = 0.7152; lumWeights.b = 0.0722;

    color = c0 * step(threshold, dot(c0.xyz, lumWeights)) * 0.5
          + c1 * step(threshold, dot(c1.xyz, lumWeights)) * 0.125
          + c2 * step(threshold, dot(c2.xyz, lumWeights)) * 0.125
          + c3 * step(threshold, dot(c3.xyz, lumWeights)) * 0.125
          + c4 * step(threshold, dot(c4.xyz, lumWeights)) * 0.125;
#endif

#if defined(BloomBlend)
    vec2 texelSize = ViewportScale.xy * 4.0 / ViewportScale.zw;
    vec2 clampUV = (floor(ViewportScale.zw * ViewportScale.xy) - vec2_splat(0.5)) / ViewportScale.zw;
    vec2 uv = v_texcoord0.xy;

    vec2 offsets[9] = vec2[](
        vec2_splat(0.0),
        vec2( 0.5,  0.5), vec2(-0.5,  0.5),
        vec2( 0.5, -0.5), vec2(-0.5, -0.5),
        vec2( 1.0,  1.0), vec2(-1.0,  1.0),
        vec2( 1.0, -1.0), vec2(-1.0, -1.0)
    );

    float weights[9] = float[](
        0.40,
        0.18, 0.18,
        0.18, 0.18,
        0.12, 0.12,
        0.12, 0.12
    );

    vec3 bloom = vec3_splat(0.0);
    for (int i = 0; i < 9; ++i) {
        vec2 sampleUV = clamp(uv + offsets[i] * texelSize, vec2_splat(0.0), clampUV);
        bloom += texture(s_BlurPyramidTexture, sampleUV).rgb * weights[i];
    }

    bloom *= bloomTint;

    vec3 hdrColor = texture(s_HDRi, uv).rgb;
    vec3 result = hdrColor + bloom * BloomParams.x;
    color = vec4(result, 1.0);
#endif

    gl_FragColor = color; // ✅ fixed
}