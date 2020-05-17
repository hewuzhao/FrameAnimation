package com.hewuzhao.frameanimation.frameview;

import android.content.Context;
import android.content.ContextWrapper;
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
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;

import com.hewuzhao.frameanimation.R;
import com.hewuzhao.frameanimation.blobcache.BlobCache;
import com.hewuzhao.frameanimation.blobcache.BlobCacheManager;
import com.hewuzhao.frameanimation.blobcache.BlobCacheUtil;
import com.hewuzhao.frameanimation.utils.CommonUtil;
import com.hewuzhao.frameanimation.utils.FrameParseUtil;
import com.hewuzhao.frameanimation.utils.MatrixUtil;
import com.hewuzhao.frameanimation.utils.ResourceUtil;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

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
    private final CustomLinkedBlockingQueue mDecodedBitmapQueue = new CustomLinkedBlockingQueue(BUFFER_SIZE);

    /**
     * 已绘制的Bitmap队列
     */
    private final CustomLinkedBlockingQueue mDrawnBitmapQueue = new CustomLinkedBlockingQueue(BUFFER_SIZE);

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

    /**
     * 开启动画时，可能surface还没创建好，需要在surface创建好后去开启动画
     */
    private boolean mNeedToStartDrawThread;

    /**
     * 在activity的OnPause生命周期时，动画是否在执行中
     */
    private final AtomicBoolean mIsAnimatingWhenOnPause = new AtomicBoolean(false);

    /**
     * 【绘制锁】：[绘制图片]、[销毁texture surface]和[回收所有图片] 这个三个操作需要互斥
     */
    private final ReentrantLock mDrawingLock = new ReentrantLock();

    /**
     * 【解码锁】[解码图片] 跟 [回收所有图片] 这两个操作需要互斥
     */
    private final ReentrantLock mDecodingLock = new ReentrantLock();

    /**
     * 当前在绘制的帧动画资源id
     */
    private int mCurrentResId = -1;

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
                Log.i(TAG, "surface created.");
                mIsSurfaceAlive.set(true);
                if (mNeedToStartDrawThread) {
                    mNeedToStartDrawThread = false;
                    startDrawThread();
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                Log.e(TAG, "surface destroy.");
                mIsSurfaceAlive.set(false);

                // 尝试获取【绘制锁】，防止正在绘制中，surface销毁导致崩溃(超时50毫秒，防止阻塞主线程)
                try {
                    mDrawingLock.tryLock(50, TimeUnit.MILLISECONDS);
                } catch (Exception ex) {
                    ex.printStackTrace();
                } finally {
                    try {
                        mDrawingLock.unlock();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }

                destroy();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });

        final Lifecycle lifecycle = getLifecycle(context);
        if (lifecycle == null) {
            Log.e(TAG, "FrameTextureView, get lifecycle is null.");
        } else {
            lifecycle.addObserver(new LifecycleObserver() {

                @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
                public void onCreate() {

                }

                @OnLifecycleEvent(Lifecycle.Event.ON_START)
                public void onStart() {

                }

                @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
                public void onResume() {
                    Log.i(TAG, "lifecycle, onResume.");
                    if (mIsAnimatingWhenOnPause.get() && !isDestroy()) {
                        resume();
                    }
                    mIsAnimatingWhenOnPause.set(false);
                }

                @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                public void onPause() {
                    Log.i(TAG, "lifecycle, onPause.");
                    if (isStart()) {
                        mIsAnimatingWhenOnPause.set(true);
                        pause();
                    }
                }

                @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
                public void onStop() {

                }

                @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                public void onDestroy() {
                    Log.i(TAG, "lifecycle, onDestroy.");
                    destroy();
                    lifecycle.removeObserver(this);
                }
            });
        }

    }

    private Lifecycle getLifecycle(Context context) {
        if (context instanceof FragmentActivity) {
            return ((FragmentActivity) context).getLifecycle();
        } else {
            while (context instanceof ContextWrapper) {
                if (context instanceof FragmentActivity) {
                    return ((FragmentActivity) context).getLifecycle();
                }
                context = ((ContextWrapper) context).getBaseContext();
            }
        }
        return null;
    }

    private void setStatus(@FrameViewStatus int status) {
        mStatus.set(status);
    }

    /**
     * 设置帧动画资源
     *
     * @param resId 资源id
     */
    public void startWithFrameSrc(@DrawableRes int resId) {
        Log.e(TAG, "startWithFrameSrc, resId=" + resId
                + ", mCurrentResId=" + mCurrentResId + ", status=" + mStatus);
        if (isDestroy()) {
            return;
        }
        if (resId == mCurrentResId) {
            if (isPause()) {
                setStatus(FrameViewStatus.START);
                startDrawThread();
                startDecodeThread(new DecodeRunnable());
            }
            return;
        }
        mCurrentResId = resId;
        // 初始化状态
        setStatus(FrameViewStatus.IDLE);
        // 释放之前的资源
        destroyBitmapQueue();

        // 解码新的资源列表数据
        startDecodeThread(new Runnable() {
            @Override
            public void run() {
                if (isDestroy()) {
                    Log.e(TAG, "startWithFrameSrc, 111 is destroy, return.");
                    return;
                }
                // 解析动画列表数据
                mFrameList = FrameParseUtil.parse(mCurrentResId);
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
                if (isDestroy()) {
                    Log.e(TAG, "startWithFrameSrc, 222 is destroy, return.");
                    return;
                }

                // 预解码两个bitmap到解码队列
                int index = mIndexDecoding.getAndIncrement();
                LinkedBitmap linkedBitmap = new LinkedBitmap();
                linkedBitmap.bitmap = decodeBitmap(mFrameList.getFrameItemByIndex(index));
                putDecodedBitmap(linkedBitmap);

                index = mIndexDecoding.getAndIncrement();
                LinkedBitmap linkedBitmap1 = new LinkedBitmap();
                linkedBitmap1.bitmap = decodeBitmap(mFrameList.getFrameItemByIndex(index));
                putDecodedBitmap(linkedBitmap1);

                if (isDestroy()) {
                    Log.e(TAG, "startWithFrameSrc, 333 is destroy, return.");
                    return;
                }

                Log.e(TAG, "startWithFrameSrc, start draw, resId=" + mCurrentResId);
                if (mDecodeHandler == null) {
                    Log.e(TAG, "startWithFrameSrc, decode handler is null, may be is destroy.");
                    return;
                }
                // 开始播放动画
                setStatus(FrameViewStatus.START);
                mDecodeHandler.post(new DecodeRunnable());
                if (mIsSurfaceAlive.get()) {
                    startDrawThread();
                } else {
                    Log.i(TAG, "startWithFrameSrc, surface is not alive, resId=" + mCurrentResId);
                    mNeedToStartDrawThread = true;
                }
            }
        });
    }

    public boolean isPause() {
        return mStatus.get() == FrameViewStatus.PAUSE;
    }

    public boolean isDestroy() {
        return mStatus.get() == FrameViewStatus.DESTROY;
    }

    public boolean isStart() {
        return mStatus.get() == FrameViewStatus.START;
    }

    /**
     * 暂停动画，停留在当前一帧
     */
    public void pause() {
        Log.i(TAG, "stop, status=" + mStatus);
        if (isDestroy()) {
            return;
        }
        setStatus(FrameViewStatus.PAUSE);
    }

    public void resume() {
        Log.i(TAG, "resume, status=" + mStatus);
        if (isDestroy()) {
            return;
        }

        setStatus(FrameViewStatus.START);
        startDrawThread();
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
        Log.i(TAG, "startDrawThread, resId=" + mCurrentResId + ", status=" + mStatus);
        if (isDestroy()) {
            return;
        }
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
            // 尝试获取【绘制锁】【解码锁】，避免正在解码时回收了mDecodeOptions中的inBitmap，进而导致崩溃
            // (超时50毫秒，防止阻塞主线程)
            mDecodingLock.tryLock(50, TimeUnit.MILLISECONDS);
            mDrawingLock.tryLock(50, TimeUnit.MILLISECONDS);
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
            mDecodeOptions.inBitmap = null;
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            try {
                mDecodingLock.unlock();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            try {
                mDrawingLock.unlock();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private void destroyBitmapQueue() {
        try {
            // 尝试获取【绘制锁】【解码锁】，避免正在解码时回收了mDecodeOptions中的inBitmap，进而导致崩溃
            // (超时50毫秒，防止阻塞主线程)
            mDecodingLock.tryLock(50, TimeUnit.MILLISECONDS);
            mDrawingLock.tryLock(50, TimeUnit.MILLISECONDS);
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
            mDecodeOptions.inBitmap = null;
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            try {
                mDecodingLock.unlock();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            try {
                mDrawingLock.unlock();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * 彻底销毁并释放资源
     */
    public void destroy() {
        if (isDestroy()) {
            return;
        }
        Log.e(TAG, "destroy FrameTextureView, start.");
        setStatus(FrameViewStatus.DESTROY);
        destroyHandler();
        destroyBitmapQueue();
        destroyThread();
        Log.e(TAG, "destroy FrameTextureView, end.");
    }

    private void destroyHandler() {
        if (mDecodeHandler != null) {
            mDecodeHandler.removeCallbacksAndMessages(null);
            mDecodeHandler = null;
        }

        if (mDrawHandler != null) {
            mDrawHandler.removeCallbacksAndMessages(null);
            mDrawHandler = null;
        }
    }

    private void destroyThread() {
        try {
            if (mDecodeHandlerThread != null) {
                mDecodeHandlerThread.quit();
                mDecodeHandlerThread = null;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        try {
            if (mDrawHandlerThread != null) {
                mDrawHandlerThread.quit();
                mDrawHandlerThread = null;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void setScaleType(@FrameScaleType int scaleType) {
        mScaleType = scaleType;
    }

    /**
     * 绘制一帧
     */
    private void drawOneFrame() {
        if (!mIsSurfaceAlive.get()) {
            Log.e(TAG, "drawOneFrame, suface is not alive.");
            return;
        }
        if (!isStart()) {
            Log.e(TAG, "drawOneFrame, status is not start, status=" + mStatus);
            return;
        }
        LinkedBitmap linkedBitmap = getDecodedBitmap();
        if (linkedBitmap != null && linkedBitmap.bitmap != null) {
            if (!isStart()) {
                // 如果是暂停状态，则取出来后，要添加到已绘制的队列里，避免恢复动画时【绘制队列】和【解码队列】都为空的情况
                if (isPause()) {
                    putDrawnBitmap(linkedBitmap);
                }
                return;
            }
            if (!mIsSurfaceAlive.get()) {
                return;
            }
            Bitmap bitmap = linkedBitmap.bitmap;
            MatrixUtil.configureDrawMatrix(bitmap, getWidth(), getHeight(), mDrawMatrix, mScaleType);
            Canvas canvas = null;
            try {
                canvas = lockCanvas();
                if (canvas != null) {
                    // 获取【绘制锁】，防止绘制中，surface销毁了导致崩溃
                    mDrawingLock.lock();
                    try {
                        if (mIsSurfaceAlive.get() && !bitmap.isRecycled()) {
                            clearCanvas(canvas);
                            canvas.drawBitmap(bitmap, mDrawMatrix, null);
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    } finally {
                        mDrawingLock.unlock();
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            } finally {
                try {
                    if (canvas != null) {
                        unlockCanvasAndPost(canvas);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
            putDrawnBitmap(linkedBitmap);
        }
        mIndexDrawing.incrementAndGet();
    }

    /**
     * 存储已绘制的bitmap到【已绘制的Bitmap队列】
     *
     * @param bitmap 已绘制的bitmap（不阻塞）
     */
    private void putDrawnBitmap(LinkedBitmap bitmap) {
        if (isDestroy()) {
            return;
        }
        try {
            mDrawnBitmapQueue.offer(bitmap);
        } catch (Exception ex) {
            ex.printStackTrace();
            Log.e(TAG, "putDrawnBitmap, ex=" + ex);
        }
    }

    /**
     * 存储已经解码的bitmap到【已解码的bitmap队列】
     *
     * @param bitmap 已解码bitmap（可能阻塞）
     */
    private void putDecodedBitmap(LinkedBitmap bitmap) {
        if (isDestroy()) {
            return;
        }
        try {
            mDecodedBitmapQueue.put(bitmap);
        } catch (Exception ex) {
            ex.printStackTrace();
            Log.e(TAG, "putDecodedBitmap, ex=" + ex);
        }
    }

    /**
     * 清除画布上的绘图，准备下一帧
     */
    private void clearCanvas(Canvas canvas) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
    }

    /**
     * 从【解码bitmap队列】里取bitmap
     * （可能会阻塞）
     */
    private LinkedBitmap getDecodedBitmap() {
        if (isDestroy()) {
            return null;
        }
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
     * 从【已绘制的bitmap队列】里取废弃的已绘制的bitmap
     */
    private LinkedBitmap getDrawnBitmap() {
        if (isDestroy()) {
            return null;
        }
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
    private Bitmap decodeBitmap(FrameItem frameItem) {
        final String name = frameItem.getDrawableName();
        Bitmap bitmap = null;
        if (mUseCache) {
            try {
                bitmap = BlobCacheUtil.getCacheBitmapByName(mBlobCache, name, mDecodeOptions.inBitmap);
            } catch (Exception ex) {
                ex.printStackTrace();
                Log.e(TAG, "decodeBitmap, from cache, ex=" + ex + ", name=" + name);
            }

            if (bitmap != null) {
                return bitmap;
            }
        }

        // 获取【解码锁】，避免在解码图片时已经处于destroy状态，导致mDecodeOptions中inBitmap被回收了而崩溃
        mDecodingLock.lock();
        try {
            if (!isDestroy()) {
                bitmap = ResourceUtil.getBitmap(name, mDecodeOptions);
                if (mUseCache) {
                    BlobCacheUtil.saveImageByBlobCache(bitmap, name, mBlobCache);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            Log.e(TAG, "decodeBitmap, ex: " + ex + ", name=" + name);
        } finally {
            mDecodingLock.unlock();
        }
        return bitmap;
    }

    private class DrawRunnable implements Runnable {

        @Override
        public void run() {
            if (!mIsSurfaceAlive.get()) {
                Log.e(TAG, "DrawRunnable, surface is not alive.");
                return;
            }
            if (!isStart()) {
                Log.e(TAG, "DrawRunnable, status is not start, status=" + mStatus);
                return;
            }
            if (isLastFrame()) {
                boolean isOneShot = mFrameList.isOneShot();
                if (isOneShot) {
                    setStatus(FrameViewStatus.END);
                } else {
                    drawOneFrame();
                    mIndexDrawing.set(0);
                }
            } else {
                drawOneFrame();
            }

            if (mDrawHandler != null) {
                if (mFrameList == null) {
                    return;
                }
                if (!isStart()) {
                    return;
                }
                if (!mIsSurfaceAlive.get()) {
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
            if (isDestroy()) {
                Log.e(TAG, "DecodeRunnable, is not start, status=" + mStatus);
                return;
            }
            if (mFrameList == null) {
                Log.e(TAG, "DecodeRunnable, frame list is null.");
                return;
            }

            int index = mIndexDecoding.getAndIncrement();
            if (index >= mFrameList.getFrameItemSize()) {
                index = 0;
                mIndexDecoding.set(1);
            }

            FrameItem frameItem = mFrameList.getFrameItemByIndex(index);
            if (frameItem == null) {
                Log.e(TAG, "DecodeRunnable, index=" + index + ", frameItem is null.");
                return;
            }

            LinkedBitmap linkedBitmap = getDrawnBitmap();
            if (isDestroy()) {
                return;
            }
            if (linkedBitmap == null) {
                linkedBitmap = new LinkedBitmap();
            }
            mDecodeOptions.inBitmap = linkedBitmap.bitmap;
            Bitmap bitmap = decodeBitmap(frameItem);
            if (isDestroy()) {
                return;
            }
            if (bitmap == null) {
                Log.e(TAG, "DecodeRunnable, bitmap is null.");
            } else {
                linkedBitmap.bitmap = bitmap;
                putDecodedBitmap(linkedBitmap);
            }

            if (mDecodeHandler != null) {
                if (isDestroy()) {
                    return;
                }
                mDecodeHandler.post(this);
            }
        }
    }
}
