#version 330 core
out vec4 FragColor;

in vec2 TexCoord;
in vec2 BTexCoord;
in vec4 Color;
in vec4 Viewport;
in mat4 ProjInv;
in vec4 FogColor;
in vec2 FogStartEnd;
in float FogFactor;

uniform sampler2D atlas;
uniform sampler2D lightTex;

void main()
{
	vec4 texColor = texture(atlas, TexCoord
#ifdef SHORT_UV
	/ 32768.0
#endif
	);
	
	vec4 colorMult = Color/256.0;
	
	vec4 lightyColor = texture(lightTex, (BTexCoord + 8.0) / 256.0);

	vec4 rasterColor = 
#ifdef PASS_0
		vec4((texColor.xyz * colorMult.xyz) * lightyColor.xyz, texColor.w);
#else
		(texColor * colorMult) * lightyColor;
#endif
	
#ifdef RENDER_FOG
		FragColor = vec4(mix(FogColor.xyz, rasterColor.xyz, FogFactor), rasterColor.w);
#else
		FragColor = rasterColor;
#endif
}
