package com.hewuzhao.frameanimation.blobcache;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.Nullable;

import com.hewuzhao.frameanimation.frameview.FrameImage;
import com.hewuzhao.frameanimation.frameview.FrameImageParser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by hewuzhao
 * on 2020-02-01
 */
public class BlobCacheService extends IntentService {
    private static final String TAG = "BlobCacheService";

    private static final String NAME_FRAME_LIST_FOLDER = "frame_list";

    public BlobCacheService() {
        super("BlobCacheService");
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent == null) {
            return;
        }

        int flag = intent.getIntExtra(BlobCacheParams.FLAG_IMAGE_CACHE_INIT, 0);
        if (flag != BlobCacheParams.FLAG_IMAGE_CACHE_INIT_VALUE) {
            return;
        }

        Log.e(TAG, "service start.");

        long t1 = System.currentTimeMillis();
        List<FrameImage> frameImageList = new ArrayList<>();

        try {
            String[] files = getBaseContext().getAssets().list(NAME_FRAME_LIST_FOLDER);
            if (files != null && files.length > 0) {
                for (String file : files) {
                    List<FrameImage> tmp = new FrameImageParser().parse(NAME_FRAME_LIST_FOLDER + File.separator + file);
                    if (tmp != null && tmp.size() > 0) {
                        frameImageList.addAll(tmp);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "BlobCacheService, ex: " + e);
        }

        Log.e(TAG, "parse time: " + (System.currentTimeMillis() - t1));

        if (frameImageList.size() <= 0) {
            BlobCacheManager.getInstance().setImageBlobCacheInited();
            return;
        }

        for (FrameImage info : frameImageList) {
            if (!BlobCacheUtil.checkCacheByName(info.getName())) {
                BlobCacheUtil.saveImageByBlobCache(info);
            }
        }
        BlobCacheManager.getInstance().setImageBlobCacheInited();

        Log.e(TAG, "service end, total time: " + (System.currentTimeMillis() - t1));
    }
}
