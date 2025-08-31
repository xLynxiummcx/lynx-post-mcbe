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
    vec4 color = vec4(0.0);
    const vec3 bloomTint = vec3(1.0, 0.85, 0.6);

#if defined(DFDownSample)
    highp vec2 _306 = (floor(ViewportScale.zw * ViewportScale.xy) - vec2(0.5)) / ViewportScale.zw;
    highp vec2 _274 = (ViewportScale.xy * 1.5) * (vec2(2.0) / ViewportScale.zw);
    color = ((((texture(s_BlurPyramidTexture, min(v_texcoord0.xy, _306)) * 0.5) 
             + (texture(s_BlurPyramidTexture, min(v_texcoord0.xy + vec2(_274.x, _274.y), _306)) * 0.125)) 
             + (texture(s_BlurPyramidTexture, min(v_texcoord0.xy + vec2(-_274.x, _274.y), _306)) * 0.125)) 
             + (texture(s_BlurPyramidTexture, min(v_texcoord0.xy + vec2(_274.x, -_274.y), _306)) * 0.125)) 
             + (texture(s_BlurPyramidTexture, min(v_texcoord0.xy + vec2(-_274.x, -_274.y), _306)) * 0.125);
#endif

#if defined(DFUpSample)
    highp vec2 _363 = (floor(ViewportScale.zw * ViewportScale.xy) - vec2(0.5)) / ViewportScale.zw;
    highp vec2 _331 = (ViewportScale.xy * 4.0) * (vec2(0.5) / ViewportScale.zw);
    highp vec4 _474 = ((((((texture(s_BlurPyramidTexture, min(v_texcoord0.xy + vec2(0.5 * _331.x, 0.5 * _331.y), _363)) * 0.166) 
                        + (texture(s_BlurPyramidTexture, min(v_texcoord0.xy + vec2(-0.5 * _331.x, 0.5 * _331.y), _363)) * 0.166)) 
                        + (texture(s_BlurPyramidTexture, min(v_texcoord0.xy + vec2(0.5 * _331.x, -0.5 * _331.y), _363)) * 0.166)) 
                        + (texture(s_BlurPyramidTexture, min(v_texcoord0.xy + vec2(-0.5 * _331.x, -0.5 * _331.y), _363)) * 0.166)) 
                        + (texture(s_BlurPyramidTexture, min(v_texcoord0.xy + vec2(_331.x, _331.y), _363)) * 0.083)) 
                        + (texture(s_BlurPyramidTexture, min(v_texcoord0.xy + vec2(-_331.x, _331.y), _363)) * 0.083)) 
                        + (texture(s_BlurPyramidTexture, min(v_texcoord0.xy + vec2(_331.x, -_331.y), _363)) * 0.083);
    color = _474 + (texture(s_BlurPyramidTexture, min(v_texcoord0.xy + vec2(-_331.x, -_331.y), _363)) * 0.083);
#endif

#if defined(ThresholdedDownSample)
    highp vec2 _371 = (floor(ViewportScale.zw * ViewportScale.xy) - vec2(0.5)) / ViewportScale.zw;
    highp float _354 = BloomParams.y * texture(s_AverageLuminance, vec2(0.5)).x;
    highp vec2 _333 = (ViewportScale.xy * 1.5) * (vec2(2.0) / ViewportScale.zw);
    highp vec4 _470 = texture(s_BlurPyramidTexture, min(v_texcoord0.xy, _371));
    highp vec4 _486 = texture(s_BlurPyramidTexture, min(v_texcoord0.xy + vec2(_333.x, _333.y), _371));
    highp vec4 _502 = texture(s_BlurPyramidTexture, min(v_texcoord0.xy + vec2(-_333.x, _333.y), _371));
    highp vec4 _518 = texture(s_BlurPyramidTexture, min(v_texcoord0.xy + vec2(_333.x, -_333.y), _371));
    highp vec4 _534 = texture(s_BlurPyramidTexture, min(v_texcoord0.xy + vec2(-_333.x, -_333.y), _371));
    color = (((((_470 * step(_354, dot(_470.xyz, vec3(0.2126, 0.7152, 0.0722)))) * 0.5) 
             + ((_486 * step(_354, dot(_486.xyz, vec3(0.2126, 0.7152, 0.0722)))) * 0.125)) 
             + ((_502 * step(_354, dot(_502.xyz, vec3(0.2126, 0.7152, 0.0722)))) * 0.125)) 
             + ((_518 * step(_354, dot(_518.xyz, vec3(0.2126, 0.7152, 0.0722)))) * 0.125)) 
             + ((_534 * step(_354, dot(_534.xyz, vec3(0.2126, 0.7152, 0.0722)))) * 0.125);
#endif

#if defined(BloomBlend)
    vec2 texelSize = ViewportScale.xy * 4.0 / ViewportScale.zw;
    vec2 clampUV = (floor(ViewportScale.zw * ViewportScale.xy) - 0.5) / ViewportScale.zw;
    vec2 uv = v_texcoord0.xy;

    vec2 offsets[9] = vec2[](
        vec2(0.0,  0.0),
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

    vec3 bloom = vec3(0.0);
    for (int i = 0; i < 9; ++i) {
        vec2 sampleUV = clamp(uv + offsets[i] * texelSize, vec2(0.0), clampUV);
        bloom += texture(s_BlurPyramidTexture, sampleUV).rgb * weights[i];
    }

    bloom *= bloomTint;

    vec3 hdrColor = texture(s_HDRi, uv).rgb;
    vec3 result = hdrColor + bloom * BloomParams.x;
    color = vec4(result, 1.0);
#endif

    gl_FragColor = color; // ✅ fixed
}