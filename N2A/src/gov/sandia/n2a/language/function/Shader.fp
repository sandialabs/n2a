struct LightSource
{
    bool  on;
    vec3  position;
    vec3  direction;
    vec3  ambient;
    vec3  diffuse;
    vec3  specular;
    float spotExponent;
    float spotCutoff;
    float attenuation0;
    float attenuation1;
    float attenuation2;
};
uniform LightSource light;

struct Material
{
    vec3  ambient;
    vec4  diffuse;
    vec3  emission;
    vec3  specular;
    float shininess;
};
uniform Material material;

varying vec3 vN; // normal direction
varying vec3 vL; // vector to light source

void main()
{
    vec3 N = normalize (vN);
    float distance = length (vL);
    vec3 L = vL / distance;  // normalize

    float diffuseFactor  = max (0, dot (N, L));  // Lambertian reflection
    float specularFactor = 0;
    if (diffuseFactor > 0)
    {
        // Blinn-Phong specularity
        vec3 H         = normalize (L + vec3 (0,0,1)); // light half vector
        float angle    = max (0, dot (N, H));
        specularFactor = pow (angle, material.shininess);
    }

    distance *= distance;
    float attenuation = light.attenuation0 + (light.attenuation1 + light.attenuation2 * distance) * distance;
    vec3 ambient  =      material.ambient  * light.ambient;
    vec3 diffuse  = vec3(material.diffuse) * light.diffuse  * diffuseFactor  / attenuation;
    vec3 specular =      material.specular * light.specular * specularFactor / attenuation;

    vec3 color = clamp(material.emission + ambient + diffuse + specular, 0, 1);
    gl_FragColor = vec4(color, material.diffuse.a);
}
