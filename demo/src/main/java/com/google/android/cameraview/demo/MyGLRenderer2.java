package com.google.android.cameraview.demo;

import android.graphics.PointF;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MyGLRenderer2 implements GLSurfaceView.Renderer {
    private static final String TAG = "PolySample";

    // Camera field of view angle, in degrees (vertical).
    private static final float FOV_Y = 60;

    // Near clipping plane.
    private static final float NEAR_CLIP = 0.1f;

    // Far clipping plane.
    private static final float FAR_CLIP = 1000f;

    // Model spin speed in degrees per second.
    private static final float MODEL_ROTATION_SPEED_DPS = 45.0f;

    // Camera position and orientation:
    private static final float EYE_X = 0;
    private static final float EYE_Y = 3;
    private static final float EYE_Z = -10;
    private static final float TARGET_X = 0;
    private static final float TARGET_Y = 0;
    private static final float TARGET_Z = 0;
    private static final float UP_X = 0;
    private static final float UP_Y = 1;
    private static final float UP_Z = 0;

    public float[] getModelMatrix() {
        return modelMatrix;
    }

    public float[] getViewMatrix() {
        return viewMatrix;
    }

    public float[] getProjMatrix() {
        return projMatrix;
    }

    public float[] getMvpMatrix() {
        return mvpMatrix;
    }

    public float[] getTmpMatrix() {
        return tmpMatrix;
    }

    // Model matrix. Transforms object space into world space.
    public float[] modelMatrix = new float[16];

    // View matrix. Transforms world space into eye space.
    public float[] viewMatrix = new float[16];

    // Projection matrix. Transforms eye space into clip space.
    public float[] projMatrix = new float[16];

    // Model View Projection matrix (product of projection, view and model matrices).
    public float[] mvpMatrix = new float[16];

    // Temporary matrix for calculations.
    public float[] tmpMatrix = new float[16];

    // Scale matrix for calculation
    public float[] scaleMatrix = new float[16];

    // Translation matrix for calculation
    public float[] translationMatrix = new float[16];

    // Rotation matrix for calculation
    public float[] rotationMatrix = new float[16];


    // The shader we use to draw the object.
    private MyShader myShader;

    // If true, we are ready to render the object. If false, the object isn't available yet.
    private boolean readyToRender = false;

    // Handle of the VBO that stores the vertex positions of the object.
    private int positionsVbo;

    // Handle of the VBO that stores the color information for the object.
    private int colorsVbo;

    // Handle of the IBO that stores the sequence of indices we use to draw the object.
    private int ibo;

    // Number of indices present in the IBO.
    private int indexCount;

    // Time (as given by System.currentTimeMillis) when the last frame was rendered.
    private long lastFrameTime;

    // The current model rotation angle, in degrees. This angle is increased each frame to create
    // the spinning animation.
    private float angleDegrees;

    // The RawObject to render. This is set by the main thread when the object is ready to render,
    // and is consumed by the GL thread. Once set, this is never modified.
    private volatile RawObject objectToRender;

    public volatile float mAngle;

    public float getmPosX() {
        return mPosX;
    }

    public void setmPosX(float mPosX) {
        this.mPosX = mPosX;
    }

    public float getmPosY() {
        return mPosY;
    }

    public void setmPosY(float mPosY) {
        this.mPosY = mPosY;
    }

    public float getmScale() {
        return mScale;
    }

    public void setmScale(float mScale) {
        this.mScale = mScale;
    }

    public volatile float mPosX = 0.0f;
    public volatile float mPosY = 0.0f;
    public volatile float mScale = 1.0f;


    public float getAngle() {
        return mAngle;
    }

    public void setAngle(float angle) {
        mAngle = angle;
    }


    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
//        GLES20.glClearColor(0.0f, 0.15f, 0.15f, 1.0f);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        myShader = new MyShader();
    }

    @Override
    public void onDrawFrame(GL10 unused) {

        // Draw background color.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        Matrix.setIdentityM(modelMatrix, 0);

//        Matrix.translateM(modelMatrix, 0, mPosX, mPosY, 0);

        // Make a model matrix that rotates the model about the Y axis so it appears to spin.
        Matrix.setRotateM(modelMatrix, 0, mAngle, 0, 1, 0);

//        Matrix.scaleM(modelMatrix, 0, mScale, mScale, 1);

        // Set the camera position (View matrix)
        Matrix.setLookAtM(viewMatrix, 0,
                // Camera position.
                EYE_X, EYE_Y, EYE_Z,
                // Point that the camera is looking at.
                TARGET_X, TARGET_Y, TARGET_Z,
                // The vector that defines which way is up.
                UP_X, UP_Y, UP_Z);

        // Calculate the MVP matrix (model-view-projection) by multiplying the model, view, and
        // projection matrices together.
        Matrix.multiplyMM(tmpMatrix, 0, viewMatrix, 0, modelMatrix, 0);  // V * M
        Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, tmpMatrix, 0);  // P * V * M

        // objectToRender is volatile, so we capture it in a local variable.
        RawObject obj = objectToRender;

        if (readyToRender) {
            // We're ready to render, so just render using our existing VBOs and IBO.
            myShader.render(mvpMatrix, indexCount, ibo, positionsVbo, colorsVbo);
        } else if (obj != null) {
            // The object is ready, but we haven't consumed it yet. We need to create the VBOs and IBO
            // to render the object.
            indexCount = obj.indexCount;
            ibo = MyGLUtils.createIbo(obj.indices);
            positionsVbo = MyGLUtils.createVbo(obj.positions);
            colorsVbo = MyGLUtils.createVbo(obj.colors);
            // Now we're ready to render the object.
            readyToRender = true;
            Log.d(TAG, "VBOs/IBO created. Now ready to render object.");
        }
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        float aspectRatio = (float) width / height;
        // Recompute the projection matrix, because it depends on the aspect ration of the display.
        Matrix.perspectiveM(projMatrix, 0, FOV_Y, aspectRatio, NEAR_CLIP, FAR_CLIP);
    }

    // Can be called on any thread.
    public void setRawObjectToRender(RawObject rawObject) {
        if (objectToRender != null) throw new RuntimeException("Already had object.");
        // It's safe to set objectToRender from a different thread. It's marked as volatile, and
        // the GL thread will notice it on the next frame.
        objectToRender = rawObject;
        Log.d(TAG, "Received raw object to render.");
    }

    public void SetPositionMatrix()
    {
        // Build Translation Matrix
        Matrix.setIdentityM(translationMatrix, 0);
        Matrix.translateM(translationMatrix, 0, mPosX, mPosY, 1f);
    }

    public void setScaleMatrix()
    {
        // Build Scale Matrix
        Matrix.setIdentityM(scaleMatrix, 0);
        Matrix.scaleM(scaleMatrix, 0, mScale, mScale, mScale);
    }

    public void updateOrientation()
    {
        SetPositionMatrix();
        setScaleMatrix();

        // Then Rotate object around Axis then translate
        Matrix.multiplyMM(tmpMatrix, 0, projMatrix, 0, rotationMatrix, 0);
        // Scale Object first
        Matrix.multiplyMM(modelMatrix, 0, tmpMatrix, 0, scaleMatrix, 0);
    }

    private void addRotation(float angle)
    {
        mAngle += angle;

        //rotateM(float[] m, int mOffset, float a, float x, float y, float z)
        //Rotates matrix m in place by angle a (in degrees) around the axis (x, y, z)
        Matrix.rotateM(rotationMatrix, 0,
                angle,
                0,
                1,
                0);
    }
}
