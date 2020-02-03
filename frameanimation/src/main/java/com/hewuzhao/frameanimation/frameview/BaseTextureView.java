package com.hewuzhao.frameanimation.frameview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.AttributeSet;
import android.util.Log;
import android.view.TextureView;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by hewuzhao
 * on 2020-02-01
 */
public abstract class BaseTextureView extends TextureView implements TextureView.SurfaceTextureListener {
    private static final String TAG = "BaseTextureView";

    private static final String DRAW_THREAD_NAME = "DRAW_HANDLER_THREAD";

    private HandlerThread mDrawHandlerThread;
    private Handler mDrawHandler;
    protected int mFrameDuration = 80;
    private Canvas mCanvas;
    private final AtomicBoolean mIsAlive = new AtomicBoolean(false);


    public BaseTextureView(Context context) {
        super(context);
        init();
    }

    public BaseTextureView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BaseTextureView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public BaseTextureView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    protected void init() {
        setSurfaceTextureListener(this);
    }

    @Override
    public boolean isOpaque() {
        return false;
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.d(TAG, "surface created.");
        mIsAlive.set(true);
        startDrawThread();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        mIsAlive.set(false);
        surface.release();
        stopDrawThread();
        Log.d(TAG, "surface destroy.");

        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    protected void destroy() {
        if (mDrawHandler != null) {
            mDrawHandler.removeCallbacksAndMessages(null);
            mDrawHandler = null;
        }

        if (mDrawHandlerThread != null) {
            mDrawHandlerThread.quit();
            mDrawHandlerThread = null;
        }
    }

    protected int getFrameDuration() {
        return mFrameDuration;
    }

    protected void setFrameDuration(int frameDuration) {
        mFrameDuration = frameDuration;
    }

    private void stopDrawThread() {
        destroy();
    }

    private void startDrawThread() {
        mDrawHandlerThread = new HandlerThread(DRAW_THREAD_NAME);
        mDrawHandlerThread.start();
        mDrawHandler = new Handler(mDrawHandlerThread.getLooper());
        mDrawHandler.post(new DrawRunnable());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int originWidth = getMeasuredWidth();
        int originHeight = getMeasuredHeight();
        int width = widthMode == MeasureSpec.AT_MOST ? getDefaultWidth() : originWidth;
        int height = heightMode == MeasureSpec.AT_MOST ? getDefaultHeight() : originHeight;
        setMeasuredDimension(width, height);
    }

    /**
     * the width is used when wrap_content is set to layout_width
     * the child knows how big it should be
     *
     * @return
     */
    protected abstract int getDefaultWidth();

    /**
     * the height is used when wrap_content is set to layout_height
     * the child knows how big it should be
     *
     * @return
     */
    protected abstract int getDefaultHeight();

    private class DrawRunnable implements Runnable {

        @Override
        public void run() {
            if (!mIsAlive.get()) {
                return;
            }
            try {
                mCanvas = lockCanvas();
                onFrameDraw(mCanvas);
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "DrawRunnable, ex: " + e);
            } finally {
                try {
                    unlockCanvasAndPost(mCanvas);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                onFrameDrawFinish();
            }

            // TODO: 2019-05-08 stop the drawing thread
            if (mDrawHandler != null) {
                mDrawHandler.postDelayed(this, mFrameDuration);
            }
        }
    }

    /**
     * it is will be invoked after one frame is drawn
     */
    protected abstract void onFrameDrawFinish();

    /**
     * draw one frame to the surface by canvas
     *
     * @param canvas
     */
    protected abstract void onFrameDraw(Canvas canvas);

}
