package com.hewuzhao.frameanimation.frameview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.AttributeSet;
import android.util.Log;

import com.hewuzhao.frameanimation.blobcache.BlobCacheManager;
import com.hewuzhao.frameanimation.blobcache.BlobCacheUtil;
import com.hewuzhao.frameanimation.utils.ResourceUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by hewuzhao
 * on 2020-02-01
 */
public class FrameTextureView extends BaseTextureView {
    private static final String TAG = "FrameTextureView";

    public static final int INVALID_INDEX = Integer.MAX_VALUE;
    private int mBufferSize = 3;
    public static final String DECODE_THREAD_NAME = "DECODE_HANDLER_THREAD";
    public static final int INFINITE = -1;

    private int mRepeatTimes;
    private int mRepeatedCount;

    /**
     * 是否彻底被销毁
     */
    private final AtomicBoolean mIsDestroy = new AtomicBoolean(false);

    /**
     * the resources of frame animation
     */
    private List<FrameImage> mFrameImageList = new ArrayList<>();

    /**
     * the index of bitmap resource which is decoding
     */
    private int mBitmapIdIndex;
    /**
     * the index of frame which is drawing
     */
    private int mFrameIndex = INVALID_INDEX;
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
    private Paint mDrawPaint = new Paint();
    private Rect mSrcRect;
    private Rect mDstRect = new Rect();
    private int mDefaultWidth;
    private int mDefaultHeight;

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

    public void setRepeatTimes(int repeatTimes) {
        mRepeatTimes = repeatTimes;
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
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        mDstRect.set(0, 0, getWidth(), getHeight());
    }

    @Override
    protected int getDefaultWidth() {
        return mDefaultWidth;
    }

    @Override
    protected int getDefaultHeight() {
        return mDefaultHeight;
    }

    @Override
    protected void onFrameDrawFinish() {
    }

    /**
     * set the duration of frame animation
     *
     * @param duration time in milliseconds
     */
    public void setDuration(int duration) {
        setFrameDuration(duration);
    }

    /**
     * set the materials of frame animation which is an array of resource
     */
    public void setFrameImageList(List<FrameImage> list) {
        if (list == null || list.size() == 0) {
            return;
        }
        mFrameImageList = new ArrayList<>(list);
        //by default, take the first bitmap's dimension into consideration
        getBitmapDimension();
        preloadFrames();
        mDecodeRunnable = new DecodeRunnable(mBitmapIdIndex, mFrameImageList, mDecodeOptions);
    }

    private void getBitmapDimension() {
        int[] wh = null;
        if (BlobCacheManager.getInstance().isImageBlobCacheInited()
                && mFrameImageList != null
                && mBitmapIdIndex >= 0
                && mBitmapIdIndex < mFrameImageList.size()) {
            wh = BlobCacheUtil.getCacheBitmapWidthAndHeight(mFrameImageList.get(mBitmapIdIndex).getName());
        }

        if (wh == null) {
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            ResourceUtil.getBitmap(mFrameImageList.get(mBitmapIdIndex), options);
            mDefaultWidth = options.outWidth;
            mDefaultHeight = options.outHeight;
        } else {
            mDefaultWidth = wh[0];
            mDefaultHeight = wh[1];
        }

        mSrcRect = new Rect(0, 0, mDefaultWidth, mDefaultHeight);
        //we have to re-measure to make mDefaultWidth in use in onMeasure()
        requestLayout();
    }

    /**
     * load the first several frames of animation before it is started
     */
    private void preloadFrames() {
        int index = mBitmapIdIndex++;
        putDecodedBitmap(mFrameImageList.get(index), mDecodeOptions, new LinkedBitmap());
        index = mBitmapIdIndex++;
        putDecodedBitmap(mFrameImageList.get(index), mDecodeOptions, new LinkedBitmap());
    }

