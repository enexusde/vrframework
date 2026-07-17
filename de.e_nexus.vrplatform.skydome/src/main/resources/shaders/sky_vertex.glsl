#version 330 core

layout(location = 0) in vec3 aPosition;

uniform mat4 projection;
uniform mat4 view; // rotation only (translation stripped) so the sky follows the camera

out vec3 vDirection;

void main() {
    vDirection = aPosition;
    vec4 pos = projection * view * vec4(aPosition, 1.0);
    gl_Position = pos.xyww; // pin every fragment to the far plane
}
