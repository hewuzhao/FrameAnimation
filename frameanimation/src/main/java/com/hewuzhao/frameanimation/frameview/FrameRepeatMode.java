package com.hewuzhao.frameanimation.frameview;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static com.hewuzhao.frameanimation.frameview.FrameRepeatMode.INFINITE;
import static com.hewuzhao.frameanimation.frameview.FrameRepeatMode.ONCE;
import static com.hewuzhao.frameanimation.frameview.FrameRepeatMode.TWICE;

/**
 * @author hewuzhao
 * @date 2020-02-10
 */
@IntDef({ONCE, TWICE, INFINITE})
@Retention(RetentionPolicy.SOURCE)
public @interface FrameRepeatMode {
    /**
     * play once
     */
    int ONCE = 1;

    /**
     * play twice
     */
    int TWICE = 2;

    /**
     * play infinity
     */
    int INFINITE = 3;
}
