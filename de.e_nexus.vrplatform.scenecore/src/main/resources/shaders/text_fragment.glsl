#version 330 core

in  vec2 fragUV;

uniform sampler2D uTexture;    // panel's own baked content (background/border/text)
uniform sampler2D uBackdrop;   // blurred capture of whatever is behind the panel
uniform vec2      uViewportSize;

out vec4 outColor;

void main() {
    vec4 panel = texture(uTexture, fragUV);
    vec2 screenUV = gl_FragCoord.xy / uViewportSize;
    vec3 backdrop = texture(uBackdrop, screenUV).rgb;

    // Frosted-glass compositing: transparent parts of the panel (unfocused
    // text fields) show the blurred scene behind them instead of the sharp
    // live framebuffer, so this must be an in-shader mix, not GL blending.
    outColor = vec4(mix(backdrop, panel.rgb, panel.a), 1.0);
}
