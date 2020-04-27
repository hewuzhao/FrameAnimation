package com.hewuzhao.frameanimation.frameview;

import com.hewuzhao.frameanimation.utils.CommonUtil;

import java.util.List;

/**
 * @author hewuzhao
 * @date 2020/4/13
 */
public class FrameList {

    private String fileName;

    private int version;

    private int maxEntries;

    private int maxBytes;

    private boolean oneShot;

    private List<FrameItem> frameItemList;

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public int getMaxEntries() {
        return maxEntries;
    }

    public void setMaxEntries(int maxEntries) {
        this.maxEntries = maxEntries;
    }

    public int getMaxBytes() {
        return maxBytes;
    }

    public void setMaxBytes(int maxBytes) {
        this.maxBytes = maxBytes;
    }

    public List<FrameItem> getFrameItemList() {
        return frameItemList;
    }

    public void setFrameItemList(List<FrameItem> frameItemList) {
        this.frameItemList = frameItemList;
    }

    public boolean isOneShot() {
        return oneShot;
    }

    public void setOneShot(boolean oneShot) {
        this.oneShot = oneShot;
    }

    public int getFrameItemSize() {
        return CommonUtil.size(frameItemList);
    }

    public FrameItem getFrameItemByIndex(int index) {
        if (index < 0 || index >= getFrameItemSize()) {
            return null;
        }
        return frameItemList.get(index);
    }
}
