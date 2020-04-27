package com.hewuzhao.frameanimation.blobcache;

/**
 * @author hewuzhao
 * @date 2020-02-01
 */
public class BlobCacheParams {

    public static final String FLAG_IMAGE_CACHE_INIT = "FLAG_IMAGE_CACHE_INIT";

    public static final int FLAG_IMAGE_CACHE_INIT_VALUE = -250;

    /**
     * assets文件夹下的存放帧动画的文件夹名字
     */
    public static final String NAME_FRAME_LIST_FOLDER = "frame_list";

    /**
     * 缓存的最大文件个数
     */
    public static final int DEFAULT_BLOB_CACHE_MAX_ENTRIES = 1000;

    /**
     * 最大的缓存容量
     */
    public static final int DEFAULT_BLOB_CACHE_MAX_BYTES = 950 * 1024 * 1024;

}
