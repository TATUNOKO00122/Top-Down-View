#version 150

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    // 単色白・不透明度60%固定（uniform伝播に依存しない設計）
    fragColor = vec4(1.0, 1.0, 1.0, 0.6);
}
