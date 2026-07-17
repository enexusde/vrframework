#version 330 core

in vec2 fragUV;

uniform sampler2D uSource;
uniform vec2 uTexelSize; // 1/width, 1/height of uSource
uniform vec2 uDirection; // (1,0) for the horizontal pass, (0,1) for the vertical pass

out vec4 outColor;

// Standard 9-tap separable Gaussian; two passes (horizontal then vertical)
// give a full 2D blur for roughly the cost of one 9x9 kernel.
const float WEIGHTS[5] = float[](0.227027, 0.1945946, 0.1216216, 0.054054, 0.016216);

void main() {
    vec3 result = texture(uSource, fragUV).rgb * WEIGHTS[0];
    for (int i = 1; i < 5; i++) {
        vec2 offset = uDirection * uTexelSize * float(i) * 2.0;
        result += texture(uSource, fragUV + offset).rgb * WEIGHTS[i];
        result += texture(uSource, fragUV - offset).rgb * WEIGHTS[i];
    }
    outColor = vec4(result, 1.0);
}
