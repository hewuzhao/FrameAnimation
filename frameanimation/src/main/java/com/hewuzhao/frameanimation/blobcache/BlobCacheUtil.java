package com.hewuzhao.frameanimation.blobcache;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.hewuzhao.frameanimation.bytespool.BytesBufferPool;
import com.hewuzhao.frameanimation.frameview.FrameImage;
import com.hewuzhao.frameanimation.utils.ResourceUtil;

import java.nio.ByteBuffer;

/**
 * @author hewuzhao
 * @date 2020-02-01
 */
public class BlobCacheUtil {
    private static final String TAG = "BlobCacheUtil";

    private static final long POLY64REV = 0x95AC9329AC4BC9B5L;
    private static final long INITIALCRC = 0xFFFFFFFFFFFFFFFFL;

    private static long[] sCrcTable = new long[256];

    static {
        // http://bioinf.cs.ucl.ac.uk/downloads/crc64/crc64.c
        long part;
        for (int i = 0; i < 256; i++) {
            part = i;
            for (int j = 0; j < 8; j++) {
                long x = ((int) part & 1) != 0 ? POLY64REV : 0;
                part = (part >> 1) ^ x;
            }
            sCrcTable[i] = part;
        }
    }

    private static long crc64Long(byte[] buffer) {
        long crc = INITIALCRC;
        for (byte b : buffer) {
            crc = sCrcTable[(((int) crc) ^ b) & 0xff] ^ (crc >> 8);
        }
        return crc;
    }

    private static byte[] getBytes(String in) {
        byte[] result = new byte[in.length() * 2];
        int output = 0;
        for (char ch : in.toCharArray()) {
            result[output++] = (byte) (ch & 0xFF);
            result[output++] = (byte) (ch >> 8);
        }
        return result;
    }

