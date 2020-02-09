package com.hewuzhao.frameanimation;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import com.hewuzhao.frameanimation.frameview.FrameImage;
import com.hewuzhao.frameanimation.frameview.FrameImageParser;
import com.hewuzhao.frameanimation.frameview.FrameTextureView;

import java.util.List;

import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity implements EasyPermissions.PermissionCallbacks {
    private static final String TAG = "MainActivity";

    private FrameTextureView mFrameView;
    private ImageView mAnimationImageView;
    private Button mSmallFrameBt;
    private Button mSmallAnimationBt;
    private Button mBigFrameBt;
    private Button mBigAnimationBt;
    private boolean mIsInited = false;

    private Button mCurrentSelectedBt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mFrameView = findViewById(R.id.frame_view);
        mAnimationImageView = findViewById(R.id.animation_image_view);
        mSmallFrameBt = findViewById(R.id.small_frame_texture_view);
        mSmallFrameBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mIsInited) {
                    return;
                }
                updateCurrentSelectedBt(mSmallFrameBt);

                stopAnimationView();
                mAnimationImageView.setVisibility(View.GONE);
                mFrameView.setVisibility(View.VISIBLE);
                showSmallFrameView();
            }
        });
        mSmallAnimationBt = findViewById(R.id.small_animation_drawable);
        mSmallAnimationBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mIsInited) {
                    return;
                }
                updateCurrentSelectedBt(mSmallAnimationBt);

                stopFrameViw();
                mAnimationImageView.setVisibility(View.VISIBLE);
                mFrameView.setVisibility(View.GONE);
                showSmallAnimationView();
            }
        });
        mBigFrameBt = findViewById(R.id.big_frame_texture_view);
        mBigFrameBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mIsInited) {
                    return;
                }
                updateCurrentSelectedBt(mBigFrameBt);

                stopAnimationView();
                mAnimationImageView.setVisibility(View.GONE);
                mFrameView.setVisibility(View.VISIBLE);
                showBigFrameView();
            }
        });
        mBigAnimationBt = findViewById(R.id.big_animation_drawable);
        mBigAnimationBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mIsInited) {
                    return;
                }
                updateCurrentSelectedBt(mBigAnimationBt);

                stopFrameViw();
                mAnimationImageView.setVisibility(View.VISIBLE);
                mFrameView.setVisibility(View.GONE);
                showBigAnimationView();
            }
        });

        checkPermissions();
    }

    private void updateCurrentSelectedBt(Button button) {
        if (mCurrentSelectedBt != null) {
            mCurrentSelectedBt.setEnabled(true);
        }
        mCurrentSelectedBt = button;
        mCurrentSelectedBt.setEnabled(false);
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

    private void showSmallFrameView() {
        stopFrameViw();
        List<FrameImage> frameImageList = null;
        try {
            frameImageList = new FrameImageParser().parse("frame_list/small_image_list.xml");
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
    }

    private void showBigFrameView() {
        stopFrameViw();
        List<FrameImage> frameImageList = null;
        try {
            frameImageList = new FrameImageParser().parse("frame_list/big_image_list.xml");
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
    }

    private void stopFrameViw() {
        mFrameView.stop();
    }

    private void stopAnimationView() {
        Drawable drawable = mAnimationImageView.getBackground();
        if (drawable instanceof AnimationDrawable) {
            AnimationDrawable animation = (AnimationDrawable) drawable;
            animation.stop();
        }
    }

    private void showSmallAnimationView() {
        stopAnimationView();
        mAnimationImageView.setBackgroundResource(R.drawable.small_animation_drawable);
        AnimationDrawable animation = (AnimationDrawable) mAnimationImageView.getBackground();
        animation.start();
    }

    private void showBigAnimationView() {
        stopAnimationView();
        mAnimationImageView.setBackgroundResource(R.drawable.big_animation_drawable);
        AnimationDrawable animation = (AnimationDrawable) mAnimationImageView.getBackground();
        animation.start();
    }


    private void init() {
        mIsInited = true;
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
