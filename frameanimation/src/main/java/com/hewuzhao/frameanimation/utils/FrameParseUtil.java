package com.hewuzhao.frameanimation.utils;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;

import androidx.annotation.DrawableRes;

import com.hewuzhao.frameanimation.FrameApplication;
import com.hewuzhao.frameanimation.frameview.FrameItem;
import com.hewuzhao.frameanimation.frameview.FrameList;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.util.ArrayList;
import java.util.List;

/**
 * @author hewuzhao
 * @date 2020/4/19
 */
public class FrameParseUtil {
    private static final String TAG = "FrameParseUtil";

    /**
     * 帧动画文件解析
     *
     * @param resId 帧动画文件id
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
            int event = parser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                switch (event) {
                    case XmlPullParser.START_DOCUMENT: {
                        Log.i(TAG, "xml 解析开始.");
                        break;
                    }
                    case XmlPullParser.START_TAG: {
                        String name = parser.getName();
                        if ("animation-list".equals(name)) {
                            int count = parser.getAttributeCount();
                            for (int i = 0; i < count; i++) {
                                String attributeName = parser.getAttributeName(i);
                                if (attributeName != null) {
                                    switch (attributeName) {
                                        case "oneshot": {
                                            boolean oneShot = parser.getAttributeBooleanValue(i, false);
                                            frameList.setOneShot(oneShot);
                                            break;
                                        }
                                        case "maxBytes": {
                                            // default 500M
                                            int maxBytes = parser.getAttributeIntValue(i, 524288000);
                                            frameList.setMaxBytes(maxBytes);
                                            break;
                                        }
                                        case "maxEntries": {
                                            int maxEntries = parser.getAttributeIntValue(i, 100);
                                            frameList.setMaxEntries(maxEntries);
                                            break;
                                        }
                                        case "version": {
                                            int version = parser.getAttributeIntValue(i, 1);
                                            frameList.setVersion(version);
                                            break;
                                        }
                                    }
                                }
                            }
                        } else if ("item".equals(name)) {
                            FrameItem frameItem = new FrameItem();
                            int count = parser.getAttributeCount();
                            for (int i = 0; i < count; i++) {
                                String attributeName = parser.getAttributeName(i);

                                if (attributeName != null) {
                                    switch (attributeName) {
                                        case "drawable": {
                                            // @2131099732
                                            String drawable = parser.getAttributeValue(i);
                                            if (TextUtils.isEmpty(drawable)) {
                                                throw new XmlPullParserException("the drawable is empty, need a drawable.");
                                            }
                                            drawable = drawable.replace("@", "");
                                            String path = res.getResourceName(Integer.parseInt(drawable));
                                            String[] dr = path.split("/");
                                            drawable = dr[dr.length - 1];
                                            frameItem.setDrawableName(drawable);
                                            break;
                                        }
                                        case "duration": {
                                            int duration = parser.getAttributeIntValue(i, 60);
                                            frameItem.setDuration(duration);
                                            break;
                                        }
                                    }
                                }
                            }
                            itemList.add(frameItem);
                        }
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
            Log.e(TAG, "FrameParseUtil, ex=" + ex);
        }
        String[] str = file.split("/");
        file = str[str.length - 1];
        file = file.split("\\.")[0];
        frameList.setFileName(file);
        return frameList;
    }
}
