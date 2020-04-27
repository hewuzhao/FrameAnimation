package com.hewuzhao.frameanimation.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;

import com.hewuzhao.frameanimation.FrameApplication;
import com.hewuzhao.frameanimation.frameview.FrameImage;

/**
 * @author hewuzhao
 * @date 2020-02-02
 */
public class ResourceUtil {

    /**
     * int --> byte[] 整形转byte[]
     *
     * @param res
     * @return
     */
    public static byte[] int2byte(int res) {
        byte[] targets = new byte[4];

        // 最低位
        targets[0] = (byte) (res & 0xff);
        // 次低位
        targets[1] = (byte) ((res >> 8) & 0xff);
        // 次高位
        targets[2] = (byte) ((res >> 16) & 0xff);
        // 最高位,无符号右移
        targets[3] = (byte) (res >>> 24);
        return targets;
    }

    /**
     * byte[] -->int byte[]转整形
     *
     * @param res
     * @return
     */
    public static int byte2int(byte[] res) {
        // 一个byte数据左移24位变成0x??000000，再右移8位变成0x00??0000
        // | 表示安位或

        return (res[0] & 0xff) | ((res[1] << 8) & 0xff00)
                | ((res[2] << 24) >>> 8) | (res[3] << 24);
    }


    public static Bitmap getBitmap(String drawableName, BitmapFactory.Options options) {
        return BitmapFactory.decodeResource(FrameApplication.sApplication.getResources(),
                getDrawableId(FrameApplication.sApplication, drawableName), options);
    }

    private static int getDrawableId(Context context, String resName) {
        return context.getResources().getIdentifier(resName, "drawable", context.getPackageName());
    }
}
