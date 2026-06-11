package com.openim.tophone.utils;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.gson.JsonObject;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

import java.util.EnumMap;
import java.util.Map;

public final class QrCodeHelper {
    private QrCodeHelper() {
    }

    public static String buildDevicePairingPayload(String deviceCode) {
        JsonObject obj = new JsonObject();
        obj.addProperty("v", 1);
        obj.addProperty("type", "tophone_device");
        obj.addProperty("deviceCode", deviceCode != null ? deviceCode : "");
        return obj.toString();
    }

    public static Bitmap encode(String content, int sizePx) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = new MultiFormatWriter().encode(
                    content,
                    BarcodeFormat.QR_CODE,
                    sizePx,
                    sizePx,
                    hints
            );
            int width = matrix.getWidth();
            int height = matrix.getHeight();
            int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                int offset = y * width;
                for (int x = 0; x < width; x++) {
                    pixels[offset + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
                }
            }
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            return bitmap;
        } catch (Exception e) {
            L.e("QrCodeHelper", "encode failed: " + e.getMessage());
            return null;
        }
    }
}
