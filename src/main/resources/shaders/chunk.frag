#version 330 core
out vec4 FragColor;

in vec2 TexCoord;
in vec2 BTexCoord;
in vec4 Color;
in vec4 MQPos;
in vec4 Viewport;
in mat4 ProjInv;
in vec4 FogColor;
in vec2 FogStartEnd;
in float FogFactor;
flat in vec2 ProvokingTexCoord;

uniform sampler2D atlas;
uniform sampler2D lightTex;

void main()
{
	vec2 goodTexCoord = TexCoord;

#ifdef SIMPLIFY_MESHES
	if(MQPos.x <= 254){
		float wrappedU = mod(MQPos.x, 1.0);
		float wrappedV = mod(MQPos.y, 1.0);
		
		goodTexCoord = ProvokingTexCoord.xy + (((TexCoord.xy - ProvokingTexCoord.xy) / MQPos.zw) * vec2(wrappedU, wrappedV));
	}
#endif
	
	vec4 texColor = texture(atlas, goodTexCoord
#ifdef SHORT_UV
	/ 16384.0
#endif
	);
	
	vec4 colorMult = Color/256.0;
	
	vec4 lightyColor = texture(lightTex, (BTexCoord + 8.0) / 256.0);
	
	vec4 rasterColor = ((texColor * colorMult) * lightyColor);
	
#ifdef RENDER_FOG
		FragColor = vec4(mix(FogColor.xyz, rasterColor.xyz, FogFactor), rasterColor.w);
#else
		FragColor = rasterColor;
#endif
}
