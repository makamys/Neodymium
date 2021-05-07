#version 330 core
out vec4 FragColor;

in vec2 TexCoord;
in vec2 BTexCoord;
in vec4 DaColor;
in vec4 Viewport;
in mat4 ProjInv;
in vec4 FogColor;
in vec2 FogStartEnd;

uniform sampler2D atlas;
uniform sampler2D lightTex;

void main()
{
	//FragColor = texture(lightTex, TexCoord);
	//FragColor = texture(lightTex, vec2(8.0/256.0,8.0/256.0));
	//FragColor = texture(lightTex, vec2(0, 0));
	//FragColor = vec4(texture(lightTex, vec2(0, 0)).xyz,1);
	//FragColor = vec4(BTexCoord.xy / 256, 0, 1);
	//FragColor = texture(lightTex, (BTexCoord + 8.0) / 256.0);
	//FragColor = texture(light, vec2(16,16));
	//FragColor = vec4(1,0,0,1);
	//FragColor = DaColor/256.0;
	
	vec4 texColor = texture(atlas, TexCoord);
	vec4 colorMult = DaColor/256.0;
	
	vec4 lightyColor = texture(lightTex, (BTexCoord + 8.0) / 256.0);
	//vec4 lightyMultColor = vec4(lightyColor.x * colorMult.x, lightyColor.y * colorMult.y, lightyColor.z * colorMult.z, lightyColor.w * colorMult.w);
	
	//FragColor = vec4(texColor.x * colorMult.x, texColor.y * colorMult.y, texColor.z * colorMult.z, texColor.w * colorMult.w);
	//vec4 texMultColor = texColor * colorMult;
	
	//FragColor = texMultColor;// + (lightyColor - vec4(0.5,0.5,0.5,0.0));
	//FragColor = vec4(lightyColor.xyz, 1);
	//FragColor = ((texColor * colorMult) * lightyColor);
	vec4 rasterColor = ((texColor * colorMult) * lightyColor);
	float s = FogStartEnd.x;
	float e = FogStartEnd.y;
	
	vec4 ndcPos;
	ndcPos.xy = ((2.0 * gl_FragCoord.xy) - (2.0 * Viewport.xy)) / (Viewport.zw) - 1;
	ndcPos.z = (2.0 * gl_FragCoord.z - gl_DepthRange.near - gl_DepthRange.far) /
		(gl_DepthRange.far - gl_DepthRange.near);
	ndcPos.w = 1.0;

	vec4 clipPos = ndcPos / gl_FragCoord.w;
	vec4 eyePos = ProjInv * clipPos;
	
	float z = length(eyePos);
	/*
	if(z < 200){
		discard;
	}
	*/
	float f = (e - z) / (e - s);
	f = clamp(f, 0, 1);
	FragColor = mix(FogColor, rasterColor, f);
}