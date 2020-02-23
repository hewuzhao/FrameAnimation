package com.hewuzhao.frameanimation.utils;

import java.io.Closeable;
import java.text.DecimalFormat;
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

    public static String convertUnit(long size) {
        if (size <= 0) {
            return "0";
        }
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }
}
