package com.hewuzhao.frameanimation.frameview;

import android.graphics.Matrix;

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
    /**
     * scale using the bitmap matrix when drawing.
     */
    int MATRIX = 0;

    /**
     * @see Matrix.ScaleToFit.FILL
     */
    int FIT_XY = 1;

    /**
     * @see Matrix.ScaleToFit.START
     */
    int FIT_START = 2;

    /**
     * @see Matrix.ScaleToFit.CENTER
     */
    int FIT_CENTER = 3;

    /**
     * @see Matrix.ScaleToFit.END
     */
    int FIT_END = 4;

    /**
     * Center the image in the view, but perform no scaling.
     */
    int CENTER = 5;

    /**
     * Scale the image uniformly (maintain the image's aspect ratio) so that both dimensions (width and height) of the image
     * will be equal to or larger than the corresponding dimension of the view.
     */
    int CENTER_CROP = 6;

    /**
     * Scale the image uniformly (maintain the image's aspect ratio) so that both dimensions (width and height) of the image
     * will be equal to or less than the corresponding dimension of the view.
     */
    int CENTER_INSIDE = 7;
}

