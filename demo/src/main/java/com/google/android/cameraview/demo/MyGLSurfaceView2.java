package com.google.android.cameraview.demo;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.Toast;

import static android.content.ContentValues.TAG;

public class MyGLSurfaceView2 extends GLSurfaceView {
    // The renderer responsible for rendering the contents of this view.
    private final MyGLRenderer2 renderer;
    private Context mContext;
    private GestureDetector mTapScrollDetector;
    private ScaleGestureDetector mScaleDetector;
    private final float TOUCH_SCALE_FACTOR = 180.0f / 320;

    // We can be in one of these 3 states
    private static final int NONE = 0;
    private static final int ZOOM = 2;
    private int mode = NONE;

    // Remember some things for zooming
    private PointF start = new PointF();
    private PointF mid = new PointF();
    private float oldDist = 1f;
    static int INVALID_POINTER_ID = -100;
    private int mTwoFingerPointerId = INVALID_POINTER_ID;
    private float mLastTouchX, mLastTouchY;

    public MyGLSurfaceView2(Context context) {
        this(context, null);
    }

    public MyGLSurfaceView2(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        mContext = context;
        // We want OpenGL ES 2.
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        setZOrderOnTop(true);
        renderer = new MyGLRenderer2();

        // Set the Renderer for drawing on the GLSurfaceView
        setRenderer(renderer);
        mTapScrollDetector = new GestureDetector(context, new MyTapScrollListener());
        mScaleDetector = new ScaleGestureDetector(context.getApplicationContext(),
                new ScaleListener());

        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    public MyGLRenderer2 getRenderer() {
        return renderer;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        mTapScrollDetector.onTouchEvent(event);
        mScaleDetector.onTouchEvent(event);

        final int action = event.getAction();

        switch (action) {

            case MotionEvent.ACTION_DOWN: {
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                // track the drag only if two fingers are placed on screen
                if (mTwoFingerPointerId != INVALID_POINTER_ID) {

                    final float x = event.getX(mTwoFingerPointerId);
                    final float y = event.getY(mTwoFingerPointerId);

                    // Calculate the distance moved
                    final float dx = x - mLastTouchX;
                    final float dy = y - mLastTouchY;

                    // Remember this touch position for the next move event
                    mLastTouchX = x;
                    mLastTouchY = y;
                    renderer.setmPosX(dx);
                    renderer.setmPosY(dy);
                }
                break;
            }

            case MotionEvent.ACTION_UP: {
                mTwoFingerPointerId = INVALID_POINTER_ID;
                break;
            }

            case MotionEvent.ACTION_CANCEL: {
                mTwoFingerPointerId = INVALID_POINTER_ID;
                break;
            }

            case MotionEvent.ACTION_POINTER_DOWN: {
                // detected two fingers, start the drag
                mTwoFingerPointerId = event.getActionIndex();
                final float x = event.getX(mTwoFingerPointerId);
                final float y = event.getY(mTwoFingerPointerId);

                // Remember where we started (for dragging)
                mLastTouchX = x;
                mLastTouchY = y;
                break;
            }

            case MotionEvent.ACTION_POINTER_UP: {
                // two fingers are not placed on screen anymore
                mTwoFingerPointerId = INVALID_POINTER_ID;
                break;
            }
        }

        requestRender();
        return true;
    }


    /**
     * Determine the space between the first two fingers
     */
    private float spacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    /**
     * Calculate the mid point of the first two fingers
     */
    private void midPoint(PointF point, MotionEvent event) {
        float x = event.getX(0) + event.getX(1);
        float y = event.getY(0) + event.getY(1);
        point.set(x / 2, y / 2);
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            renderer.setmScale(detector.getScaleFactor());
            requestRender();
            return true;
        }
    }

    class MyTapScrollListener extends GestureDetector.SimpleOnGestureListener {

        public boolean onDoubleTap(MotionEvent event) {
            renderer.setmScale(1);
            requestRender();
            return true;
        }

        // function is called if user scrolls with one/two fingers
        // we ignore the call if two fingers are placed on screen
        public boolean onScroll(MotionEvent e1, MotionEvent e2,
                                float distanceX, float distanceY) {

            if (mTwoFingerPointerId == INVALID_POINTER_ID) {
                renderer.setAngle(
                        renderer.getAngle() +
                                ((distanceX + distanceY) * TOUCH_SCALE_FACTOR));
                requestRender();

//                ScrollNative(distanceX, distanceY, e2.getX(), e2.getY());
            }
            return true;
        }
    }
}
