#version 330 core
layout (location = 0) in vec3 aPos;
layout (location = 1) in vec2 aTexCoord;
layout (location = 2) in vec2 aBTexCoord;
layout (location = 3) in vec4 aColor;

#ifdef SIMPLIFY_MESHES
layout (location = 4) in vec4 aMQPos; // if the first coordinate is 255, it means: disable megaquad processing for this quad
#endif

uniform mat4 modelView;
uniform mat4 proj;
uniform mat4 projInv;
uniform vec4 viewport;
uniform vec4 fogColor;
uniform vec2 fogStartEnd;
uniform int fogMode;
uniform float fogDensity;

uniform vec3 playerPos;

out vec2 TexCoord;
out vec2 BTexCoord;
out vec4 Color;
out vec4 MQPos;
out vec4 Viewport;
out mat4 ProjInv;
out vec4 FogColor;
out vec2 FogStartEnd;
out float FogFactor; // -1 means: disable fog
flat out vec2 ProvokingTexCoord;

void main()
{
    gl_Position = proj * modelView * (vec4(aPos - playerPos, 1.0) + vec4(0, 0.12, 0, 0));
	TexCoord = aTexCoord;
	BTexCoord = aBTexCoord;
	Color = aColor;
	
#ifdef SIMPLIFY_MESHES
	MQPos = aMQPos;
#endif
	
	Viewport = viewport;
	ProjInv = projInv;
	FogColor = fogColor;
	
	if(fogStartEnd.x >= 0 && fogStartEnd.y >= 0){
		float s = fogStartEnd.x;
		float e = fogStartEnd.y;
		vec4 eyePos = (modelView * (vec4(aPos - playerPos, 1.0) + vec4(0, 0.12, 0, 0)));
		float c = length(eyePos);
		
		float fogFactor = fogMode == 0x2601
						? clamp((e - c) / (e - s), 0, 1) /* GL_LINEAR */
						: exp(-fogDensity * c); /* GL_EXP */
						
		
		FogFactor = fogFactor;	
	} else {
		FogFactor = -1;
	}
	FogStartEnd = fogStartEnd;
	
	ProvokingTexCoord = aTexCoord;
}