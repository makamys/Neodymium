#version 330 core
out vec4 FragColor;

in vec2 TexCoord;
#ifdef RPLE
in vec2 BTexCoordR;
in vec2 BTexCoordG;
in vec2 BTexCoordB;
#else
in vec2 BTexCoord;
#endif
in vec4 Color;
in vec4 Viewport;
in mat4 ProjInv;
in vec4 FogColor;
in vec2 FogStartEnd;
in float FogFactor;

uniform sampler2D atlas;
#ifdef RPLE
uniform sampler2D lightTexR;
uniform sampler2D lightTexG;
uniform sampler2D lightTexB;
#else
uniform sampler2D lightTex;
#endif

void main()
{
	vec4 texColor = texture(atlas, TexCoord
#ifdef SHORT_UV
	/ 32768.0
#endif
	);
	
	vec4 colorMult = Color/256.0;
	
	vec4 lightyColor =
#ifdef RPLE
	// RPLE assumes that we're using the legacy opengl pipeline, so it creates 3 textures:
	//  color dark       bright
	//   RED: Cyan    -> White
	// GREEN: Magenta -> White
	//  BLUE: Yellow  -> White
	// In each texture, only a single channel varies, while the other 2 are set to 1, so the result becomes:
	// (r, 1, 1) * (1, g, 1) * (1, 1, b) = (r, g, b)
	texture(lightTexR, (BTexCoordR + 32767) / 65535.0) *
	texture(lightTexG, (BTexCoordG + 32767) / 65535.0) *
	texture(lightTexB, (BTexCoordB + 32767) / 65535.0);
#else
	texture(lightTex, (BTexCoord + 8.0) / 256.0);
#endif

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
