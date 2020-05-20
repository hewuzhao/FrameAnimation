package com.hewuzhao.frameanimation.bytespool;

/**
 * @author hewuzhao
 * @date 2020/5/20
 */
public class BytesBuffer {
    public byte[] data;
    public int offset;
    public int length;

    public BytesBuffer(int capacity) {
        this.data = new byte[capacity];
    }

    public BytesBuffer() {

    }
}
