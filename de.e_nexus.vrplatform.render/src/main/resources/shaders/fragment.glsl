#version 330 core

in  vec3 fragColor;
in  vec3 fragNormal;
in  vec3 fragPos;

uniform vec3 sunDirection; // normalized, points toward the sun (same as the skydome)
uniform vec3 viewPos;

out vec4 outColor;

// Cheap sky-tinted ambient so objects sit in the same light as the skydome,
// instead of a flat, sun-position-independent ambient term.
vec3 skyAmbient(float sunElevation) {
    vec3 dusk = vec3(0.55, 0.35, 0.28);
    vec3 day  = vec3(0.35, 0.42, 0.55);
    return mix(dusk, day, clamp(sunElevation * 1.5 + 0.4, 0.0, 1.0));
}

vec3 acesFilmic(vec3 x) {
    const float a = 2.51, b = 0.03, c = 2.43, d = 0.59, e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

void main() {
    vec3 norm     = normalize(fragNormal);
    vec3 lightDir = normalize(sunDirection);
    vec3 viewDir  = normalize(viewPos - fragPos);
    vec3 ambTint  = skyAmbient(sunDirection.y);

    vec3 ambient = 0.30 * ambTint * fragColor;

    float diff     = max(dot(norm, lightDir), 0.0);
    float sunHigh   = smoothstep(-0.15, 0.05, sunDirection.y); // dims/warms as the sun sets
    vec3  sunColor  = mix(vec3(1.0, 0.55, 0.30), vec3(1.0), sunHigh);
    vec3  diffuse   = diff * sunColor * fragColor;

    vec3 halfway = normalize(lightDir + viewDir);
    float spec   = pow(max(dot(norm, halfway), 0.0), 48.0);
    vec3 specular = 0.35 * spec * sunColor;

    // Fresnel rim light: a cheap stand-in for environment reflection.
    float fresnel = pow(1.0 - max(dot(norm, viewDir), 0.0), 3.0);
    vec3  rim     = fresnel * 0.25 * ambTint;

    // Aerial perspective: fade distant geometry into the horizon sky color.
    float dist      = length(viewPos - fragPos);
    float fogFactor = 1.0 - exp(-dist * 0.012);
    vec3  fogColor  = ambTint * 1.2;

    vec3 lit = acesFilmic((ambient + diffuse + specular + rim) * 1.2);
    lit = pow(lit, vec3(1.0 / 2.2)); // gamma correction

    vec3 finalColor = mix(lit, fogColor, fogFactor * 0.5);

    outColor = vec4(finalColor, 1.0);
}
