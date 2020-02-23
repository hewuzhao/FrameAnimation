package com.hewuzhao.frameanimation.frameview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.AttributeSet;
import android.util.Log;

import com.hewuzhao.frameanimation.blobcache.BlobCacheManager;
import com.hewuzhao.frameanimation.blobcache.BlobCacheParams;
import com.hewuzhao.frameanimation.blobcache.BlobCacheUtil;
import com.hewuzhao.frameanimation.utils.CommonUtil;
import com.hewuzhao.frameanimation.utils.ResourceUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author hewuzhao
 * @date 2020-02-01
 */
public class FrameTextureView extends BaseTextureView {
    private static final String TAG = "FrameTextureView";

    public static final int INVALID_INDEX = Integer.MAX_VALUE;
    private int mBufferSize = 3;
    public static final String DECODE_THREAD_NAME = "DECODE_HANDLER_THREAD";

    /**
     * the resources of frame animation
     */
    private List<FrameImage> mFrameImageList = new ArrayList<>();

    /**
     * the index of bitmap resource which is decoding
     */
    private final AtomicInteger mBitmapIdIndex = new AtomicInteger();

    /**
     * the index of frame which is drawing
     */
    private final AtomicInteger mFrameIndex = new AtomicInteger(INVALID_INDEX);

    /**
     * decoded bitmaps stores in this queue
     * consumer is drawing thread, producer is decoding thread.
     */
    private LinkedBlockingQueue mDecodedBitmapQueue = new LinkedBlockingQueue(mBufferSize);

    /**
     * bitmaps already drawn by canvas stores in this queue
     * consumer is decoding thread, producer is drawing thread.
     */
    private LinkedBlockingQueue mDrawnBitmapQueue = new LinkedBlockingQueue(mBufferSize);

    /**
     * the thread for decoding bitmaps
     */
    private HandlerThread mDecodeHandlerThread;
    /**
     * the Runnable describes how to decode one bitmap
     */
    private DecodeRunnable mDecodeRunnable;
    /**
     * this mDecodeHandler helps to decode bitmap one after another
     */
    private Handler mDecodeHandler;
    private BitmapFactory.Options mDecodeOptions;

    public FrameTextureView(Context context) {
        super(context);
    }

    public FrameTextureView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FrameTextureView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public FrameTextureView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void init() {
        super.init();
        mDecodeOptions = new BitmapFactory.Options();
        mDecodeOptions.inMutable = true;
        Log.d(TAG, "init FrameTextureView.");
        mDecodeHandlerThread = new HandlerThread(DECODE_THREAD_NAME);
    }

    @Override
    protected void onFrameDrawFinish() {
    }

