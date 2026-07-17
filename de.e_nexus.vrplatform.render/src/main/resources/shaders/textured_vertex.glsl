#version 330 core

layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec2 aUV;
layout(location = 2) in vec3 aNormal;

uniform mat4 model;
uniform mat4 view;
uniform mat4 projection;

out vec2 fragUV;
out vec3 fragNormal;
out vec3 fragPos;

void main() {
    vec4 worldPos = model * vec4(aPosition, 1.0);
    fragPos       = worldPos.xyz;
    fragNormal    = mat3(transpose(inverse(model))) * aNormal;
    fragUV        = aUV; // may exceed [0,1]; the texture wraps with GL_REPEAT to tile
    gl_Position   = projection * view * worldPos;
}
