package com.newrelic.agent.android.sessionReplay;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Build;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.WorkerThread;

import java.io.ByteArrayOutputStream;

/**
 * Utility class for image compression and conversion operations
 * Used by both SessionReplayImageViewThingy and ComposeImageThingy
 */
public class ImageCompressionUtils {
    private static final String LOG_TAG = "ImageCompressionUtils";

    /**
     * Converts a bitmap to a Base64 encoded string with WEBP compression
     * @param bitmap The bitmap to convert
     * @return Base64 encoded image data or null if conversion fails
     */
    @WorkerThread
    public static String bitmapToBase64(Bitmap bitmap) {
        return bitmapToBase64(bitmap, 10);
    }

    /**
     * Converts a bitmap to a Base64 encoded string with WEBP compression
     * @param bitmap The bitmap to convert
     * @param quality The compression quality (0-100)
     * @return Base64 encoded image data or null if conversion fails
     */
    @WorkerThread
    public static String bitmapToBase64(Bitmap bitmap, int quality) {

        Bitmap bitmapCopy;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.getConfig() == Bitmap.Config.HARDWARE) {
            bitmapCopy = bitmap;
        } else {
            bitmapCopy = bitmap.copy(Bitmap.Config.ARGB_8888, false);
        }

        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    bitmapCopy.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, byteArrayOutputStream);
                } else {
                    @SuppressWarnings("deprecation")
                    Bitmap.CompressFormat format = Bitmap.CompressFormat.WEBP;
                    bitmapCopy.compress(format, quality, byteArrayOutputStream);
                }

                byte[] byteArray = byteArrayOutputStream.toByteArray();
                return Base64.encodeToString(byteArray, Base64.NO_WRAP);
            } catch (Exception e) {
                Log.e(LOG_TAG, "Error converting bitmap to Base64", e);
                return null;
            }
        } catch (Exception ignored) {
            // Ignore cleanup errors
        }
        return null;
    }

    /**
     * Converts Base64 image data to a data URL for use in CSS or HTML
     * @param base64Data The Base64 encoded image data
     * @return A data URL string or null if input is null
     */
    public static String toImageDataUrl(String base64Data) {
        if (base64Data != null) {
            return "data:image/webp;base64," + base64Data;
        }
        return null;
    }
}