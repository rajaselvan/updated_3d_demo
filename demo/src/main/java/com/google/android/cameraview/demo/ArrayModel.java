package com.google.android.cameraview.demo;

import android.opengl.GLES20;
import android.opengl.Matrix;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.nio.FloatBuffer;

public class ArrayModel extends Model {
    protected static final int BYTES_PER_FLOAT = 4;
    protected static final int COORDS_PER_VERTEX = 3;
    protected static final int VERTEX_STRIDE = COORDS_PER_VERTEX * BYTES_PER_FLOAT;
    protected static final int INPUT_BUFFER_SIZE = 0x10000;

    // Vertices, normals will be populated by subclasses
    protected int vertexCount;
    protected FloatBuffer vertexBuffer;
    protected FloatBuffer normalBuffer;
    protected FloatBuffer colorBuffer;   // Buffer for color-array (NEW)


    private static final String FRAGMENT_SHADER_SOURCE =
            "precision mediump float;\n" +
                    // Color (received from vertex shader).
                    "varying vec4 vColor;\n" +
                    "void main() {\n" +
                    // Since this is a simple unlit shader, we just set the fragment color to the color
                    // we got from the vertex shader.
                    "  gl_FragColor = vColor;\n" +
                    "}\n";


    @Override
    public void init(float boundSize) {
        if (GLES20.glIsProgram(glProgram)) {
            GLES20.glDeleteProgram(glProgram);
            glProgram = -1;
        }
        glProgram = Util.compileProgram(R.raw.model_vertex, R.raw.single_light_fragment,
                new String[] {"a_Position", "a_Normal", "a_Color"});


        // Get the handles to our shader parameters.
        GLES20.glUseProgram(glProgram);
        super.init(boundSize);
    }

    @Override
    public void draw(float[] viewMatrix, float[] projectionMatrix, @NonNull Light light) {
        if (vertexBuffer == null || normalBuffer == null) {
            return;
        }
        GLES20.glUseProgram(glProgram);

        int mvpMatrixHandle = GLES20.glGetUniformLocation(glProgram, "u_MVP");
        int positionHandle = GLES20.glGetAttribLocation(glProgram, "a_Position");
        int normalHandle = GLES20.glGetAttribLocation(glProgram, "a_Normal");
        int colorHandle = GLES20.glGetAttribLocation(glProgram, "a_Color");
        int lightPosHandle = GLES20.glGetUniformLocation(glProgram, "u_LightPos");
        int ambientColorHandle = GLES20.glGetUniformLocation(glProgram, "u_ambientColor");
        int diffuseColorHandle = GLES20.glGetUniformLocation(glProgram, "u_diffuseColor");
        int specularColorHandle = GLES20.glGetUniformLocation(glProgram, "u_specularColor");


        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false,
                VERTEX_STRIDE, vertexBuffer);

        GLES20.glEnableVertexAttribArray(normalHandle);
        GLES20.glVertexAttribPointer(normalHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false,
                VERTEX_STRIDE, normalBuffer);

        GLES20.glEnableVertexAttribArray(colorHandle);
        GLES20.glVertexAttribPointer(colorHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false,
                VERTEX_STRIDE, colorBuffer);


        Matrix.multiplyMM(mvMatrix, 0, viewMatrix, 0, modelMatrix, 0);
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvMatrix, 0);
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0);

        GLES20.glUniform3fv(lightPosHandle, 1, light.getPositionInEyeSpace(), 0);
        GLES20.glUniform3fv(ambientColorHandle, 1, light.getAmbientColor(), 0);
        GLES20.glUniform3fv(diffuseColorHandle, 1, light.getDiffuseColor(), 0);
        GLES20.glUniform3fv(specularColorHandle, 1, light.getSpecularColor(), 0);

        drawFunc();

        GLES20.glDisableVertexAttribArray(normalHandle);
        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(colorHandle);
    }

    protected void drawFunc() {
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount);
    }
}
