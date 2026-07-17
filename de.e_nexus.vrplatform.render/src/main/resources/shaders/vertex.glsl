#version 330 core

layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec3 aColor;
layout(location = 2) in vec3 aNormal;

uniform mat4 model;
uniform mat4 view;
uniform mat4 projection;

out vec3 fragColor;
out vec3 fragNormal;
out vec3 fragPos;

void main() {
    vec4 worldPos  = model * vec4(aPosition, 1.0);
    fragPos        = worldPos.xyz;
    fragNormal     = mat3(transpose(inverse(model))) * aNormal;
    fragColor      = aColor;
    gl_Position    = projection * view * worldPos;
}
