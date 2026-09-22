#version 150

// ドールハウス表示: 深度からカメラ相対ワールド座標を復元し、可視マスク外を exteriorBrightness まで暗くする。
// 表面法線方向へ微小バイアスしてから分類することで、マスク境界ちょうどの面（壁の外面・上面）が
// 内外どちらにも転ばなくなり、点滅を防ぐと同時に壁の外側の面を黒へ落とす。

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform sampler2D MaskSampler;
uniform mat4 InvProjView;
uniform vec3 MaskOrigin;
uniform float Exterior;
uniform float DollhouseActive;

in vec2 texCoord;
out vec4 fragColor;

const int SIZE_X = 49;
const int SIZE_Y = 25;
const int SIZE_Z = 49;
const int MASK_W = 1024;
const float SURFACE_BIAS = 0.05;

float maskAt(ivec3 c) {
    int lin = c.x + c.z * SIZE_X + c.y * (SIZE_X * SIZE_Z);
    return texelFetch(MaskSampler, ivec2(lin % MASK_W, lin / MASK_W), 0).r;
}

void main() {
    vec4 color = texture(DiffuseSampler, texCoord);

    float vis = Exterior;
    float depth = texture(DepthSampler, texCoord).r;
    if (depth < 0.9999) {
        vec4 clip = vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
        vec4 rel = InvProjView * clip;
        if (abs(rel.w) > 1.0e-6) {
            vec3 pos = rel.xyz / rel.w;
            vec3 n = cross(dFdx(pos), dFdy(pos));
            float len = length(n);
            if (len > 1.0e-12) {
                n /= len;
                if (dot(n, pos) > 0.0) {
                    n = -n;
                }
                pos += n * SURFACE_BIAS;
            }
            vec3 local = pos - MaskOrigin;
            ivec3 c = ivec3(floor(local));
            if (!any(lessThan(c, ivec3(0))) && !any(greaterThanEqual(c, ivec3(SIZE_X, SIZE_Y, SIZE_Z)))) {
                vis = mix(Exterior, 1.0, step(0.5, maskAt(c)));
            }
        }
    }

    vis = mix(1.0, vis, DollhouseActive);
    fragColor = vec4(color.rgb * vis, color.a);
}
