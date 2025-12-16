package com.domidodo.logx.common.util;


import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import java.util.List;

public class JsonUtil {

    public static String toJson(Object obj) {
        return JSON.toJSONString(obj);
    }

    public static <T> T parse(String json, Class<T> clazz) {
        return JSON.parseObject(json, clazz);
    }

    public static <T> List<T> parseArray(String json, Class<T> clazz) {
        return JSON.parseArray(json, clazz);
    }

    public static JSONObject parseObject(String json) {
        return JSON.parseObject(json);
    }
}