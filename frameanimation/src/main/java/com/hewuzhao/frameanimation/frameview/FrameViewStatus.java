package com.hewuzhao.frameanimation.frameview;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static com.hewuzhao.frameanimation.frameview.FrameViewStatus.DESTROY;
import static com.hewuzhao.frameanimation.frameview.FrameViewStatus.END;
import static com.hewuzhao.frameanimation.frameview.FrameViewStatus.IDLE;
import static com.hewuzhao.frameanimation.frameview.FrameViewStatus.PAUSE;
import static com.hewuzhao.frameanimation.frameview.FrameViewStatus.START;

/**
 * @author hewuzhao
 * @date 2020-02-09
 * <p>
 * 逐帧动画的播放状态
 */
@IntDef({IDLE, START, PAUSE, END, DESTROY})
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
     * 暂停
     */
    int PAUSE = 3;

    /**
     * 结束
     */
    int END = 4;

    /**
     * 彻底销毁
     */
    int DESTROY = 5;
}