    /**
     * recycle the bitmap used by frame animation.
     * Usually it should be invoked when the ui of frame animation is no longer visible
     */
    @Override
    public void destroy() {
        Log.i(TAG, "destroy FrameTextureView.");
        mIsDestroy.set(true);
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
    protected void onFrameDraw(Canvas canvas) {
        clearCanvas(canvas);
        if (!isStarted()) {
            return;
        }
        if (isFinish()) {
            onFrameAnimationEnd();
            if (mRepeatTimes == INFINITE) {
                repeatDrawOneFrame(canvas);
            } else if (mRepeatedCount < mRepeatTimes) {
                repeatDrawOneFrame(canvas);
                mRepeatedCount++;
            } else {
                mRepeatedCount = 0;
            }
        } else {
            drawOneFrame(canvas);
        }
    }

    private void repeatDrawOneFrame(Canvas canvas) {
        if (mIsDestroy.get()) {
            return;
        }
        mFrameIndex = 0;
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
            mDecodeRunnable = new DecodeRunnable(mBitmapIdIndex, mFrameImageList, mDecodeOptions);
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
        LinkedBitmap linkedBitmap = getDecodedBitmap();
        if (linkedBitmap != null) {
            canvas.drawBitmap(linkedBitmap.bitmap, mSrcRect, mDstRect, mDrawPaint);
            putDrawnBitmap(linkedBitmap);
        }
        mFrameIndex++;
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
        mFrameIndex = INVALID_INDEX;
    }

    /**
     * whether frame animation is finished
     *
     * @return true: animation is finished, false: animation is doing
     */
    private boolean isFinish() {
        return mFrameIndex >= mFrameImageList.size() - 1;
    }

    /**
     * whether frame animation is started
     *
     * @return true: animation is started, false: animation is not started
     */
    private boolean isStarted() {
        return mFrameIndex != INVALID_INDEX;
    }

    /**
     * start frame animation from the first frame
     */
    public void start() {
        boolean isStarted = isStarted();
        Log.i(TAG, "start frame textureview, is started: " + isStarted);
        if (isStarted) {
            return;
        }
        if (mIsDestroy.get()) {
            return;
        }
        mFrameIndex = 0;
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
            mDecodeRunnable = new DecodeRunnable(mBitmapIdIndex, mFrameImageList, mDecodeOptions);
        }

        mDecodeRunnable.setIndex(0);
        mDecodeHandler.post(mDecodeRunnable);
    }


    /**
     * clear out the drawing on canvas,preparing for the next frame
     * * @param canvas
     */
    private void clearCanvas(Canvas canvas) {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
    }

    /**
     * decode bitmap by BitmapFactory.decodeStream(), it is about twice faster than BitmapFactory.decodeResource()
     *
     * @param frameImage   the bitmap resource
     * @param options
     * @return
     */
    private Bitmap decodeBitmap(FrameImage frameImage, BitmapFactory.Options options) {
        if (mIsDestroy.get()) {
            return null;
        }
        options.inScaled = false;
        Bitmap bitmap = null;
        if (BlobCacheManager.getInstance().isImageBlobCacheInited()) {
            bitmap = BlobCacheUtil.getCacheBitmapByName(frameImage.getName(), options);
            if (bitmap != null) {
                return bitmap;
            }
        }

        long t1 = System.currentTimeMillis();
        try {
            bitmap = ResourceUtil.getBitmap(frameImage, options);
            Log.e(TAG, "decode bitmap from stream, cost time: " + (System.currentTimeMillis() - t1));
        } catch (Exception ex) {
            ex.printStackTrace();
            Log.e(TAG, "decode, ex: " + ex);
        }
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
        if (mIsDestroy.get()) {
            return;
        }
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
        if (mIsDestroy.get()) {
            return null;
        }
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

        private int index;
        private List<FrameImage> frameImageList;
        private BitmapFactory.Options options;

        public DecodeRunnable(int index, List<FrameImage> frameImageList, BitmapFactory.Options options) {
            this.index = index;
            this.frameImageList = frameImageList;
            this.options = options;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public void destroy() {
            if (frameImageList != null) {
                frameImageList.clear();
            }
            options = null;
        }

        @Override
        public void run() {
            if (mIsDestroy.get()) {
                return;
            }
            putDecodedBitmapByReuse(frameImageList.get(index), options);
            index++;
            if (index < frameImageList.size()) {
                if (mDecodeHandler != null) {
                    mDecodeHandler.post(this);
                }
            } else {
                index = 0;
            }
        }
    }
}
