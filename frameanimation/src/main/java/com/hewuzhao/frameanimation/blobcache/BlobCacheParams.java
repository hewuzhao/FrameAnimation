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
     * BlobCache缓存文件名
     */
    public static final String IMAGE_CACHE_FILE_BLOBCHCHE = "frame_ani_image_blobcache";

    /**
     * 缓存的最大文件个数
     */
    public static final int IMAGE_CACHE_MAX_ENTRIES = 5000;

    /**
     * 最大的缓存容量
     */
    public static final int IMAGE_CACHE_MAX_BYTES = 950 * 1024 * 1024;

    /**
     * 版本号
     */
    public static final int IMAGE_CACHE_VERSION = 1;
}
