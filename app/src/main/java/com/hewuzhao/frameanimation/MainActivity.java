package com.hewuzhao.frameanimation;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatSeekBar;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.appcompat.widget.SwitchCompat;

import android.Manifest;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.hewuzhao.frameanimation.blobcache.BlobCacheManager;
import com.hewuzhao.frameanimation.frameview.FrameRepeatMode;
import com.hewuzhao.frameanimation.frameview.FrameScaleType;
import com.hewuzhao.frameanimation.frameview.FrameTextureView;

import java.util.Arrays;
import java.util.List;

import pub.devrel.easypermissions.EasyPermissions;

/**
 * @author hewuzhao
 * @date 2020-02-10
 */
public class MainActivity extends AppCompatActivity implements EasyPermissions.PermissionCallbacks {
    private static final String TAG = "MainActivity";

    private final List<String> REPEAT_MODE = Arrays.asList(
            "INFINITE",
            "ONCE",
            "TWICE"
    );

    private final List<String> SCALE_TYPE = Arrays.asList(
            "CENTER",
            "CENTER_INSIDE",
            "CENTER_CROP",
            "FIT_END",
            "FIT_CENTER",
            "FIT_START",
            "FIT_XY"
    );

    private FrameTextureView mFrameView;
    private ImageView mAnimationImageView;
    private Button mSmallFrameBt;
    private Button mSmallAnimationBt;
    private Button mBigFrameBt;
    private Button mBigAnimationBt;
    private Button mCurrentSelectedBt;
    private AppCompatSpinner mScaleType;
    private AppCompatSpinner mRepeatMode;
    private AppCompatSeekBar mFrameInterval;
    private SwitchCompat mBlobCacheSwitch;
    private TextView mIntervalView;

    private boolean mIsInited = false;

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

        mIntervalView = findViewById(R.id.interval_view);

        mScaleType = findViewById(R.id.scale_type);
        mScaleType.setAdapter(new ArrayAdapter(this, R.layout.spinner_item_view, SCALE_TYPE));
        mScaleType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (parent == null || view == null) {
                    return;
                }
                if (view instanceof TextView) {
                    String text = ((TextView) view).getText().toString();
                    int scaleType;
                    ImageView.ScaleType imageViewScaleType;
                    switch (text) {
                        case "CENTER": {
                            scaleType = FrameScaleType.CENTER;
                            imageViewScaleType = ImageView.ScaleType.CENTER;
                            break;
                        }
                        case "CENTER_INSIDE": {
                            scaleType = FrameScaleType.CENTER_INSIDE;
                            imageViewScaleType = ImageView.ScaleType.CENTER_INSIDE;
                            break;
                        }
                        case "CENTER_CROP": {
                            scaleType = FrameScaleType.CENTER_CROP;
                            imageViewScaleType = ImageView.ScaleType.CENTER_CROP;
                            break;
                        }
                        case "FIT_END": {
                            scaleType = FrameScaleType.FIT_END;
                            imageViewScaleType = ImageView.ScaleType.FIT_END;
                            break;
                        }
                        case "FIT_CENTER": {
                            scaleType = FrameScaleType.FIT_CENTER;
                            imageViewScaleType = ImageView.ScaleType.FIT_CENTER;
                            break;
                        }
                        case "FIT_START": {
                            scaleType = FrameScaleType.FIT_START;
                            imageViewScaleType = ImageView.ScaleType.FIT_START;
                            break;
                        }
                        default: {
                            scaleType = FrameScaleType.FIT_XY;
                            imageViewScaleType = ImageView.ScaleType.FIT_XY;
                        }
                    }
                    mFrameView.setScaleType(scaleType);
                    mAnimationImageView.setScaleType(imageViewScaleType);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        mRepeatMode = findViewById(R.id.repeat_mode);
        mRepeatMode.setAdapter(new ArrayAdapter(this, R.layout.spinner_item_view, REPEAT_MODE));
        mRepeatMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (parent == null || view == null) {
                    return;
                }
                if (view instanceof TextView) {
                    String text = ((TextView) view).getText().toString();
                    int repeatMode;
                    switch (text) {
                        case "ONCE": {
                            repeatMode = FrameRepeatMode.ONCE;
                            break;
                        }
                        case "TWICE": {
                            repeatMode = FrameRepeatMode.TWICE;
                            break;
                        }
                        case "INFINITE":
                        default: {
                            repeatMode = FrameRepeatMode.INFINITE;
                        }
                    }
                    mFrameView.setRepeatMode(repeatMode);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        mFrameInterval = findViewById(R.id.frame_interval);
        mFrameInterval.setMax(300);
        mFrameInterval.setProgress(80);
        mIntervalView.setText("80ms");
        mFrameInterval.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress <= 0) {
                    progress = 1;
                }

                mIntervalView.setText(progress + "ms");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int progress = seekBar.getProgress();
                if (progress <= 0) {
                    progress = 1;
                }
                mFrameView.setFrameInterval(progress);
            }
        });

        mBlobCacheSwitch = findViewById(R.id.blob_cache_switch);
        mBlobCacheSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                BlobCacheManager.getInstance().setIsUseBlobCache(isChecked);
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
        if (mFrameView.setFrameImageListPath("small_image_list.xml")) {
            mFrameView.start();
        }
    }

    private void showBigFrameView() {
        stopFrameViw();
        if (mFrameView.setFrameImageListPath("big_image_list.xml")) {
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
        mAnimationImageView.setImageResource(R.drawable.small_animation_drawable);
        AnimationDrawable animation = (AnimationDrawable) mAnimationImageView.getDrawable();
        animation.start();
    }

    private void showBigAnimationView() {
        stopAnimationView();
        mAnimationImageView.setImageResource(R.drawable.big_animation_drawable);
        AnimationDrawable animation = (AnimationDrawable) mAnimationImageView.getDrawable();
        animation.start();
    }


    private void init() {
        mIsInited = true;
        startCheckImageCache();
    }

    private void startCheckImageCache() {
        BlobCacheManager.startCheckBlobCache();
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
