package com.hewuzhao.frameanimation.utils;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.util.Xml;

import androidx.annotation.DrawableRes;

import com.hewuzhao.frameanimation.FrameApplication;
import com.hewuzhao.frameanimation.R;
import com.hewuzhao.frameanimation.frameview.FrameItem;
import com.hewuzhao.frameanimation.frameview.FrameList;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * @author hewuzhao
 * @date 2020/4/19
 */
public class FrameParseUtil {

    /**
     * 帧动画文件解析
     *
     * @param resId   帧动画文件id
     */
    public static FrameList parse(@DrawableRes int resId) {
        FrameList frameList = new FrameList();
        List<FrameItem> itemList = new ArrayList<>();
        Context context = FrameApplication.sApplication;
        Resources res = context.getResources();
        TypedValue value = new TypedValue();

        res.getValueForDensity(resId, 0, value, true);
        String file = value.string.toString();
        if (TextUtils.isEmpty(file)) {
            return frameList;
        }

        try {
            AssetManager assetManager = context.getAssets();
            XmlResourceParser parser = assetManager.openXmlResourceParser(0, file);
            AttributeSet attrs = Xml.asAttributeSet(parser);
            int event = parser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                switch (event) {
                    case XmlPullParser.START_DOCUMENT: {
                        Log.i("fuck", "xml 解析开始.");
                        break;
                    }
                    case XmlPullParser.START_TAG: {
                        String name = parser.getName();
                        Log.e("fuck", "start tag, name = " + name);
                        if ("animation-list".equals(name)) {
                            Class styleClass = Class.forName("com.android.internal.R$styleable");
                            Field field = styleClass.getDeclaredField("AnimationDrawable");
                            field.setAccessible(true);
                            TypedArray a = res.obtainAttributes(attrs, (int[]) field.get(null));
                            field = styleClass.getDeclaredField("AnimationDrawable_oneshot");
                            field.setAccessible(true);
                            boolean oneShot = a.getBoolean((Integer) field.get(null), false);
                            a.recycle();

                            TypedArray app = res.obtainAttributes(attrs, R.styleable.BlobCache);
                            int version = app.getInt(R.styleable.BlobCache_version, 1);
                            int maxEntries = app.getInt(R.styleable.BlobCache_maxEntries, 100);
                            int maxBytes = app.getInt(R.styleable.BlobCache_maxBytes, 100 * 1024 * 1024);
                            app.recycle();
                            frameList.setVersion(version);
                            frameList.setMaxBytes(maxBytes);
                            frameList.setMaxEntries(maxEntries);
                            frameList.setOneShot(oneShot);
                            Log.e("fuck", "oneShot = " + oneShot + ", version=" + version
                                    + ", maxEntries=" + maxEntries + ", maxBytes=" + maxBytes);

                        } else if ("item".equals(name)) {
                            Class styleClass = Class.forName("com.android.internal.R$styleable");
                            Field field = styleClass.getDeclaredField("AnimationDrawableItem");
                            field.setAccessible(true);
                            TypedArray itemArray = res.obtainAttributes(attrs, (int[]) field.get(null));
                            field = styleClass.getDeclaredField("AnimationDrawableItem_duration");
                            field.setAccessible(true);
                            int duration = itemArray.getInt((Integer) field.get(null), 100);

                            field = styleClass.getDeclaredField("AnimationDrawableItem_drawable");
                            field.setAccessible(true);
                            String drawable = itemArray.getString((Integer) field.get(null));
                            if (TextUtils.isEmpty(drawable)) {
                                throw new XmlPullParserException("the drawable is empty, need a drawable.");
                            }
                            String[] dr = drawable.split("/");
                            drawable = dr[dr.length -1];
                            drawable = drawable.split("\\.")[0];

                            FrameItem frameItem = new FrameItem();
                            frameItem.setDuration(duration);
                            frameItem.setDrawableName(drawable);
                            itemList.add(frameItem);

                            Log.e("fuck", "duration=" + duration + ", drawable=" + drawable);
                        }
                        break;
                    }
                    case XmlPullParser.TEXT: {
                        break;
                    }
                    case XmlPullParser.END_TAG: {
                        String name = parser.getName();
                        Log.e("fuck", "end tag, name=" + name);
                        break;
                    }
                    default:
                        break;
                }

                event = parser.next();
            }
            frameList.setFrameItemList(itemList);
        } catch (Exception ex) {
            ex.printStackTrace();
            Log.e("fuck", "ex=" + ex);
        }
        String[] str = file.split("/");
        file = str[str.length -1];
        file = file.split("\\.")[0];
        Log.e("fuck", "fileName=" + file);
        frameList.setFileName(file);
        return frameList;
    }
}
