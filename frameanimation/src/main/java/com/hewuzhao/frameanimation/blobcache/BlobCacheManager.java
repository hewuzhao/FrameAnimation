package com.hewuzhao.frameanimation.blobcache;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import com.hewuzhao.frameanimation.FrameApplication;

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

    private BlobCacheManager() {
        mBlobCacheMap = new ConcurrentHashMap<>();
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
}
