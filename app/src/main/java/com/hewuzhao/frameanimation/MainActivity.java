package com.hewuzhao.frameanimation;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatSeekBar;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.appcompat.widget.SwitchCompat;

import android.Manifest;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;

import com.hewuzhao.frameanimation.blobcache.BlobCacheManager;
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
    private Button mSmallFrameBt;
    private Button mBtStop;
    private Button mBigFrameBt;
    private Button mCurrentSelectedBt;
    private AppCompatSpinner mScaleType;
    private AppCompatSeekBar mFrameInterval;
    private SwitchCompat mBlobCacheSwitch;
    private TextView mIntervalView;

    private boolean mIsInited = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mFrameView = findViewById(R.id.frame_view);
        mSmallFrameBt = findViewById(R.id.small_frame_texture_view);
        mSmallFrameBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mIsInited) {
                    return;
                }
                mBtStop.setEnabled(true);
                updateCurrentSelectedBt(mSmallFrameBt);

                mFrameView.setVisibility(View.VISIBLE);
                showSmallFrameView();
            }
        });
        mBtStop = findViewById(R.id.bt_stop);
        mBtStop.setEnabled(false);
        mBtStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mFrameView.isPause()) {
                    mFrameView.start();
                    mBtStop.setText("停止");
                } else {
                    pauseFrameViw();
                    mBtStop.setText("开始");
                }
            }
        });
        mBigFrameBt = findViewById(R.id.big_frame_texture_view);
        mBigFrameBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mIsInited) {
                    return;
                }
                mBtStop.setEnabled(true);
                updateCurrentSelectedBt(mBigFrameBt);

                mFrameView.setVisibility(View.VISIBLE);
                showBigFrameView();
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
                    switch (text) {
                        case "CENTER": {
                            scaleType = FrameScaleType.CENTER;
                            break;
                        }
                        case "CENTER_INSIDE": {
                            scaleType = FrameScaleType.CENTER_INSIDE;
                            break;
                        }
                        case "CENTER_CROP": {
                            scaleType = FrameScaleType.CENTER_CROP;
                            break;
                        }
                        case "FIT_END": {
                            scaleType = FrameScaleType.FIT_END;
                            break;
                        }
                        case "FIT_CENTER": {
                            scaleType = FrameScaleType.FIT_CENTER;
                            break;
                        }
                        case "FIT_START": {
                            scaleType = FrameScaleType.FIT_START;
                            break;
                        }
                        default: {
                            scaleType = FrameScaleType.FIT_XY;
                        }
                    }
                    mFrameView.setScaleType(scaleType);
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
                mFrameView.setUseCache(isChecked);
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
        mFrameView.startWithFrameSrc(R.drawable.small_animation_drawable);
    }

    private void showBigFrameView() {
        mFrameView.startWithFrameSrc(R.drawable.big_animation_drawable);
    }

    private void pauseFrameViw() {
        mFrameView.pause();
    }

    private void destroyFrameView() {
        mFrameView.destroy();
    }


    private void init() {
        mIsInited = true;
//        startCheckImageCache();
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
        destroyFrameView();
    }
}
