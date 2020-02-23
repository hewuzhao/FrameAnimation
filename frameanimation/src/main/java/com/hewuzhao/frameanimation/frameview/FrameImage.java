package com.hewuzhao.frameanimation.frameview;

import androidx.annotation.NonNull;

/**
 * @author hewuzhao
 * @date 2020-02-01
 */
public class FrameImage {
    private String from;

    private String name;

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @NonNull
    @Override
    public String toString() {
        return "{ from = " + from
                + "; name = " + name
                + " }";
    }
}
