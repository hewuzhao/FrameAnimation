package com.hewuzhao.frameanimation.frameview;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PorterDuff;
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
import com.hewuzhao.frameanimation.utils.MatrixUtil;
import com.hewuzhao.frameanimation.utils.ResourceUtil;

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

    /**
     * 默认的一帧时间间隔
     */
    private static final int DEFAULT_DURATION = 80;

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

    protected Matrix mDrawMatrix;

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

    protected void init(Context context, AttributeSet attrs) {
        TypedArray array = context.obtainStyledAttributes(attrs, R.styleable.FrameTextureView);
        mUseCache = array.getBoolean(R.styleable.FrameTextureView_useCache, false);
        final int src = array.getResourceId(R.styleable.FrameTextureView_src, -1);
        array.recycle();
        if (mUseCache && src != -1) {
            startDecodeThread(new Runnable() {
                @Override
                public void run() {
                    mFrameList = FrameParseUtil.parse(src);

                    mBlobCache = BlobCacheManager.getInstance().getBlobCache(
                            mFrameList.getFileName(),
                            mFrameList.getMaxEntries(),
                            mFrameList.getMaxBytes(),
                            mFrameList.getVersion());
                }
            });
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
        // 初始化状态
        setStatus(FrameViewStatus.IDLE);
        // 重置资源
        releaseBitmapQueue();
        mDecodeOptions.inBitmap = null;

        // 解码新的资源列表数据
        startDecodeThread(new Runnable() {
            @Override
            public void run() {
                // 解析动画列表数据
                mFrameList = FrameParseUtil.parse(resId);
                List<FrameItem> list = mFrameList.getFrameItemList();
                if (CommonUtil.isEmpty(list)) {
                    Log.e(TAG, "startWithFrameSrc, frame list parse error, list is empty.");
                    return;
                }

                if (mUseCache) {
                    mBlobCache = BlobCacheManager.getInstance().getBlobCache(
                            mFrameList.getFileName(),
                            mFrameList.getMaxEntries(),
                            mFrameList.getMaxBytes(),
                            mFrameList.getVersion());
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
                putDecodedBitmap(linkedBitmap);

                index = mIndexDecoding.getAndIncrement();
                LinkedBitmap linkedBitmap1 = new LinkedBitmap();
                linkedBitmap1.bitmap = decodeBitmap(mFrameList.getFrameItemByIndex(index), mDecodeOptions);
                putDecodedBitmap(linkedBitmap1);

                Log.e(TAG, "startWithFrameSrc, start draw.");
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
        Log.e(TAG, "destroy FrameTextureView, start.");
        setStatus(FrameViewStatus.DESTROY);
        releaseHandler();
        releaseBitmapQueue();
        releaseThread();
        mDecodeOptions.inBitmap = null;
        Log.e(TAG, "destroy FrameTextureView, end.");
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

    /**
     * 绘制
     */
    private void onFrameDraw(Canvas canvas) {
        if (mStatus.get() != FrameViewStatus.START) {
            return;
        }
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
        if (mStatus.get() != FrameViewStatus.START) {
            return;
        }
        Log.e(TAG, "draw on frame start, thread id=" + Thread.currentThread().getId());
        LinkedBitmap linkedBitmap = getDecodedBitmap();
        if (linkedBitmap != null && linkedBitmap.bitmap != null) {
            clearCanvas(canvas);
            MatrixUtil.configureDrawMatrix(linkedBitmap.bitmap, getWidth(), getHeight(), mDrawMatrix, mScaleType);
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            canvas.drawBitmap(linkedBitmap.bitmap, mDrawMatrix, null);
            putDrawnBitmap(linkedBitmap);
        }
        Log.e(TAG, "draw on frame end, thread id=" + Thread.currentThread().getId());

        mIndexDrawing.incrementAndGet();
    }

    /**
     * 存储已绘制的bitmap到『已绘制的Bitmap队列』
     *
     * @param bitmap 已绘制的bitmap
     */
    private void putDrawnBitmap(LinkedBitmap bitmap) {
        Log.e(TAG, "putDrawnBitmap, start.");
        try {
            mDrawnBitmapQueue.offer(bitmap);
        } catch (Exception ex) {
            ex.printStackTrace();
            Log.e(TAG, "putDrawnBitmap, ex=" + ex);
        }
        Log.e(TAG, "putDrawnBitmap, end.");
    }

    /**
     * 存储已经解码的bitmap到『已解码的bitmap队列』
     * @param bitmap 已解码bitmap
     */
    private void putDecodedBitmap(LinkedBitmap bitmap) {
        Log.e(TAG, "putDecodedBitmap, start.");
        try {
            mDecodedBitmapQueue.put(bitmap);
        } catch (Exception ex) {
            ex.printStackTrace();
            Log.e(TAG, "putDecodedBitmap, ex=" + ex);
        }
        Log.e(TAG, "putDecodedBitmap, end.");
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
            try {
                bitmap = BlobCacheUtil.getCacheBitmapByName(mBlobCache, name, options.inBitmap);
            } catch (Exception ex) {
                ex.printStackTrace();
                Log.e(TAG, "decodeBitmap, from cache, ex=" + ex + ", name=" + name);
            } finally {
                t0 = System.currentTimeMillis() - t0;
            }

            if (bitmap == null) {
                Log.e(TAG, "get bitmap from cache, bitmap is null, name=" + name);
            } else {
                Log.e(TAG, "get bitmap from cache, name=" + name + ", cost time=" + t0 + ", name=" + name);
                return bitmap;
            }
        }

        t0 = System.currentTimeMillis();
        try {
            bitmap = ResourceUtil.getBitmap(name, options);
            if (mUseCache) {
                BlobCacheUtil.saveImageByBlobCache(bitmap, name, mBlobCache);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            Log.e(TAG, "decodeBitmap, ex: " + ex + ", name=" + name);
        } finally {
            t0 = System.currentTimeMillis() - t0;
        }
        Log.e(TAG, "decode bitmap from Stream, cost time=" + t0 + ", name=" + name);
        return bitmap;
    }

    private class DrawRunnable implements Runnable {

        @Override
        public void run() {
            Log.e(TAG, "draw runnable, status: " + mStatus.get() + ", isAlive: " + mIsSurfaceAlive.get());
            if (!mIsSurfaceAlive.get()) {
                return;
            }
            if (mStatus.get() != FrameViewStatus.START) {
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

            if (mDrawHandler != null) {
                if (mFrameList == null) {
                    return;
                }
                int duration = DEFAULT_DURATION;
                FrameItem frameItem = mFrameList.getFrameItemByIndex(mIndexDrawing.get());
                if (frameItem != null) {
                    duration = frameItem.getDuration();
                }
                mDrawHandler.postDelayed(this, duration);
            }
        }
    }

    private class DecodeRunnable implements Runnable {

        @Override
        public void run() {
            Log.e(TAG, "decode runnable, status: " + mStatus.get());
            if (mStatus.get() != FrameViewStatus.START) {
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

            Log.e(TAG, "DecodeRunnable, getDrawnBitmap start, size=" + mDrawnBitmapQueue.size());
            LinkedBitmap linkedBitmap = getDrawnBitmap();
            Log.e(TAG, "DecodeRunnable, getDrawnBitmap end, thread id=" + Thread.currentThread().getId());
            if (linkedBitmap == null) {
                linkedBitmap = new LinkedBitmap();
            }
            mDecodeOptions.inBitmap = linkedBitmap.bitmap;
            Bitmap bitmap = decodeBitmap(frameItem, mDecodeOptions);
            if (bitmap == null) {
                Log.e(TAG, "DecodeRunnable, bitmap is null.");
                return;
            }

            if (mStatus.get() != FrameViewStatus.START) {
                Log.e(TAG, "DecodeRunnable, status is end or destroy 222, size=" + mDecodedBitmapQueue.size());
                return;
            }

            linkedBitmap.bitmap = bitmap;
            putDecodedBitmap(linkedBitmap);

            if (mDecodeHandler != null) {
                mDecodeHandler.post(this);
            }
        }
    }
}
