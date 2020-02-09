package com.hewuzhao.frameanimation.frameview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.AttributeSet;
import android.util.Log;
import android.view.TextureView;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by hewuzhao
 * on 2020-02-01
 */
public abstract class BaseTextureView extends TextureView implements TextureView.SurfaceTextureListener {
    private static final String TAG = "BaseTextureView";

    private static final String DRAW_THREAD_NAME = "DRAW_HANDLER_THREAD";

    private final List<Matrix.ScaleToFit> MATRIX_SCALE_ARRAY = Arrays.asList(
            Matrix.ScaleToFit.FILL, Matrix.ScaleToFit.START, Matrix.ScaleToFit.CENTER, Matrix.ScaleToFit.END
    );
    private final AtomicBoolean mIsAlive = new AtomicBoolean(false);
    protected final AtomicInteger mStatus = new AtomicInteger(FrameViewStatus.IDLE);
    private final AtomicInteger mFrameInterval = new AtomicInteger(80);
    @ScaleType
    private int mScaleType = ScaleType.CENTER;
    private HandlerThread mDrawHandlerThread;
    private int mLastScaleType = -1;
    private Handler mDrawHandler;
    protected Matrix mDrawMatrix;
    private int mLastSrcHeight;
    private int mLastDstHeight;
    private int mLastSrcWidth;
    private int mLastDstWidth;
    private Canvas mCanvas;

    @RepeatMode
    protected int mRepeatMode = RepeatMode.INFINITE;
    protected int mRepeatTimes;
    protected int mRepeatedCount;

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
        mDrawMatrix = new Matrix();
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

    protected void stop() {
        Log.e(TAG, "stop.");
        mStatus.set(FrameViewStatus.STOP);
    }

    protected void start() {
        Log.e(TAG, "start.");
        mStatus.set(FrameViewStatus.START);
        startDrawThread();
    }

    protected void resetData() {
        mLastScaleType = -1;
        mLastSrcHeight = 0;
        mLastDstHeight = 0;
        mLastSrcWidth = 0;
        mLastDstWidth = 0;
        if (mDrawHandler != null) {
            mDrawHandler.removeCallbacksAndMessages(null);
            mDrawHandler = null;
        }

        if (mDrawHandlerThread != null) {
            mDrawHandlerThread.quit();
            mDrawHandlerThread = null;
        }
    }

    protected void destroy() {
        mStatus.set(FrameViewStatus.DESTROY);
        if (mDrawHandler != null) {
            mDrawHandler.removeCallbacksAndMessages(null);
            mDrawHandler = null;
        }

        if (mDrawHandlerThread != null) {
            mDrawHandlerThread.quit();
            mDrawHandlerThread = null;
        }
    }

    public void setScaleType(@ScaleType int scaleType) {
        mScaleType = scaleType;
    }

    public void setFrameInterval(int interval) {
        mFrameInterval.set(interval);
    }

    public void setRepeatMode(@RepeatMode int repeatMode) {
        mRepeatMode = repeatMode;
        if (mRepeatMode == RepeatMode.ONCE) {
            mRepeatTimes = 1;
        } else if (mRepeatMode == RepeatMode.TWICE) {
            mRepeatTimes = 2;
        }
    }

    private void stopDrawThread() {
        destroy();
    }

    private void startDrawThread() {
        if (mDrawHandlerThread == null) {
            mDrawHandlerThread = new HandlerThread(DRAW_THREAD_NAME);
        }
        if (!mDrawHandlerThread.isAlive()) {
            mDrawHandlerThread.start();
        }

        if (mDrawHandler == null) {
            mDrawHandler = new Handler(mDrawHandlerThread.getLooper());
        }
        mDrawHandler.post(new DrawRunnable());
    }

    private class DrawRunnable implements Runnable {

        @Override
        public void run() {
            Log.e(TAG, "draw runnable, status: " + mStatus.get() + ", isAlive: " + mIsAlive.get());
            if (!mIsAlive.get()) {
                return;
            }
            if (mStatus.get() == FrameViewStatus.DESTROY
                    || mStatus.get() == FrameViewStatus.STOP
                    || mStatus.get() == FrameViewStatus.END) {
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
                mDrawHandler.postDelayed(this, mFrameInterval.get());
            }
        }
    }

