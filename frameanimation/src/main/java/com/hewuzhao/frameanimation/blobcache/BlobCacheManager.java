package com.hewuzhao.frameanimation.blobcache;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.hewuzhao.frameanimation.FrameApplication;
import com.hewuzhao.frameanimation.bytespool.BytesBufferPool;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author hewuzhao
 * @date 2020-02-01
 */
public class BlobCacheManager {
    private static final String TAG = "BlobCacheManager";

    private static final String NAME_BLOBCACHE_SHAREDPREFERENCES = "BLOBCACHE_SHAREDPREFERENCES";
    private static final String KEY_CACHE_UP_TO_DATE = "cache-up-to-date";

    private Map<String, BlobCache> mBlobCacheMap;
    private BlobCache mBlobCache;
    private boolean mOldCheckDone = false;

    private AtomicBoolean mImageBlobCacheInited;

    private BytesBufferPool mDataBufferPool;
    private BytesBufferPool mWidthAndHeightBufferPool;

    private BlobCacheManager() {
        mImageBlobCacheInited = new AtomicBoolean(false);
        mBlobCacheMap = new ConcurrentHashMap<>();
        mDataBufferPool = new BytesBufferPool(4, 5 * 1024 * 1024);
        mWidthAndHeightBufferPool = new BytesBufferPool(4, 4);
        mBlobCache = getCache(FrameApplication.sApplication,
                BlobCacheParams.IMAGE_CACHE_FILE_BLOBCHCHE,
                BlobCacheParams.IMAGE_CACHE_MAX_ENTRIES,
                BlobCacheParams.IMAGE_CACHE_MAX_BYTES,
                BlobCacheParams.IMAGE_CACHE_VERSION);
    }

    private static class SingletonHolder {
        private static final BlobCacheManager INSTANCE = new BlobCacheManager();
    }

    public static BlobCacheManager getInstance() {
        return SingletonHolder.INSTANCE;
    }

    /**
     * Return null when we cannot instantiate a BlobCache, e.g.:
     * there is no SD card found.
     * This can only be called from data thread.
     */
    private BlobCache getCache(Context context, String filename,
                               int maxEntries, int maxBytes, int version) {
        if (context == null) {
            return null;
        }
        if (!mOldCheckDone) {
            removeOldFilesIfNecessary(context);
            mOldCheckDone = true;
        }
        BlobCache cache = mBlobCacheMap.get(filename);
        if (cache == null) {
            File cacheDir = context.getExternalCacheDir();
            if (cacheDir == null) {
                return null;
            }
            String path = cacheDir.getAbsolutePath() + "/" + filename;
            try {
                cache = new BlobCache(path, maxEntries, maxBytes, false, version);
                mBlobCacheMap.put(filename, cache);
            } catch (Exception e) {
                Log.e(TAG, "BlobCacheManager, Cannot instantiate cache, ex: " + e);
            }
        }
        return cache;
    }

    public void closeBlobCache(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return;
        }
        BlobCache cache = mBlobCacheMap.get(fileName);
        if (cache != null) {
            cache.close();
            mBlobCacheMap.remove(fileName);
        }
    }

    /**
     * Removes the old files if the data is wiped.
     */
    private void removeOldFilesIfNecessary(Context context) {
        if (context == null) {
            return;
        }
        SharedPreferences pref = context.getSharedPreferences(NAME_BLOBCACHE_SHAREDPREFERENCES, Context.MODE_PRIVATE);
        int n = 0;
        try {
            n = pref.getInt(KEY_CACHE_UP_TO_DATE, 0);
        } catch (Exception ex) {
            Log.e(TAG, "removeOldFilesIfNecessary, ex: " + ex);
        }
        if (n != 0) {
            return;
        }
        pref.edit().putInt(KEY_CACHE_UP_TO_DATE, 1).apply();

        File cacheDir = context.getExternalCacheDir();
        if (cacheDir == null) {
            return;
        }
        String prefix = cacheDir.getAbsolutePath() + "/";

        BlobCache.deleteFiles(prefix + "imgcache");
        BlobCache.deleteFiles(prefix + "rev_geocoding");
        BlobCache.deleteFiles(prefix + "bookmark");
    }

    public BlobCache getBlobCache() {
        return mBlobCache;
    }

    public void setImageBlobCacheInited() {
        mImageBlobCacheInited.set(true);
    }

    public boolean isImageBlobCacheInited() {
        return mImageBlobCacheInited.get();
    }

    public BytesBufferPool getBufferPool() {
        return mDataBufferPool;
    }

    public BytesBufferPool getWidthAndHeightBufferPool() {
        return mWidthAndHeightBufferPool;
    }

    public static void startCheckBlobCache() {
        Intent intent = new Intent(FrameApplication.sApplication, BlobCacheService.class);
        intent.putExtra(BlobCacheParams.FLAG_IMAGE_CACHE_INIT, BlobCacheParams.FLAG_IMAGE_CACHE_INIT_VALUE);
        FrameApplication.sApplication.startService(intent);
    }
}
