package com.hewuzhao.frameanimation.blobcache;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import com.hewuzhao.frameanimation.FrameApplication;
import com.hewuzhao.frameanimation.bytespool.BytesBufferPool;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author hewuzhao
 * @date 2020-02-01
 */
public class BlobCacheManager {
    private static final String TAG = "BlobCacheManager";

    private static final String NAME_BLOBCACHE_SP = "BLOBCACHE_SP";

    private static final String NAME_BLOBCACHE_SHAREDPREFERENCES = "BLOBCACHE_SHAREDPREFERENCES";
    private static final String KEY_CACHE_UP_TO_DATE = "cache-up-to-date";

    private Map<String, BlobCache> mBlobCacheMap;
    private boolean mOldCheckDoneMap = false;

    private BytesBufferPool mDataBufferPool;
    private BytesBufferPool mWidthAndHeightBufferPool;

    private BlobCacheManager() {
        mBlobCacheMap = new ConcurrentHashMap<>();

        mDataBufferPool = new BytesBufferPool(4, 5 * 1024 * 1024);
        mWidthAndHeightBufferPool = new BytesBufferPool(4, 4);
    }

    private static class SingletonHolder {
        private static final BlobCacheManager INSTANCE = new BlobCacheManager();
    }

    public static BlobCacheManager getInstance() {
        return SingletonHolder.INSTANCE;
    }

    public BlobCache getBlobCache(String fileName) {
        if (TextUtils.isEmpty(fileName)) {
            return null;
        }
        SharedPreferences sp = FrameApplication.sApplication.getSharedPreferences(NAME_BLOBCACHE_SP,
                Context.MODE_PRIVATE);
        int version = sp.getInt(fileName, 1);
        return getBlobCache(fileName,
                BlobCacheParams.DEFAULT_BLOB_CACHE_MAX_ENTRIES,
                BlobCacheParams.DEFAULT_BLOB_CACHE_MAX_BYTES,
                version);
    }

    /**
     * Return null when we cannot instantiate a BlobCache, e.g.:
     * there is no SD card found.
     * This can only be called from data thread.
     */
    public BlobCache getBlobCache(String filename,
                               int maxEntries, int maxBytes, int version) {
        if (TextUtils.isEmpty(filename)) {
            return null;
        }
        Context context = FrameApplication.sApplication;
        if (!mOldCheckDoneMap) {
            // 安装APP之前的久数据，需要删除掉
            removeOldFilesIfNecessary(context);
            mOldCheckDoneMap = true;
        }
        BlobCache cache = mBlobCacheMap.get(filename);
        if (cache == null) {
            File cacheDir = context.getExternalCacheDir();
            if (cacheDir == null) {
                return null;
            }
            String path = cacheDir.getAbsolutePath() + "/frameanimation";
            createPath(path);
            path = path + "/" + filename;
            try {
                cache = new BlobCache(path, maxEntries, maxBytes, false, version);
                mBlobCacheMap.put(filename, cache);
            } catch (Exception e) {
                Log.e(TAG, "BlobCacheManager, Cannot instantiate cache, ex: " + e);
            }
        }
        return cache;
    }

    private void createPath(String path) {
        File file = new File(path);
        if (!file.exists()) {
            try {
                file.mkdirs();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
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

        try {
            String path = cacheDir.getAbsolutePath() + "/frameanimation";
            File file = new File(path);

            // 删除frameanimation下所有cache文件
            deleteFile(file);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void deleteFile(File file) {
        if (file == null) {
            return;
        }
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    deleteFile(f);
                }
            }
        } else if (file.exists()) {
            file.delete();
        }
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
