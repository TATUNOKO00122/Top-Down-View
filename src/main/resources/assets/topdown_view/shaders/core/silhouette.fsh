#version 150

uniform vec4 ColorModulator;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    fragColor = vec4(1.0, 1.0, 1.0, 0.6 * ColorModulator.a);
}
