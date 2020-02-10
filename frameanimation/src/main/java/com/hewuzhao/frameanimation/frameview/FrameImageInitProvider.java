package com.hewuzhao.frameanimation.frameview;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hewuzhao.frameanimation.blobcache.BlobCacheParams;
import com.hewuzhao.frameanimation.blobcache.BlobCacheService;

/**
 * @author hewuzhao
 * @date 2020-02-01
 */
public class FrameImageInitProvider extends ContentProvider {
    private static final String TAG = "FrameImageInitProvider";
    @Override
    public boolean onCreate() {
        Log.e(TAG, "Frame Image Init Provider onCreate start.");
        Context context = getContext();
        if (context == null) {
            Log.e(TAG, "Frame Image Init Provider onCreate start failed, context is null.");
            return false;
        }
        Intent intent = new Intent(getContext(), BlobCacheService.class);
        intent.putExtra(BlobCacheParams.FLAG_IMAGE_CACHE_INIT, BlobCacheParams.FLAG_IMAGE_CACHE_INIT_VALUE);
        context.startService(intent);
        return false;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }
}
