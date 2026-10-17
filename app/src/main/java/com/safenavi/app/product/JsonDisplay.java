package com.safenavi.app.product;

import org.json.JSONObject;

public final class JsonDisplay {
    private JsonDisplay() { }
    public static String text(JSONObject value, String key, String fallback) {
        if (value == null || value.isNull(key)) return fallback;
        String text = value.optString(key, "").trim();
        return text.isEmpty() || "null".equalsIgnoreCase(text) ? fallback : text;
    }
}
