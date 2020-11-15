# FrameAnimation

FrameAnimation是一个简洁流畅的高性能帧动画控件。
结合使用了TextureView和BlobCache，逐帧解析与绘制，避免了多帧的情况下使用Android原生AnimationDrawable带来的OOM和卡顿问题。

BlobCache算法可以查看：
1. [BlobCache算法详解](https://blog.csdn.net/hewuzhao/article/details/108028320)
2. [BlobCache与DiskLruCache的读写对比](https://blog.csdn.net/hewuzhao/article/details/108696808)

## 示例
![image](https://github.com/hewuzhao/FrameAnimation/image/gif-demo.gif）

## 添加配置
```
dependencies {
    ...
    implementation 'com.hewuzhao.frameanimation:frameanimation:1.0.0'
    ...
}
```

## 使用方法
**动画资源图片，在/res/drawable下建立xxx.xml文件：**
```
<animation-list xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:oneshot="false"
    app:maxBytes="524288000"
    app:maxEntries="100"
    app:version="1">

    <item
        android:drawable="@drawable/big_00"
        android:duration="60" />
    <item
        android:drawable="@drawable/big_01"
        android:duration="60" />
    <item
        android:drawable="@drawable/big_02"
        android:duration="60" />
    <item
        android:drawable="@drawable/big_03"
        android:duration="60" />
    <item
        android:drawable="@drawable/big_04"
        android:duration="60" />
    <item
        android:drawable="@drawable/big_05"
        android:duration="60" />
    <item
        android:drawable="@drawable/big_06"
        android:duration="60" />
    ....

</animation-list>
```

|Attributes|format|describe|
|-----|-----|-----|
|maxBytes    |integer    |BlobCache缓存的最大容量，单位：字节    |
|maxEntries    |integer    |BlobCache缓存的资源个数    |
|version    |integer    |BlobCache版本号    |




**在你的xml布局文件中使用：**
```
<com.hewuzhao.frameanimation.frameview.FrameTextureView
        android:id="@+id/frame_view"
        android:layout_width="0dp"
        android:layout_height="0dp"
        app:src="@drawable/big_animation_drawable"
        app:useCache="true" />
```
|Attributes|format|describe|
|-----|-----|-----|
|src    |reference    |res/drawable下的资源列表    |
|useCache    |boolean    |开关：是否使用BlobCache缓存    |


**启动帧动画：**
```
FrameTextureView.startWithFrameSrc(R.drawable.xxx);
```

## License
```
Copyright (C)  hewuzhao, FrameAnimation Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
