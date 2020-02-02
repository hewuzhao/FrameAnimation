package com.hewuzhao.frameanimation.frameview;

import androidx.annotation.NonNull;

/**
 * Created by hewuzhao
 * on 2020-02-01
 */
public class FrameImage {
    private String from;

    private String name;

    private long duration;

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

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    @NonNull
    @Override
    public String toString() {
        return "{ from = " + from
                + "; name = " + name
                + "; duration = " + duration
                + " }";
    }
}
