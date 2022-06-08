#version 330 core
out vec4 FragColor;

in vec2 TexCoord;
in vec2 BTexCoord;
in vec4 Color;
in vec4 Viewport;
in mat4 ProjInv;
in vec4 FogColor;
in vec2 FogStartEnd;

uniform sampler2D atlas;
uniform sampler2D lightTex;

void main()
{	
	vec4 texColor = texture(atlas, TexCoord);
	vec4 colorMult = Color/256.0;
	
	vec4 lightyColor = texture(lightTex, (BTexCoord + 8.0) / 256.0);
	
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
	float f = (e - z) / (e - s);
	f = clamp(f, 0, 1);
	FragColor = mix(FogColor, rasterColor, f);
}