    /**
     * 根据ScaleType配置绘制bitmap的Matrix
     *
     * @param bitmap
     */
    protected void configureDrawMatrix(Bitmap bitmap) {
        int srcWidth = bitmap.getWidth();
        int dstWidth = getWidth();
        int srcHeight = bitmap.getHeight();
        int dstHeight = getHeight();
        boolean nothingChanged = ((mLastScaleType == mScaleType)
                && (mLastSrcWidth == srcWidth)
                && (mLastDstWidth == dstWidth)
                && (mLastSrcHeight == srcHeight)
                && (mLastDstHeight == dstHeight));
        if (nothingChanged) {
            return;
        }
        mLastSrcWidth = srcWidth;
        mLastDstWidth = dstWidth;
        mLastSrcHeight = srcHeight;
        mLastDstHeight = dstHeight;
        mLastScaleType = mScaleType;
        switch (mScaleType) {
            case ScaleType.MATRIX: {
                return;
            }
            case ScaleType.CENTER: {
                mDrawMatrix.setTranslate(Math.round((dstWidth - srcWidth) * 0.5f), Math.round((dstHeight - srcHeight) * 0.5f));
                break;
            }
            case ScaleType.CENTER_CROP: {
                float scale;
                float dx = 0f;
                float dy = 0f;
                //按照高缩放
                if (dstHeight * srcWidth > dstWidth * srcHeight) {
                    scale = dstHeight / srcHeight;
                    dx = (dstWidth - srcWidth * scale) * 0.5f;
                } else {
                    scale = dstWidth / srcWidth;
                    dy = (dstHeight - srcHeight * scale) * 0.5f;
                }
                mDrawMatrix.setScale(scale, scale);
                mDrawMatrix.postTranslate(dx, dy);
                break;
            }
            case ScaleType.CENTER_INSIDE: {
                float scale;
                //小于dst时不缩放
                if (srcWidth <= dstWidth && srcHeight <= dstHeight) {
                    scale = 1.0f;
                } else {
                    scale = Math.min(dstWidth / srcWidth, dstHeight / srcHeight);
                }
                float dx = Math.round((dstWidth - srcWidth * scale) * 0.5f);
                float dy = Math.round((dstHeight - srcHeight * scale) * 0.5f);
                mDrawMatrix.setScale(scale, scale);
                mDrawMatrix.postTranslate(dx, dy);
                break;
            }
            default: {
                RectF srcRect = new RectF(0f, 0f, bitmap.getWidth(), bitmap.getHeight());
                RectF dstRect = new RectF(0f, 0f, getWidth(), getHeight());
                mDrawMatrix.setRectToRect(srcRect, dstRect, MATRIX_SCALE_ARRAY.get(mScaleType - 1));
            }
        }
    }

    @Retention(RetentionPolicy.SOURCE)
    public @interface RepeatMode {
        /**
         * play once
         */
        int ONCE = 1;

        /**
         * play twice
         */
        int TWICE = 2;

        /**
         * play infinity
         */
        int INFINITE = 3;
    }

    @Retention(RetentionPolicy.SOURCE)
    public @interface ScaleType {
        /**
         * scale using the bitmap matrix when drawing.
         */
        int MATRIX = 0;

        /**
         * @see Matrix.ScaleToFit.FILL
         */
        int FIT_XY = 1;

        /**
         * @see Matrix.ScaleToFit.START
         */
        int FIT_START = 2;

        /**
         * @see Matrix.ScaleToFit.CENTER
         */
        int FIT_CENTER = 3;

        /**
         * @see Matrix.ScaleToFit.END
         */
        int FIT_END = 4;

        /**
         * Center the image in the view, but perform no scaling.
         */
        int CENTER = 5;

        /**
         * Scale the image uniformly (maintain the image's aspect ratio) so that both dimensions (width and height) of the image
         * will be equal to or larger than the corresponding dimension of the view.
         */
        int CENTER_CROP = 6;

        /**
         * Scale the image uniformly (maintain the image's aspect ratio) so that both dimensions (width and height) of the image
         * will be equal to or less than the corresponding dimension of the view.
         */
        int CENTER_INSIDE = 7;
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