    public static int[] getCacheBitmapWidthAndHeight(String name) {
        BytesBufferPool.BytesBuffer bytesBuffer = BlobCacheManager.getInstance().getBufferPool().get();
        try {
            BlobCache.LookupRequest request = new BlobCache.LookupRequest();
            byte[] key = getBytes(name);
            request.key = crc64Long(key);
            request.buffer = bytesBuffer.data;

            if (BlobCacheManager.getInstance().getBlobCache().lookup(request)) {
                if (isSameKey(key, request.buffer, request.length)) {
                    bytesBuffer.data = request.buffer;
                    bytesBuffer.offset = key.length + 8;
                    bytesBuffer.length = request.length - bytesBuffer.offset;

//                    byte[] wb = new byte[4];
                    BytesBufferPool.BytesBuffer widthBuffer = BlobCacheManager.getInstance().getWidthAndHeightBufferPool().get();
                    byte[] wb = widthBuffer.data;
                    System.arraycopy(bytesBuffer.data, bytesBuffer.length, wb, 0, 4);
//                    byte[] hb = new byte[4];
                    BytesBufferPool.BytesBuffer heightBuffer = BlobCacheManager.getInstance().getWidthAndHeightBufferPool().get();
                    byte[] hb = heightBuffer.data;
                    System.arraycopy(bytesBuffer.data, bytesBuffer.length + 4, hb, 0, 4);
                    int width = ResourceUtil.byte2int(wb);
                    int height = ResourceUtil.byte2int(hb);

                    BlobCacheManager.getInstance().getWidthAndHeightBufferPool().recycle(widthBuffer);
                    BlobCacheManager.getInstance().getWidthAndHeightBufferPool().recycle(heightBuffer);

                    int[] wh = new int[2];
                    wh[0] = width;
                    wh[1] = height;
                    return wh;
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            Log.e(TAG, "getCacheBitmapWidthAndHeight error, ex: " + ex);
        } finally {
            BlobCacheManager.getInstance().getBufferPool().recycle(bytesBuffer);
        }

        return null;
    }

    public static Bitmap getCacheBitmapByName(String name, BitmapFactory.Options options) {

        long t1 = System.currentTimeMillis();
        BytesBufferPool.BytesBuffer bytesBuffer = BlobCacheManager.getInstance().getBufferPool().get();
        try {

            BlobCache.LookupRequest request = new BlobCache.LookupRequest();
            byte[] key = getBytes(name);
            request.key = crc64Long(key);
            request.buffer = bytesBuffer.data;

            if (BlobCacheManager.getInstance().getBlobCache().lookup(request)) {
                if (isSameKey(key, request.buffer, request.length)) {
                    bytesBuffer.data = request.buffer;
                    bytesBuffer.offset = key.length + 8;
                    bytesBuffer.length = request.length - bytesBuffer.offset;

//                    byte[] wb = new byte[4];
                    BytesBufferPool.BytesBuffer widthBuffer = BlobCacheManager.getInstance().getWidthAndHeightBufferPool().get();
                    byte[] wb = widthBuffer.data;
                    System.arraycopy(bytesBuffer.data, bytesBuffer.length, wb, 0, 4);
//                    byte[] hb = new byte[4];
                    BytesBufferPool.BytesBuffer heightBuffer = BlobCacheManager.getInstance().getWidthAndHeightBufferPool().get();
                    byte[] hb = heightBuffer.data;
                    System.arraycopy(bytesBuffer.data, bytesBuffer.length + 4, hb, 0, 4);
                    int width = ResourceUtil.byte2int(wb);
                    int height = ResourceUtil.byte2int(hb);
                    BlobCacheManager.getInstance().getWidthAndHeightBufferPool().recycle(widthBuffer);
                    BlobCacheManager.getInstance().getWidthAndHeightBufferPool().recycle(heightBuffer);

                    Bitmap bitmap = null;
                    if (options != null && options.inBitmap != null
                            && options.inBitmap.getWidth() == width
                            && options.inBitmap.getHeight() == height) {
                        bitmap = options.inBitmap;
                    }
                    if (bitmap == null) {
                        Log.e(TAG, "option inBitmap is null or width and height not fit, name: " + name);
                        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    }

                    if (bitmap != null) {
                        ByteBuffer buf = ByteBuffer.wrap(bytesBuffer.data, 0, bytesBuffer.length);
                        bitmap.copyPixelsFromBuffer(buf);
                        buf.clear();
                        return bitmap;
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            Log.e(TAG, "decode bitmap from blobcache error, name: " + name + ", ex: " + ex);
        } finally {
            BlobCacheManager.getInstance().getBufferPool().recycle(bytesBuffer);
            Log.e(TAG, "decode bitmap from blobcache, cost time: " + (System.currentTimeMillis() - t1) + ", name: " + name);
        }

        return null;
    }

    public static void saveImageByBlobCache(FrameImage info) {
        long t1 = System.currentTimeMillis();
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            Bitmap bitmap = ResourceUtil.getBitmap(info, options);
            if (bitmap == null) {
                Log.e(TAG, "save image to blob cache, bitmap is null, name: " + info.getName());
                return;
            }
            final int width = options.outWidth;
            final int height = options.outHeight;

            byte[] key = BlobCacheUtil.getBytes(info.getName());
            ByteBuffer bu = ByteBuffer.allocate(bitmap.getByteCount());
            bitmap.copyPixelsToBuffer(bu);
            byte[] value = bu.array();
            ByteBuffer buffer = ByteBuffer.allocate(key.length + value.length + 8);
            buffer.put(value);
            buffer.put(ResourceUtil.int2byte(width));
            buffer.put(ResourceUtil.int2byte(height));
            buffer.put(key);

            BlobCacheManager.getInstance().getBlobCache().insert(BlobCacheUtil.getCacheKey(info.getName()), buffer.array());
        } catch (Exception ex) {
            ex.printStackTrace();
            Log.e(TAG, "save imge by blob cache error, name: " + info.getName() + ", ex: " + ex);
        } finally {
            BlobCacheManager.getInstance().getBlobCache().syncAll();
            Log.e(TAG, "save image to blob cache, cost time: " + (System.currentTimeMillis() - t1) + ", name: " + info.getName());
        }
    }

    public static long getCacheKey(String path) {
        if (path == null || path.isEmpty()) {
            return 0;
        }

        byte[] key = BlobCacheUtil.getBytes(path);
        return BlobCacheUtil.crc64Long(key);
    }

    public static boolean checkCacheByName(String name) {
        if (name == null || name.isEmpty()) {
            Log.e(TAG, "check cache by name, name is null.");
            return false;
        }
        try {
            byte[] key = BlobCacheUtil.getBytes(name);
            long cacheKey = BlobCacheUtil.crc64Long(key);
            byte[] data = BlobCacheManager.getInstance().getBlobCache().lookup(cacheKey);
            if (data == null) {
                Log.e(TAG, "check cache by name,  name: " + name + ", data is null.");
                return false;
            }
            return true;
        } catch (Exception ex) {
            ex.printStackTrace();
            Log.e(TAG, "check cache by name error, name: " + name + ", ex: " + ex);
        }
        return false;
    }

    private static boolean isSameKey(byte[] key, byte[] buffer, int bufferLen) {
        if (buffer == null || key == null) {
            return false;
        }
        int n = key.length;
        if (buffer.length < n) {
            return false;
        }
        for (int i = n - 1, j = bufferLen - 1; i >= 0; i--, j--) {
            if (key[i] != buffer[j]) {
                return false;
            }
        }
        return true;
    }

}
