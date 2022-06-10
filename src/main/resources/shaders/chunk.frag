#version 330 core
out vec4 FragColor;

in vec2 TexCoord;
in vec2 BTexCoord;
in vec4 Color;
in vec4 SPos;
in vec4 Viewport;
in mat4 ProjInv;
in vec4 FogColor;
in vec2 FogStartEnd;
flat in vec2 ProvokingTexCoord;

uniform sampler2D atlas;
uniform sampler2D lightTex;

void main()
{
	float relU = mod(SPos.x, 1.0);
	float relV = mod(SPos.y, 1.0);
	
	//if(true||relU > 1 || relV > 1){
	if(false){
		//FragColor = vec4(1, 1, 1, 1);
		//FragColor = vec4((SPos.x), (SPos.y), 0, 1);
		//FragColor = vec4(SPos.xy, 0, 1);
		FragColor = vec4(relU, relV, 0, 1);
		//FragColor = vec4(abs(TexCoord.x - ProvokingTexCoord.x) * 100.0, abs(TexCoord.y - ProvokingTexCoord.y) * 100.0, 0, 1);
		//FragColor = vec4((TexCoord.xy - ProvokingTexCoord.xy) * 100.0, 0, 1);
	} else {
	
	//float goodTexCoordU = ProvokingTexCoord.x + (TexCoord.x - ProvokingTexCoord.x) * relU;
	//float goodTexCoordV = ProvokingTexCoord.y + (TexCoord.y - ProvokingTexCoord.y) * relV;
	
	float goodTexCoordU = ProvokingTexCoord.x + (((TexCoord.x - ProvokingTexCoord.x) / SPos.z) * relU);
	float goodTexCoordV = ProvokingTexCoord.y + (((TexCoord.y - ProvokingTexCoord.y) / SPos.w) * relV);
	
	vec2 goodTexCoord = vec2(goodTexCoordU, goodTexCoordV);
	
	//vec2 goodTexCoord = ProvokingTexCoord + vec2(SPos.x / 3.0, SPos.y) * (1.0/16.0);
	
	vec4 texColor = texture(atlas, goodTexCoord);
	//vec4 texColor = texture(atlas, TexCoord);
	vec4 colorMult = Color/256.0;
	
	vec4 lightyColor = texture(lightTex, (BTexCoord + 8.0) / 256.0);
	
	vec4 rasterColor = ((texColor * colorMult) * lightyColor);
	
	FragColor = rasterColor;
	
	//FragColor = vec4(SPos.z, 1, 1, 1);
	
	//FragColor = texColor;
	//FragColor = vec4(relU, relV, 0, 1);
	
	}
	
	//FragColor = vec4(1, 1, 1, 1);
}
