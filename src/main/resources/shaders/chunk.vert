#version 330 core
layout (location = 0) in vec3 aPos;
layout (location = 1) in vec2 aTexCoord;
layout (location = 2) in vec2 aBTexCoord;
layout (location = 3) in vec4 aDaColor;

uniform mat4 modelView;
uniform mat4 proj;
uniform mat4 projInv;
uniform vec4 viewport;
uniform vec4 fogColor;
uniform vec2 fogStartEnd;

uniform vec3 playerPos;

out vec2 TexCoord;
out vec2 BTexCoord;
out vec4 DaColor;
out vec4 Viewport;
out mat4 ProjInv;
out vec4 FogColor;
out vec2 FogStartEnd;

void main()
{
    gl_Position = proj * modelView * (vec4(aPos - playerPos, 1.0) + vec4(0, 0.12, 0, 0));
	TexCoord = aTexCoord;
	BTexCoord = aBTexCoord;
	DaColor = aDaColor;
	Viewport = viewport;
	ProjInv = projInv;
	FogColor = fogColor;
	FogStartEnd = fogStartEnd;
}