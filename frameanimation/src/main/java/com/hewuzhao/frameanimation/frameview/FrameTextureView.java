package com.hewuzhao.frameanimation.frameview;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.AttributeSet;
import android.util.Log;
import android.view.TextureView;

import androidx.annotation.DrawableRes;

import com.hewuzhao.frameanimation.R;
import com.hewuzhao.frameanimation.blobcache.BlobCache;
import com.hewuzhao.frameanimation.blobcache.BlobCacheManager;
import com.hewuzhao.frameanimation.blobcache.BlobCacheUtil;
import com.hewuzhao.frameanimation.utils.CommonUtil;
import com.hewuzhao.frameanimation.utils.FrameParseUtil;
import com.hewuzhao.frameanimation.utils.ResourceUtil;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author hewuzhao
 * @date 2020-02-01
 */
public class FrameTextureView extends TextureView {
    private static final String TAG = "FrameTextureView";

    private static final String DRAW_THREAD_NAME = "DRAW_HANDLER_THREAD";
    private static final String DECODE_THREAD_NAME = "DECODE_HANDLER_THREAD";

    /**
     * bitmap缓存个数
     */
    private static final int BUFFER_SIZE = 3;

    private final List<Matrix.ScaleToFit> MATRIX_SCALE_ARRAY = Arrays.asList(
            Matrix.ScaleToFit.FILL, Matrix.ScaleToFit.START, Matrix.ScaleToFit.CENTER, Matrix.ScaleToFit.END
    );
    private boolean mUseCache;
    private FrameList mFrameList;
    private BlobCache mBlobCache;

    /**
     * 正在绘制的索引
     */
    private final AtomicInteger mIndexDrawing = new AtomicInteger();

    /**
     * 正在解码的索引
     */
    private final AtomicInteger mIndexDecoding = new AtomicInteger();

    /**
     * surface 是否alive
     */
    private final AtomicBoolean mIsSurfaceAlive = new AtomicBoolean(false);

    /**
     * 状态
     */
    protected final AtomicInteger mStatus = new AtomicInteger(FrameViewStatus.IDLE);

    /**
     * 帧动画的时间间隔
     */
    private final AtomicInteger mFrameInterval = new AtomicInteger(80);

    /**
     * 缩放类型
     */
    @FrameScaleType
    private int mScaleType = FrameScaleType.CENTER;

    /**
     * 已解码Bitmap存储队列
     */
    private final LinkedBlockingQueue mDecodedBitmapQueue = new LinkedBlockingQueue(BUFFER_SIZE);

    /**
     * 已绘制的Bitmap队列
     */
    private final LinkedBlockingQueue mDrawnBitmapQueue = new LinkedBlockingQueue(BUFFER_SIZE);

    /**
     * bitmap解码线程
     */
    private HandlerThread mDecodeHandlerThread;

    /**
     * 绘制线程
     */
    private HandlerThread mDrawHandlerThread;

    /**
     * 解码handler
     */
    private Handler mDecodeHandler;

    /**
     * 绘制handler
     */
    private Handler mDrawHandler;

    /**
     * 解码图片的配置
     */
    private final BitmapFactory.Options mDecodeOptions = new BitmapFactory.Options();

    private int mLastScaleType = -1;
    protected Matrix mDrawMatrix;
    private int mLastSrcHeight;
    private int mLastDstHeight;
    private int mLastSrcWidth;
    private int mLastDstWidth;

    public FrameTextureView(Context context) {
        super(context);
        init(context, null);
    }

