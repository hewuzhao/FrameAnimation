package com.hewuzhao.frameanimation.utils;

import java.io.Closeable;
import java.util.Collection;

/**
 * @author hewuzhao
 * @date 2020-02-07
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

    public static boolean isEmpty(Collection collection) {
        return collection == null || collection.isEmpty();
    }

    public static int size(Collection collection) {
        if (collection == null) {
            return 0;
        }
        return collection.size();
    }
}
