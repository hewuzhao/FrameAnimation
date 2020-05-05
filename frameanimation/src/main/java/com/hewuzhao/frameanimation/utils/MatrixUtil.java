package com.hewuzhao.frameanimation.utils;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;

import com.hewuzhao.frameanimation.frameview.FrameScaleType;

import java.util.Arrays;
import java.util.List;

/**
 * @author : hewuzhao
 * @date : 2020/5/5
 * @email : hewuzhao@baidu.com
 */
public class MatrixUtil {
    private static final List<Matrix.ScaleToFit> MATRIX_SCALE_ARRAY = Arrays.asList(
            Matrix.ScaleToFit.FILL,
            Matrix.ScaleToFit.START,
            Matrix.ScaleToFit.CENTER,
            Matrix.ScaleToFit.END
    );

    /**
     * 根据ScaleType配置绘制bitmap的Matrix
     * <p>
     * 参考ImageView的配置规则
     *
     * @param width  view width
     * @param height view height
     */
    public static void configureDrawMatrix(Bitmap bitmap, int width, int height, Matrix matrix, @FrameScaleType int scaleType) {
        int srcWidth = bitmap.getWidth();
        int dstWidth = width;
        int srcHeight = bitmap.getHeight();
        int dstHeight = height;
        switch (scaleType) {
            case FrameScaleType.MATRIX: {
                return;
            }
            case FrameScaleType.CENTER: {
                matrix.setTranslate(Math.round((dstWidth - srcWidth) * 0.5f), Math.round((dstHeight - srcHeight) * 0.5f));
                break;
            }
            case FrameScaleType.CENTER_CROP: {
                float scale;
                float dx = 0f;
                float dy = 0f;
                //按照高缩放
                if (dstHeight * srcWidth > dstWidth * srcHeight) {
                    scale = (float) dstHeight / (float) srcHeight;
                    dx = (dstWidth - srcWidth * scale) * 0.5f;
                } else {
                    scale = (float) dstWidth / (float) srcWidth;
                    dy = (dstHeight - srcHeight * scale) * 0.5f;
                }
                matrix.setScale(scale, scale);
                matrix.postTranslate(dx, dy);
                break;
            }
            case FrameScaleType.CENTER_INSIDE: {
                float scale;
                //小于dst时不缩放
                if (srcWidth <= dstWidth && srcHeight <= dstHeight) {
                    scale = 1.0f;
                } else {
                    scale = Math.min((float) dstWidth / (float) srcWidth, (float) dstHeight / (float) srcHeight);
                }
                float dx = Math.round((dstWidth - srcWidth * scale) * 0.5f);
                float dy = Math.round((dstHeight - srcHeight * scale) * 0.5f);
                matrix.setScale(scale, scale);
                matrix.postTranslate(dx, dy);
                break;
            }
            default: {
                RectF srcRect = new RectF(0f, 0f, bitmap.getWidth(), bitmap.getHeight());
                RectF dstRect = new RectF(0f, 0f, width, height);
                matrix.setRectToRect(srcRect, dstRect, MATRIX_SCALE_ARRAY.get(scaleType - 1));
            }
        }
    }
}
