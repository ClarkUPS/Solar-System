#version 430	// version 4.30

layout (location=0) in vec3 position;  // input is a triple
layout (location=1) in vec2 vertexST;

uniform mat4 mv_matrix;	// access to MV matrix
uniform mat4 p_matrix;	// access to P matrix

out vec2 fragmentST;

void main(void) {	// output a quadruple
    gl_Position = p_matrix * mv_matrix * vec4(position, 1.0);
    fragmentST = vertexST;
}