    public boolean setFrameImageListPath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        List<FrameImage> frameImageList;
        try {
            frameImageList = new FrameImageParser().parse(BlobCacheParams.NAME_FRAME_LIST_FOLDER + File.separator + path);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "setFrameImageListPath, ex: " + e);
            return false;
        }

        if (CommonUtil.isEmpty(frameImageList)) {
            return false;
        }

        setFrameImageList(frameImageList);
        return true;
    }

    /**
     * set the materials of frame animation which is an array of resource
     */
    public void setFrameImageList(List<FrameImage> list) {
        if (list == null || list.size() == 0) {
            return;
        }
        resetData();
        init();
        mFrameImageList = new ArrayList<>(list);
        //by default, take the first bitmap's dimension into consideration
        preloadFrames();
        mDecodeRunnable = new DecodeRunnable(mBitmapIdIndex.get(), mFrameImageList, mDecodeOptions);
    }

    /**
     * load the first several frames of animation before it is started
     */
    private void preloadFrames() {
        if (!mDecodeHandlerThread.isAlive()) {
            mDecodeHandlerThread.start();
        }
        if (mDecodeHandler == null) {
            mDecodeHandler = new Handler(mDecodeHandlerThread.getLooper());
        }
        mDecodeHandler.post(new Runnable() {
            @Override
            public void run() {
                int index = mBitmapIdIndex.getAndIncrement();
                putDecodedBitmap(mFrameImageList.get(index), mDecodeOptions, new LinkedBitmap());
                index = mBitmapIdIndex.getAndIncrement();
                putDecodedBitmap(mFrameImageList.get(index), mDecodeOptions, new LinkedBitmap());
            }
        });
    }

    @Override
    protected void onFrameDraw(Canvas canvas) {
        if (isFinish()) {
            onFrameAnimationEnd();
            if (mRepeatMode == FrameRepeatMode.INFINITE) {
                repeatDrawOneFrame(canvas);
            } else if (mRepeatedCount < mRepeatTimes) {
                repeatDrawOneFrame(canvas);
                mRepeatedCount++;
            } else {
                mRepeatedCount = 0;
                mStatus.set(FrameViewStatus.END);
            }
        } else {
            drawOneFrame(canvas);
        }
    }

    private void repeatDrawOneFrame(Canvas canvas) {
        if (mStatus.get() == FrameViewStatus.DESTROY
                || mStatus.get() == FrameViewStatus.STOP
                || mStatus.get() == FrameViewStatus.END) {
            return;
        }

        mFrameIndex.set(0);
        if (mDecodeHandlerThread == null) {
            mDecodeHandlerThread = new HandlerThread(DECODE_THREAD_NAME);
        }
        if (!mDecodeHandlerThread.isAlive()) {
            mDecodeHandlerThread.start();
        }
        if (mDecodeHandler == null) {
            mDecodeHandler = new Handler(mDecodeHandlerThread.getLooper());
        }

        if (mDecodeRunnable == null) {
            mDecodeRunnable = new DecodeRunnable(mBitmapIdIndex.get(), mFrameImageList, mDecodeOptions);
        }

        mDecodeRunnable.setIndex(0);
        mDecodeHandler.post(mDecodeRunnable);

        drawOneFrame(canvas);
    }

    /**
     * draw a single frame which is a bitmap
     *
     * @param canvas
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
        mFrameIndex.incrementAndGet();
    }

    /**
     * invoked when frame animation is done
     */
    private void onFrameAnimationEnd() {
        reset();
    }

    /**
     * reset the index of frame, preparing for the next frame animation
     */
    private void reset() {
        mFrameIndex.set(INVALID_INDEX);
        mBitmapIdIndex.set(0);
    }

    /**
     * whether frame animation is finished
     *
     * @return true: animation is finished, false: animation is doing
     */
    private boolean isFinish() {
        return mFrameIndex.get() >= mFrameImageList.size() - 1;
    }

    /**
     * start frame animation from the first frame
     */
    @Override
    public void start() {
        Log.i(TAG, "start frame textureview, status: " + mStatus.get());
        if (mStatus.get() == FrameViewStatus.START) {
            return;
        }

        super.start();

        if (mStatus.get() == FrameViewStatus.DESTROY) {
            return;
        }
        mFrameIndex.set(0);
        if (mDecodeHandlerThread == null) {
            mDecodeHandlerThread = new HandlerThread(DECODE_THREAD_NAME);
        }
        if (!mDecodeHandlerThread.isAlive()) {
            mDecodeHandlerThread.start();
        }
        if (mDecodeHandler == null) {
            mDecodeHandler = new Handler(mDecodeHandlerThread.getLooper());
        }

        if (mDecodeRunnable == null) {
            mDecodeRunnable = new DecodeRunnable(mBitmapIdIndex.get(), mFrameImageList, mDecodeOptions);
        }

        mDecodeRunnable.setIndex(0);
        mDecodeHandler.post(mDecodeRunnable);
    }

    /**
     * decode bitmap by BitmapFactory.decodeStream(), it is about twice faster than BitmapFactory.decodeResource()
     *
     * @param frameImage   the bitmap resource
     * @param options
     * @return
     */
    private Bitmap decodeBitmap(FrameImage frameImage, BitmapFactory.Options options) {
        options.inScaled = false;
        Bitmap bitmap = null;
        long t0 = System.currentTimeMillis();
        if (BlobCacheManager.getInstance().isImageBlobCacheInited() && BlobCacheManager.getInstance().isUseBlobCache()) {
            bitmap = BlobCacheUtil.getCacheBitmapByName(frameImage.getName(), options);
        }

        long t1 = System.currentTimeMillis();
        t0 = t1 - t0;
        try {
            if (bitmap == null) {
                bitmap = ResourceUtil.getBitmap(frameImage, options);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            Log.e(TAG, "decode, ex: " + ex);
        } finally {
            t1 = System.currentTimeMillis() - t1;
        }
        String bitmapSize = bitmap == null ? "0B" : CommonUtil.convertUnit(bitmap.getByteCount());
        Log.e(TAG, "decode bitmap, name: " + frameImage.getName() + ", bitmap size: " + bitmapSize
                + ", BlobCache: " + t0 + ", Stream: " + t1);
        return bitmap;
    }

    private void putDecodedBitmapByReuse(FrameImage frameImage, BitmapFactory.Options options) {
        LinkedBitmap linkedBitmap = getDrawnBitmap();
        if (linkedBitmap == null) {
            linkedBitmap = new LinkedBitmap();
        }
        options.inBitmap = linkedBitmap.bitmap;
        putDecodedBitmap(frameImage, options, linkedBitmap);
    }

    private void putDecodedBitmap(FrameImage frameImage, BitmapFactory.Options options, LinkedBitmap linkedBitmap) {
        Bitmap bitmap = decodeBitmap(frameImage, options);
        if (bitmap == null) {
            return;
        }
        linkedBitmap.bitmap = bitmap;
        try {
            if (mDecodedBitmapQueue != null) {
                mDecodedBitmapQueue.put(linkedBitmap);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void putDrawnBitmap(LinkedBitmap bitmap) {
        try {
            if (mDrawnBitmapQueue != null) {
                mDrawnBitmapQueue.offer(bitmap);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * get bitmap which already drawn by canvas
     *
     * @return
     */
    private LinkedBitmap getDrawnBitmap() {
        LinkedBitmap bitmap = null;
        try {
            if (mDrawnBitmapQueue != null) {
                bitmap = mDrawnBitmapQueue.take();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bitmap;
    }

    @Override
    public void stop() {
        super.stop();
    }

    /**
     * clear out the drawing on canvas,preparing for the next frame
     * * @param canvas
     */
    private void clearCanvas(Canvas canvas) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
    }

    /**
     * recycle the bitmap used by frame animation.
     * Usually it should be invoked when the ui of frame animation is no longer visible
     */
    @Override
    public void destroy() {
        Log.i(TAG, "destroy FrameTextureView.");
        super.destroy();

        if (mDecodeHandler != null) {
            mDecodeHandler.removeCallbacksAndMessages(null);
            mDecodeHandler = null;
        }

        if (mDecodeRunnable != null) {
            mDecodeRunnable.destroy();
            mDecodeRunnable = null;
        }

        if (mDecodeHandlerThread != null) {
            mDecodeHandlerThread.quit();
            mDecodeHandlerThread = null;
        }

        destroyDrawnBitmapQueue();
        destroyDecodedBitmapQueue();

        if (mFrameImageList != null) {
            mFrameImageList.clear();
        }
    }

    private void destroyDecodedBitmapQueue() {
        try {
            if (mDecodedBitmapQueue != null) {
                mDecodedBitmapQueue.destroy();
                mDecodedBitmapQueue = null;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void destroyDrawnBitmapQueue() {
        try {
            if (mDrawnBitmapQueue != null) {
                mDrawnBitmapQueue.destroy();
                mDrawnBitmapQueue = null;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    protected void resetData() {
        Log.e(TAG, "resetData().");
        super.resetData();
        reset();
        if (mDecodeHandler != null) {
            mDecodeHandler.removeCallbacksAndMessages(null);
            mDecodeHandler = null;
        }

        if (mDecodeRunnable != null) {
            mDecodeRunnable.destroy();
            mDecodeRunnable = null;
        }

        if (mDecodeHandlerThread != null) {
            mDecodeHandlerThread.quit();
            mDecodeHandlerThread = null;
        }

        try {
            if (mDrawnBitmapQueue != null) {
                mDrawnBitmapQueue.resetData();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            Log.e(TAG, "drawn bitmap queue reset data, ex: " + ex);
        }

        try {
            if (mDecodedBitmapQueue != null) {
                mDecodedBitmapQueue.resetData();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            Log.e(TAG, "decoded bitmap queue reset data, ex: " + ex);
        }

        if (mFrameImageList != null) {
            mFrameImageList.clear();
        }
        if (mDecodeOptions != null) {
            mDecodeOptions = null;
        }
    }

    /**
     * get decoded bitmap in the decoded bitmap queue
     * it might block due to new bitmap is not ready
     *
     * @return
     */
    private LinkedBitmap getDecodedBitmap() {
        LinkedBitmap bitmap = null;
        try {
            if (mDecodedBitmapQueue != null) {
                bitmap = mDecodedBitmapQueue.take();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bitmap;
    }

    private class DecodeRunnable implements Runnable {

        private AtomicInteger index = new AtomicInteger();
        private List<FrameImage> frameImageList;
        private BitmapFactory.Options options;

        public DecodeRunnable(int index, List<FrameImage> frameImageList, BitmapFactory.Options options) {
            this.index.set(index);
            this.frameImageList = frameImageList;
            this.options = options;
        }

        public void setIndex(int index) {
            this.index.set(index);
        }

        public void destroy() {
            if (frameImageList != null) {
                frameImageList.clear();
            }
            options = null;
        }

        @Override
        public void run() {
            Log.e(TAG, "decode runnable, status: " + mStatus.get());
            if (mStatus.get() == FrameViewStatus.DESTROY
                    || mStatus.get() == FrameViewStatus.STOP
                    || mStatus.get() == FrameViewStatus.END) {
                return;
            }

            putDecodedBitmapByReuse(frameImageList.get(index.getAndIncrement()), options);
            if (index.get() < frameImageList.size()) {
                if (mDecodeHandler != null) {
                    mDecodeHandler.post(this);
                }
            } else {
                index.set(0);
            }
        }
    }
}
