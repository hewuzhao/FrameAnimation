package com.hewuzhao.frameanimation.frameview;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static com.hewuzhao.frameanimation.frameview.FrameViewStatus.DESTROY;
import static com.hewuzhao.frameanimation.frameview.FrameViewStatus.END;
import static com.hewuzhao.frameanimation.frameview.FrameViewStatus.IDLE;
import static com.hewuzhao.frameanimation.frameview.FrameViewStatus.START;
import static com.hewuzhao.frameanimation.frameview.FrameViewStatus.STOP;

/**
 * @author hewuzhao
 * @date 2020-02-09
 * <p>
 * 逐帧动画的播放状态
 */
@IntDef({IDLE, START, STOP, END, DESTROY})
@Retention(RetentionPolicy.SOURCE)
public @interface FrameViewStatus {

    /**
     * 初始化
     */
    int IDLE = 1;

    /**
     * 开始
     */
    int START = 2;

    /**
     * 停止
     */
    int STOP = 4;

    /**
     * 结束
     */
    int END = 5;

    /**
     * 彻底销毁
     */
    int DESTROY = 6;
}
