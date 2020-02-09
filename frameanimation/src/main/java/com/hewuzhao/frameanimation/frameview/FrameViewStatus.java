package com.hewuzhao.frameanimation.frameview;

/**
 * Created by hewuzhao
 * on 2020-02-09
 *
 * 逐帧动画的播放状态
 */
public class FrameViewStatus {

    /**
     * 初始化
     */
    public static final int IDLE = 1;

    /**
     * 开始
     */
    public static final int START = 2;

    /**
     * 停止
     */
    public static final int STOP = 4;

    /**
     * 结束
     */
    public static final int END = 5;

    /**
     * 彻底销毁
     */
    public static final int DESTROY = 6;
}