    public FrameTextureView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public FrameTextureView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    public FrameTextureView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs);
    }

    protected void init(Context context, AttributeSet attrs) {
        TypedArray array = context.obtainStyledAttributes(attrs, R.styleable.FrameTextureView);
        mUseCache = array.getBoolean(R.styleable.FrameTextureView_useCache, true);
        int src = array.getResourceId(R.styleable.FrameTextureView_src, -1);
        array.recycle();
        if (src != -1) {
            mFrameList = FrameParseUtil.parse(src);

            mBlobCache = BlobCacheManager.getInstance().getBlobCache(
                    mFrameList.getFileName(),
                    mFrameList.getMaxEntries(),
                    mFrameList.getMaxBytes(),
                    mFrameList.getVersion());

            for (FrameItem item : mFrameList.getFrameItemList()) {
                if (!BlobCacheUtil.checkCacheByName(item.getDrawableName(), mBlobCache)) {
                    BlobCacheUtil.saveImageByBlobCache(item.getDrawableName(), mBlobCache);
                }
            }

        }

        mDecodeOptions.inMutable = true;
        mDecodeOptions.inDensity = Bitmap.DENSITY_NONE;
        mDecodeOptions.inScaled = false;

        mDrawMatrix = new Matrix();
        setOpaque(false);
        setSurfaceTextureListener(new SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                Log.d(TAG, "surface created.");
                mIsSurfaceAlive.set(true);
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                Log.e(TAG, "surface destroy.");
                mIsSurfaceAlive.set(false);
                surface.release();
                destroy();
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });
    }

    public void setUseCache(boolean useCache) {
        mUseCache = useCache;
    }

    private void setStatus(@FrameViewStatus int status) {
        mStatus.set(status);
    }

    /**
     * 设置帧动画资源
     *
     * @param resId 资源id
     */
    public void startWithFrameSrc(final @DrawableRes int resId) {
        Log.e(TAG, "startWithFrameSrc, resId=" + resId);
        // 暂停播放
        setStatus(FrameViewStatus.PAUSE);
        // 重置资源
        resetDataForChangeSrc();

        // 解码新的资源列表数据
        startDecodeThread(new Runnable() {
            @Override
            public void run() {
                // 解析动画列表数据
                mFrameList = FrameParseUtil.parse(resId);

                mBlobCache = BlobCacheManager.getInstance().getBlobCache(
                        mFrameList.getFileName(),
                        mFrameList.getMaxEntries(),
                        mFrameList.getMaxBytes(),
                        mFrameList.getVersion());

                // 检查和缓存到BlobCache
                for (FrameItem item : mFrameList.getFrameItemList()) {
                    if (!BlobCacheUtil.checkCacheByName(item.getDrawableName(), mBlobCache)) {
                        BlobCacheUtil.saveImageByBlobCache(item.getDrawableName(), mBlobCache);
                    }
                }


                // 纠正索引
                mIndexDecoding.set(0);
                mIndexDrawing.set(0);

                // 重置队列
                resetBitmapQueue();

                // 预解码两个bitmap到解码队列
                int index = mIndexDecoding.getAndIncrement();
                LinkedBitmap linkedBitmap = new LinkedBitmap();
                linkedBitmap.bitmap = decodeBitmap(mFrameList.getFrameItemByIndex(index), mDecodeOptions);
                try {
                    mDecodedBitmapQueue.put(linkedBitmap);
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e(TAG, "startWithFrameSrc 1, ex=" + e);
                }

                index = mIndexDecoding.getAndIncrement();
                LinkedBitmap linkedBitmap1 = new LinkedBitmap();
                linkedBitmap1.bitmap = decodeBitmap(mFrameList.getFrameItemByIndex(index), mDecodeOptions);
                try {
                    mDecodedBitmapQueue.put(linkedBitmap1);
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e(TAG, "startWithFrameSrc 2, ex=" + e);
                }

                Log.e(TAG, "start draw.");
                // 开始播放动画
                setStatus(FrameViewStatus.START);
                startDrawThread();
                mDecodeHandler.post(new DecodeRunnable());
            }
        });
    }

    /**
     * 开始帧动画
     */
    public void start() {
        Log.i(TAG, "start frame textureview, status: " + mStatus.get());
        if (mStatus.get() == FrameViewStatus.START || mStatus.get() == FrameViewStatus.DESTROY) {
            return;
        }
        if (mStatus.get() != FrameViewStatus.PAUSE) {
            mIndexDecoding.set(0);
            mIndexDrawing.set(0);
        }
        setStatus(FrameViewStatus.START);
        startDrawThread();
        startDecodeThread(new DecodeRunnable());
    }

    public boolean isPause() {
        return mStatus.get() == FrameViewStatus.PAUSE;
    }

    /**
     * 暂停动画，停留在当前一帧
     */
    public void pause() {
        Log.e(TAG, "stop.");
        setStatus(FrameViewStatus.PAUSE);
    }

    /**
     * 开启解码线程
     */
    private void startDecodeThread(Runnable runnable) {
        if (mDecodeHandlerThread == null) {
            mDecodeHandlerThread = new HandlerThread(DECODE_THREAD_NAME);
        }
        if (!mDecodeHandlerThread.isAlive()) {
            mDecodeHandlerThread.start();
        }
        if (mDecodeHandler == null) {
            mDecodeHandler = new Handler(mDecodeHandlerThread.getLooper());
        }
        mDecodeHandler.removeCallbacksAndMessages(null);
        mDecodeHandler.post(runnable);
    }

    /**
     * 开启绘制线程
     */
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
        mDrawHandler.removeCallbacksAndMessages(null);
        mDrawHandler.post(new DrawRunnable());
    }

    /**
     * 重置数据
     */
    public void resetDataForChangeSrc() {
        releaseHandler();
        resetBitmapQueue();
        releaseThread();
        mDecodeOptions.inBitmap = null;
    }

    private void resetBitmapQueue() {
        try {
            mDecodedBitmapQueue.resetData();
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        try {
            mDrawnBitmapQueue.resetData();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * 彻底销毁并释放资源
     */
    public void destroy() {
        Log.i(TAG, "destroy FrameTextureView.");
        setStatus(FrameViewStatus.DESTROY);
        releaseHandler();
        releaseBitmapQueue();
        releaseThread();
    }

    private void releaseHandler() {
        if (mDecodeHandler != null) {
            mDecodeHandler.removeCallbacksAndMessages(null);
            mDecodeHandler = null;
        }

        if (mDrawHandler != null) {
            mDrawHandler.removeCallbacksAndMessages(null);
            mDrawHandler = null;
        }
    }

    private void releaseBitmapQueue() {
        try {
            mDecodedBitmapQueue.destroy();
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        try {
            mDrawnBitmapQueue.destroy();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void releaseThread() {
        if (mDecodeHandlerThread != null) {
            mDecodeHandlerThread.quit();
            mDecodeHandlerThread = null;
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

    /**
     * 根据ScaleType配置绘制bitmap的Matrix
     * <p>
     * 参考ImageView的配置规则
     */
    private void configureDrawMatrix(Bitmap bitmap) {
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
     * 绘制
     */
    private void onFrameDraw(Canvas canvas) {
        if (isLastFrame()) {
            boolean isOneShot = mFrameList.isOneShot();
            Log.e(TAG, "onFrameDraw, is last frame, isOneShot=" + isOneShot);
            if (isOneShot) {
                setStatus(FrameViewStatus.END);
            } else {
                mIndexDrawing.set(0);
                mIndexDecoding.set(0);
                drawOneFrame(canvas);
            }
        } else {
            drawOneFrame(canvas);
        }
    }

    /**
     * 绘制一帧
     */
    private void drawOneFrame(Canvas canvas) {
        Log.e(TAG, "draw on frame start.");
        LinkedBitmap linkedBitmap = getDecodedBitmap();
        if (linkedBitmap != null && linkedBitmap.bitmap != null) {
            clearCanvas(canvas);
            configureDrawMatrix(linkedBitmap.bitmap);
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            canvas.drawBitmap(linkedBitmap.bitmap, mDrawMatrix, null);
            putDrawnBitmap(linkedBitmap);
        }
        Log.e(TAG, "draw on frame end.");

        mIndexDrawing.incrementAndGet();
    }

    /**
     * 存储已绘制的bitmap到“已绘制的Bitmap队列”
     *
     * @param bitmap 已绘制的bitmap
     */
    private void putDrawnBitmap(LinkedBitmap bitmap) {
        try {
            mDrawnBitmapQueue.offer(bitmap);
        } catch (Exception ex) {
            ex.printStackTrace();
            Log.e(TAG, "putDrawnBitmap, ex=" + ex);
        }
    }

    /**
     * 清除画布上的绘图，准备下一帧
     */
    private void clearCanvas(Canvas canvas) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
    }

    /**
     * 从解码bitmap队列里取bitmap
     * 该方法可能会阻塞（新的bitmap可能还没准备好）
     */
    private LinkedBitmap getDecodedBitmap() {
        LinkedBitmap bitmap = null;
        try {
            bitmap = mDecodedBitmapQueue.take();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bitmap;
    }

    /**
     * 是否帧动画已经绘制到最后一帧了
     */
    private boolean isLastFrame() {
        if (mFrameList == null) {
            return true;
        }
        return mIndexDrawing.get() >= mFrameList.getFrameItemSize() - 1;
    }

    /**
     * 从“已绘制的bitmap队列”里取废弃的已绘制的bitmap
     */
    private LinkedBitmap getDrawnBitmap() {
        LinkedBitmap bitmap = null;
        try {
            bitmap = mDrawnBitmapQueue.take();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bitmap;
    }

    /**
     * 解码图片
     */
    private Bitmap decodeBitmap(FrameItem frameItem, BitmapFactory.Options options) {
        final String name = frameItem.getDrawableName();
        Bitmap bitmap = null;
        long t0 = System.currentTimeMillis();
        if (mUseCache) {
            bitmap = BlobCacheUtil.getCacheBitmapByName(mBlobCache, name, options);
            if (bitmap == null) {
                Log.e(TAG, "get bitmap from cache, bitmap is null, name=" + name);
            }
        }

        long t1 = System.currentTimeMillis();
        t0 = t1 - t0;
        try {
            if (bitmap == null) {
                bitmap = ResourceUtil.getBitmap(name, options);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            Log.e(TAG, "decode, ex: " + ex + ", name=" + name);
        } finally {
            t1 = System.currentTimeMillis() - t1;
        }
        String bitmapSize = bitmap == null ? "0B" : CommonUtil.convertUnit(bitmap.getByteCount());
        Log.e(TAG, "decode bitmap, name: " + name + ", bitmap size: " + bitmapSize
                + ", BlobCache: " + t0 + ", Stream: " + t1);
        return bitmap;
    }

    private class DrawRunnable implements Runnable {

        @Override
        public void run() {
            Log.e(TAG, "draw runnable, status: " + mStatus.get() + ", isAlive: " + mIsSurfaceAlive.get());
            if (!mIsSurfaceAlive.get()) {
                return;
            }
            if (mStatus.get() == FrameViewStatus.DESTROY
                    || mStatus.get() == FrameViewStatus.PAUSE
                    || mStatus.get() == FrameViewStatus.END) {
                return;
            }

            Canvas canvas = null;
            try {
                canvas = lockCanvas();
                onFrameDraw(canvas);
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "DrawRunnable 1, ex: " + e);
            } finally {
                try {
                    unlockCanvasAndPost(canvas);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    Log.e(TAG, "DrawRunnable 2, ex: " + ex);
                }
            }

            // TODO: 2019-05-08 stop the drawing thread
            if (mDrawHandler != null) {
                mDrawHandler.postDelayed(this, mFrameInterval.get());
            }
        }
    }

    private class DecodeRunnable implements Runnable {

        @Override
        public void run() {
            Log.e(TAG, "decode runnable, status: " + mStatus.get());
            if (mStatus.get() == FrameViewStatus.DESTROY
                    || mStatus.get() == FrameViewStatus.PAUSE
                    || mStatus.get() == FrameViewStatus.END) {
                Log.e(TAG, "DecodeRunnable, status is end or destroy.");
                return;
            }

            if (mFrameList == null) {
                Log.e(TAG, "DecodeRunnable, mFrameList is null.");
                return;
            }

            int index = mIndexDecoding.getAndIncrement();
            if (index >= mFrameList.getFrameItemSize()) {
                index = 0;
                mIndexDecoding.set(0);
            }
            Log.e(TAG, "DecodeRunnable, index=" + index);
            FrameItem frameItem = mFrameList.getFrameItemByIndex(index);
            if (frameItem == null) {
                Log.e(TAG, "DecodeRunnable, index=" + index + ", frameItem is null.");
                return;
            }

            LinkedBitmap linkedBitmap = getDrawnBitmap();
            if (linkedBitmap == null) {
                linkedBitmap = new LinkedBitmap();
            }
            mDecodeOptions.inBitmap = linkedBitmap.bitmap;
            Bitmap bitmap = decodeBitmap(frameItem, mDecodeOptions);
            if (bitmap == null) {
                Log.e(TAG, "DecodeRunnable, bitmap is null.");
                return;
            }
            linkedBitmap.bitmap = bitmap;
            try {
                mDecodedBitmapQueue.put(linkedBitmap);
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "DecodeRunnable, ex=" + e);
            }

            if (mDecodeHandler != null) {
                mDecodeHandler.post(this);
            }
        }
    }
}
