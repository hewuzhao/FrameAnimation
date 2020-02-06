package com.hewuzhao.frameanimation.utils;

import java.io.Closeable;

/**
 * Created by hewuzhao
 * on 2020-02-07
 */
public class CommonUtil {

    public static void closeSafely(Closeable obj) {
        if (obj != null) {
            try {
                obj.close();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }


}
