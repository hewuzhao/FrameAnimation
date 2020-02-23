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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author hewuzhao
 * @date 2020-02-01
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
    @FrameScaleType
    private int mScaleType = FrameScaleType.CENTER;
    private HandlerThread mDrawHandlerThread;
    private int mLastScaleType = -1;
    private Handler mDrawHandler;
    protected Matrix mDrawMatrix;
    private int mLastSrcHeight;
    private int mLastDstHeight;
    private int mLastSrcWidth;
    private int mLastDstWidth;
    private Canvas mCanvas;

    @FrameRepeatMode
    protected int mRepeatMode = FrameRepeatMode.INFINITE;
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
        setOpaque(false);
        setSurfaceTextureListener(this);
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

    public void setScaleType(@FrameScaleType int scaleType) {
        mScaleType = scaleType;
    }

    public void setFrameInterval(int interval) {
        mFrameInterval.set(interval);
    }

    public void setRepeatMode(@FrameRepeatMode int repeatMode) {
        mRepeatMode = repeatMode;
        if (mRepeatMode == FrameRepeatMode.ONCE) {
            mRepeatTimes = 1;
        } else if (mRepeatMode == FrameRepeatMode.TWICE) {
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
     * <p>
     * 参考ImageView的配置规则
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
            case FrameScaleType.MATRIX: {
                return;
            }
            case FrameScaleType.CENTER: {
                mDrawMatrix.setTranslate(Math.round((dstWidth - srcWidth) * 0.5f), Math.round((dstHeight - srcHeight) * 0.5f));
                break;
            }
            case FrameScaleType.CENTER_CROP: {
                float scale;
                float dx = 0f;
                float dy = 0f;
                //按照高缩放
                if (dstHeight * srcWidth > dstWidth * srcHeight) {
                    scale = (float) dstHeight / (float) srcHeight;
                    dx = (dstWidth - srcWidth * scale) * 0.5f;
                } else {
                    scale = (float) dstWidth / (float) srcWidth;
                    dy = (dstHeight - srcHeight * scale) * 0.5f;
                }
                mDrawMatrix.setScale(scale, scale);
                mDrawMatrix.postTranslate(dx, dy);
                break;
            }
            case FrameScaleType.CENTER_INSIDE: {
                float scale;
                //小于dst时不缩放
                if (srcWidth <= dstWidth && srcHeight <= dstHeight) {
                    scale = 1.0f;
                } else {
                    scale = Math.min((float) dstWidth / (float) srcWidth, (float) dstHeight / (float) srcHeight);
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
