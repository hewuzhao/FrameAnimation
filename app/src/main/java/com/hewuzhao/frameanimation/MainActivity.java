package com.hewuzhao.frameanimation;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.hewuzhao.frameanimation.frameview.FrameImage;
import com.hewuzhao.frameanimation.frameview.FrameImageParser;
import com.hewuzhao.frameanimation.frameview.FrameTextureView;

import java.util.List;

import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity implements EasyPermissions.PermissionCallbacks {
    private static final String TAG = "MainActivity";

    private FrameTextureView mFrameView, mFrameView2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mFrameView = findViewById(R.id.frame_view);
        mFrameView2 = findViewById(R.id.frame_view_2);
        mFrameView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mFrameView.destroy();
                mFrameView.setVisibility(View.GONE);
            }
        });
        mFrameView2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mFrameView2.destroy();
                mFrameView2.setVisibility(View.GONE);
            }
        });
        checkPermissions();
    }

    private void checkPermissions() {
        if (EasyPermissions.hasPermissions(this, Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            init();
        } else {
            EasyPermissions.requestPermissions(this, "test,test", 100,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
    }

    private void init() {
        List<FrameImage> frameImageList = null;

        try {
            frameImageList = new FrameImageParser().parse("frame_list/one_list.xml");
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "BlobCacheIntentService, ex: " + e);
        }
        if (frameImageList != null && frameImageList.size() > 0) {
            mFrameView.setFrameImageList(frameImageList);
            mFrameView.setDuration(60);
            mFrameView.setRepeatTimes(FrameTextureView.INFINITE);

            mFrameView.start();

        }

        List<FrameImage> frameImageList2 = null;

        try {
            frameImageList2 = new FrameImageParser().parse("frame_list/second_list.xml");
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "BlobCacheIntentService, ex: " + e);
        }
        if (frameImageList2 != null && frameImageList2.size() > 0) {
            mFrameView2.setFrameImageList(frameImageList2);
            mFrameView2.setDuration(50);
            mFrameView2.setRepeatTimes(FrameTextureView.INFINITE);

            mFrameView2.start();

        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Forward results to EasyPermissions
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    @Override
    public void onPermissionsGranted(int requestCode, @NonNull List<String> perms) {
        if (requestCode == 100) {
            init();
        }
    }

    @Override
    public void onPermissionsDenied(int requestCode, @NonNull List<String> perms) {

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mFrameView != null) {
            mFrameView.destroy();
        }
    }
}
