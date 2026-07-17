#version 330 core

in vec3 vDirection;

uniform vec3 sunDirection;

out vec4 outColor;

// Preetham-style Rayleigh/Mie atmospheric scattering (the same physical
// model behind three.js' Sky shader), adapted to this project's uniforms.

const float PI = 3.141592653589793;

const float turbidity        = 3.0;
const float rayleighCoeff    = 1.5;
const float mieCoeff         = 0.004;
const float mieDirectionalG  = 0.82;

const vec3 up = vec3(0.0, 1.0, 0.0);

// Rayleigh scattering coefficients for 680/550/450 nm wavelengths.
const vec3 totalRayleigh = vec3(5.804542996e-6, 1.356291142e-5, 3.026590247e-5);

// Mie scattering constants (Preetham primaries).
const vec3 mieConst = vec3(1.839991851e14, 2.779802392e14, 4.079047954e14);

const float rayleighZenithLength  = 8.4e3;
const float mieZenithLength       = 1.25e3;
const float sunAngularDiameterCos = 0.9999566769;
const float sunIntensity          = 1000.0;
const float cutoffAngle           = PI / 1.95;
const float steepness             = 1.5;

vec3 totalMie(float t) {
    float c = (0.2 * t) * 10e-18;
    return 0.434 * c * mieConst;
}

float rayleighPhase(float cosTheta) {
    return (3.0 / (16.0 * PI)) * (1.0 + cosTheta * cosTheta);
}

float hgPhase(float cosTheta, float g) {
    float g2  = g * g;
    float inv = 1.0 / pow(1.0 - 2.0 * g * cosTheta + g2, 1.5);
    return (1.0 / (4.0 * PI)) * ((1.0 - g2) * inv);
}

void main() {
    vec3 dir = normalize(vDirection);
    vec3 sun = normalize(sunDirection);

    float sunFade   = 1.0 - clamp(1.0 - exp(sun.y), 0.0, 1.0);
    float rayleighC = rayleighCoeff - (1.0 - sunFade);
    vec3  betaR     = totalRayleigh * rayleighC;
    vec3  betaM     = totalMie(turbidity) * mieCoeff;

    float zenithAngle = acos(max(0.0, dot(up, dir)));
    float denom = cos(zenithAngle) + 0.15 * pow(93.885 - degrees(zenithAngle), -1.253);
    float sR = rayleighZenithLength / denom;
    float sM = mieZenithLength / denom;
    vec3  fex = exp(-(betaR * sR + betaM * sM));

    float sunE = sunIntensity * max(0.0, 1.0 - exp(-(cutoffAngle - acos(dot(sun, up))) / steepness));

    float cosTheta   = dot(dir, sun);
    vec3  betaRTheta = betaR * rayleighPhase(cosTheta * 0.5 + 0.5);
    vec3  betaMTheta = betaM * hgPhase(cosTheta, mieDirectionalG);

    vec3 lin = pow(sunE * ((betaRTheta + betaMTheta) / (betaR + betaM)) * (1.0 - fex), vec3(1.5));
    lin *= mix(vec3(1.0),
               pow(sunE * ((betaRTheta + betaMTheta) / (betaR + betaM)) * fex, vec3(0.5)),
               clamp(pow(1.0 - dot(up, sun), 5.0), 0.0, 1.0));

    vec3 L0 = vec3(0.05) * fex;

    float sunDisk = smoothstep(sunAngularDiameterCos, sunAngularDiameterCos + 0.00002, cosTheta);
    L0 += sunE * 19000.0 * fex * sunDisk;

    vec3 color = (lin + L0) * 0.04;
    color += vec3(0.0, 0.001, 0.0025) * 0.3;
    color = pow(color, vec3(1.0 / (1.2 + 1.2 * sunFade)));

    // Filmic finish so the raw scattering output looks believable straight
    // out of this un-post-processed renderer.
    color = color / (color + vec3(1.0)); // Reinhard tonemap
    color = pow(color, vec3(1.0 / 2.2)); // gamma

    outColor = vec4(color, 1.0);
}
