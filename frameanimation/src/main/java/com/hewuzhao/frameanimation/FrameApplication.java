package com.hewuzhao.frameanimation;

import android.app.Application;

/**
 * Created by hewuzhao
 * on 2020-02-01
 */
public class FrameApplication extends Application {

    public static Application sApplication;

    @Override
    public void onCreate() {
        super.onCreate();
        sApplication = this;
    }
}
