package com.hewuzhao.frameanimation.blobcache;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.hewuzhao.frameanimation.bytespool.BytesBuffer;
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

    public static byte[] getBytes(String in) {
        byte[] result = new byte[in.length() * 2];
        int output = 0;
        for (char ch : in.toCharArray()) {
            result[output++] = (byte) (ch & 0xFF);
            result[output++] = (byte) (ch >> 8);
        }
        return result;
    }

    public static BytesBuffer getCacheDataByName(BlobCache blobCache, String name, BytesBuffer bytesBuffer, byte[] key, BlobCache.LookupRequest request) {

        try {
            if (bytesBuffer == null) {
                bytesBuffer = new BytesBuffer();
            }
            if (request == null) {
                request = new BlobCache.LookupRequest();
            }
            if (key == null) {
                key = getBytes(name);
            }
            request.key = crc64Long(key);
            request.buffer = bytesBuffer.data;

            if (blobCache.lookup(request)) {
                if (isSameKey(key, request.buffer, request.length)) {
                    bytesBuffer.data = request.buffer;
                    bytesBuffer.offset = key.length + 8;
                    bytesBuffer.length = request.length - bytesBuffer.offset;

                    return bytesBuffer;
                }
            } else {
                Log.e(TAG, "getCacheDataByName, not found, name=" + name);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            Log.e(TAG, "getCacheDataByName error, name: " + name + ", ex: " + ex);
        }

        return null;
    }

    public static Bitmap getCacheBitmapByData(BytesBuffer dataBuffer, ByteBuffer pixelsBuffer, Bitmap inBitmap,
                                              BytesBuffer widthBuffer, BytesBuffer heightBuffer) {
        if (dataBuffer == null || dataBuffer.data == null) {
            return null;
        }
        try {
            if (widthBuffer == null) {
                widthBuffer = new BytesBuffer(4);
            }
            byte[] wb = widthBuffer.data;
            // 读取宽度，在bitmap的后面4位
            System.arraycopy(dataBuffer.data, dataBuffer.length, wb, 0, 4);
            if (heightBuffer == null) {
                heightBuffer = new BytesBuffer(4);
            }
            byte[] hb = heightBuffer.data;
            // 读取高度，在宽度的后面4位
            System.arraycopy(dataBuffer.data, dataBuffer.length + 4, hb, 0, 4);
            int width = ResourceUtil.byte2int(wb);
            int height = ResourceUtil.byte2int(hb);

            widthBuffer.length = 0;
            widthBuffer.offset = 0;

            heightBuffer.length = 0;
            heightBuffer.offset = 0;

            Bitmap bitmap = null;
            if (inBitmap == null) {
                Log.e(TAG, "getCacheBitmapByData, inBitmap is null");
            } else if (inBitmap.isRecycled()) {
                Log.e(TAG, "getCacheBitmapByData, inBitmap is recycled.");
            } else if (inBitmap.getWidth() != width) {
                Log.e(TAG, "getCacheBitmapByData, inBitmap width is not fit.");
            } else if (inBitmap.getHeight() != height) {
                Log.e(TAG, "getCacheBitmapByData, inBitmap height is not fit.");
            } else {
                bitmap = inBitmap;
            }
            if (bitmap == null) {
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                if (bitmap == null) {
                    Log.e(TAG, "getCacheBitmapByData, Bitmap.createBitmap bitmap is null, may be something error.");
                }
            }

            if (bitmap != null) {
                if (pixelsBuffer == null) {
                    pixelsBuffer = ByteBuffer.allocate(dataBuffer.data.length);
                }
                pixelsBuffer.put(dataBuffer.data);
                pixelsBuffer.clear();
                bitmap.copyPixelsFromBuffer(pixelsBuffer);
                pixelsBuffer.clear();
                return bitmap;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            Log.e(TAG, "getCacheBitmapByData, ex=" + ex);
        } finally {
            dataBuffer.length = 0;
            dataBuffer.offset = 0;
        }
        return null;
    }

    /**
     * 该方法拆分为 getCacheDataByName 和 getCacheBitmapByData
     */
    @Deprecated
    public static Bitmap getCacheBitmapByName(BlobCache blobCache, String name, Bitmap inBitmap,
                                              BytesBuffer bytesBuffer, BytesBuffer widthBuffer,
                                              BytesBuffer heightBuffer, byte[] key) {

        if (bytesBuffer == null) {
            bytesBuffer = new BytesBuffer();
        }
        try {

            BlobCache.LookupRequest request = new BlobCache.LookupRequest();
            if (key == null) {
                key = getBytes(name);
            }
            request.key = crc64Long(key);
            request.buffer = bytesBuffer.data;

            if (blobCache.lookup(request)) {
                if (isSameKey(key, request.buffer, request.length)) {
                    bytesBuffer.data = request.buffer;
                    bytesBuffer.offset = key.length + 8;
                    bytesBuffer.length = request.length - bytesBuffer.offset;

                    if (widthBuffer == null) {
                        widthBuffer = new BytesBuffer(4);
                    }
                    byte[] wb = widthBuffer.data;
                    System.arraycopy(bytesBuffer.data, bytesBuffer.length, wb, 0, 4);
                    if (heightBuffer == null) {
                        heightBuffer = new BytesBuffer(4);
                    }
                    byte[] hb = heightBuffer.data;
                    System.arraycopy(bytesBuffer.data, bytesBuffer.length + 4, hb, 0, 4);
                    int width = ResourceUtil.byte2int(wb);
                    int height = ResourceUtil.byte2int(hb);

                    widthBuffer.length = 0;
                    widthBuffer.offset = 0;

                    heightBuffer.length = 0;
                    heightBuffer.offset = 0;

                    Bitmap bitmap = null;
                    if (inBitmap != null && !inBitmap.isRecycled() && inBitmap.getWidth() == width && inBitmap.getHeight() == height) {
                        bitmap = inBitmap;
                    }
                    if (bitmap == null) {
                        Log.e(TAG, "option inBitmap is null or width and height not fit, name: "
                                + name + ", width=" + width + ", height=" + height);

                        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                        if (bitmap == null) {
                            Log.e(TAG, "Bitmap.createBitmap bitmap is null, may be something error, name: " + name);
                        }
                    }

                    if (bitmap != null) {
                        ByteBuffer buf = ByteBuffer.wrap(bytesBuffer.data, 0, bytesBuffer.length);
                        bitmap.copyPixelsFromBuffer(buf);
                        buf.clear();
                        return bitmap;
                    }
                }
            } else {
                Log.e(TAG, "getCacheBitmapByName, not found, name=" + name);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            Log.e(TAG, "decode bitmap from blobcache error, name: " + name + ", ex: " + ex);
        } finally {
            bytesBuffer.length = 0;
            bytesBuffer.offset = 0;
        }

        return null;
    }

    public static void saveImageByBlobCache(Bitmap bitmap, String drawableName, BlobCache blobCache) {
        if (blobCache == null) {
            Log.e(TAG, "saveImageByBlobCache, blob cache is null.");
            return;
        }
        long t1 = System.currentTimeMillis();
        if (bitmap == null) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            bitmap = ResourceUtil.getBitmap(drawableName, options);
            if (bitmap == null) {
                Log.e(TAG, "save image to blob cache, bitmap is null, name: " + drawableName);
                return;
            }
        }

        try {
            final int width = bitmap.getWidth();
            final int height = bitmap.getHeight();

            byte[] key = BlobCacheUtil.getBytes(drawableName);
            ByteBuffer bu = ByteBuffer.allocate(bitmap.getByteCount());
            bitmap.copyPixelsToBuffer(bu);
            byte[] value = bu.array();
            ByteBuffer buffer = ByteBuffer.allocate(key.length + value.length + 8);
            buffer.put(value);
            buffer.put(ResourceUtil.int2byte(width));
            buffer.put(ResourceUtil.int2byte(height));
            buffer.put(key);

            blobCache.insert(BlobCacheUtil.getCacheKey(drawableName), buffer.array());
        } catch (Exception ex) {
            ex.printStackTrace();
            Log.e(TAG, "save imge by blob cache error, name: " + drawableName + ", ex: " + ex);
        } finally {
            blobCache.syncAll();
            Log.e(TAG, "save image to blob cache, cost time: " + (System.currentTimeMillis() - t1) + ", name: " + drawableName);
        }
    }

    public static long getCacheKey(String path) {
        if (path == null || path.isEmpty()) {
            return 0;
        }

        byte[] key = BlobCacheUtil.getBytes(path);
        return BlobCacheUtil.crc64Long(key);
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
