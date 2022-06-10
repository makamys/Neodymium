#version 330 core
layout (location = 0) in vec3 aPos;
layout (location = 1) in vec2 aTexCoord;
layout (location = 2) in vec2 aBTexCoord;
layout (location = 3) in vec4 aColor;
layout (location = 4) in vec4 aSPos;

uniform mat4 modelView;
uniform mat4 proj;
uniform mat4 projInv;
uniform vec4 viewport;
uniform vec4 fogColor;
uniform vec2 fogStartEnd;

uniform vec3 playerPos;

out vec2 TexCoord;
out vec2 BTexCoord;
out vec4 Color;
out vec4 SPos;
out vec4 Viewport;
out mat4 ProjInv;
out vec4 FogColor;
out vec2 FogStartEnd;
out float FogFactor;
flat out vec2 ProvokingTexCoord;

void main()
{
    gl_Position = proj * modelView * (vec4(aPos - playerPos, 1.0) + vec4(0, 0.12, 0, 0));
	TexCoord = aTexCoord;
	BTexCoord = aBTexCoord;
	Color = aColor;
	SPos = aSPos;
	Viewport = viewport;
	ProjInv = projInv;
	FogColor = fogColor;
	
	float s = fogStartEnd.x;
	float e = fogStartEnd.y;
	vec4 eyePos = (modelView * (vec4(aPos - playerPos, 1.0) + vec4(0, 0.12, 0, 0)));
	
	float fogFactor = clamp((e - length(eyePos)) / (e - s), 0, 1);
	
	FogFactor = fogFactor;
	
	FogStartEnd = fogStartEnd;
	
	ProvokingTexCoord = aTexCoord;
}