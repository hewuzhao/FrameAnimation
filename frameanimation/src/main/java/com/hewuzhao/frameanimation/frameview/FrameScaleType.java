package com.hewuzhao.frameanimation.frameview;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static com.hewuzhao.frameanimation.frameview.FrameScaleType.CENTER;
import static com.hewuzhao.frameanimation.frameview.FrameScaleType.CENTER_CROP;
import static com.hewuzhao.frameanimation.frameview.FrameScaleType.CENTER_INSIDE;
import static com.hewuzhao.frameanimation.frameview.FrameScaleType.FIT_CENTER;
import static com.hewuzhao.frameanimation.frameview.FrameScaleType.FIT_END;
import static com.hewuzhao.frameanimation.frameview.FrameScaleType.FIT_START;
import static com.hewuzhao.frameanimation.frameview.FrameScaleType.FIT_XY;
import static com.hewuzhao.frameanimation.frameview.FrameScaleType.MATRIX;

/**
 * @author hewuzhao
 * @date 2020-02-10
 */
@IntDef({MATRIX, FIT_XY, FIT_START, FIT_CENTER, FIT_END, CENTER, CENTER_CROP, CENTER_INSIDE})
@Retention(RetentionPolicy.SOURCE)
public @interface FrameScaleType {

    int MATRIX = 0;

    int FIT_XY = 1;

    int FIT_START = 2;

    int FIT_CENTER = 3;

    int FIT_END = 4;

    int CENTER = 5;

    int CENTER_CROP = 6;

    int CENTER_INSIDE = 7;
}
