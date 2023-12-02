#version 330 core
layout (location = ATTRIB_POS) in vec3 aPos;
layout (location = ATTRIB_TEXTURE) in vec2 aTexCoord;
#ifdef RPLE
layout (location = ATTRIB_BRIGHTNESS_RED) in vec2 aBTexCoordR;
layout (location = ATTRIB_BRIGHTNESS_GREEN) in vec2 aBTexCoordG;
layout (location = ATTRIB_BRIGHTNESS_BLUE) in vec2 aBTexCoordB;
#else
layout (location = ATTRIB_BRIGHTNESS) in vec2 aBTexCoord;
#endif
layout (location = ATTRIB_COLOR) in vec4 aColor;

uniform mat4 modelView;
uniform mat4 proj;
uniform mat4 projInv;
uniform vec4 viewport;
uniform vec4 fogColor;
uniform vec2 fogStartEnd;
uniform int fogMode;
uniform float fogDensity;

uniform vec3 renderOffset;

out vec2 TexCoord;
#ifdef RPLE
out vec2 BTexCoordR;
out vec2 BTexCoordG;
out vec2 BTexCoordB;
#else
out vec2 BTexCoord;
#endif
out vec4 Color;
out vec4 Viewport;
out mat4 ProjInv;
out vec4 FogColor;
out vec2 FogStartEnd;
out float FogFactor; // -1 means: disable fog

void main()
{
    vec4 untransformedPos = (vec4(aPos, 1.0) + vec4(renderOffset.x, renderOffset.y + 0.12, renderOffset.z, 0));
    gl_Position = proj * modelView * untransformedPos;
	TexCoord = aTexCoord;
#ifdef RPLE
	BTexCoordR = aBTexCoordR;
	BTexCoordG = aBTexCoordG;
	BTexCoordB = aBTexCoordB;
#else
	BTexCoord = aBTexCoord;
#endif
	Color = aColor;
	
	Viewport = viewport;
	ProjInv = projInv;
	FogColor = fogColor;
	
	if(fogStartEnd.x >= 0 && fogStartEnd.y >= 0){
		float s = fogStartEnd.x;
		float e = fogStartEnd.y;
		float c = length(untransformedPos);
		
		float fogFactor = fogMode == 0x2601
						? clamp((e - c) / (e - s), 0, 1) /* GL_LINEAR */
						: exp(-fogDensity * c); /* GL_EXP */
						
		
		FogFactor = fogFactor;	
	} else {
		FogFactor = -1;
	}
	FogStartEnd = fogStartEnd;
}