package com.hewuzhao.frameanimation.frameview;

import android.text.TextUtils;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by hewuzhao
 * on 2020-02-01
 */
public class FrameImageParser {
    private static final String TAG = "FrameImageParser";

    private static final String TAG_FRAME_LIST = "frame-list";
    private static final String TAG_FRAME = "frame";

    private static final String ATTRIBUTE_FROM = "from";
    private static final String ATTRIBUTE_NAME = "name";
    private static final String ATTRIBUTE_DURATION = "duration";

    /**
     * 解析配置XML文件
     *
     * @param in 配置XML的输入流
     * @return 帧列表信息
     */
    public List<FrameImage> parse(InputStream in) {
        List<FrameImage> result = new ArrayList<>();
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(in, null);
            parser.nextTag();

            parser.require(XmlPullParser.START_TAG, null, TAG_FRAME_LIST);
            while (parser.next() != XmlPullParser.END_TAG) {
                if (parser.getEventType() != XmlPullParser.START_TAG) {
                    continue;
                }

                String name = parser.getName();
                if (TAG_FRAME.equals(name)) {
                    result.add(readOneFrame(parser));
                } else {
                    skip(parser);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "ex: " + e);
        }

        return result;
    }

    private FrameImage readOneFrame(XmlPullParser parser) throws IOException, XmlPullParserException {

        parser.require(XmlPullParser.START_TAG, null, TAG_FRAME);

        String from = parser.getAttributeValue(null, ATTRIBUTE_FROM);
        if (TextUtils.isEmpty(from)) {
            from = "drawable";
        }

        String name = parser.getAttributeValue(null, ATTRIBUTE_NAME);
        if (TextUtils.isEmpty(name)) {
            throw new IllegalStateException();
        }

        String dura = parser.getAttributeValue(null, ATTRIBUTE_DURATION);
        long duration = 100;
        if (!TextUtils.isEmpty(dura)) {
            duration = Long.parseLong(dura);
        }

        FrameImage info = new FrameImage();
        info.setFrom(from);
        info.setName(name);
        info.setDuration(duration);

        parser.nextTag();
        parser.require(XmlPullParser.END_TAG, null, TAG_FRAME);

        return info;
    }

    private void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
        if (parser.getEventType() != XmlPullParser.START_TAG) {
            throw new IllegalStateException();
        }
        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
                case XmlPullParser.END_TAG: {
                    depth--;
                    break;
                }
                case XmlPullParser.START_TAG: {
                    depth++;
                    break;
                }
                default:
            }
        }
    }
}
