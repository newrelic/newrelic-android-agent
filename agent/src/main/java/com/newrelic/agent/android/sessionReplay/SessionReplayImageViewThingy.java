package com.newrelic.agent.android.sessionReplay;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;


import androidx.annotation.WorkerThread;

import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.R;

import com.newrelic.agent.android.sessionReplay.models.Attributes;
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.MutationRecord;
import com.newrelic.agent.android.sessionReplay.models.IncrementalEvent.RRWebMutationData;
import com.newrelic.agent.android.sessionReplay.models.RRWebElementNode;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class SessionReplayImageViewThingy implements SessionReplayViewThingyInterface {
    private static final String LOG_TAG = "SessionReplayImageViewThingy";
    
    // Static cache shared across all instances
    // Increased cache size to 1MB for better performance
    private static final LruCache<String, String> imageCache = new LruCache<String, String>(1024) {
        @Override
        protected int sizeOf(String key, String value) {
            // Return the size in KB (approximate)
            return value.length() / 1024;
        }
    };
    
    private List<? extends SessionReplayViewThingyInterface> subviews = new ArrayList<>();
    private final ViewDetails viewDetails;

    public boolean shouldRecordSubviews = false;
    private final ImageView.ScaleType scaleType;
    private final String backgroundColor;
    private String imageData; // Base64 encoded image data
    protected SessionReplayLocalConfiguration sessionReplayLocalConfiguration;
    protected SessionReplayConfiguration sessionReplayConfiguration;

    public SessionReplayImageViewThingy(ViewDetails viewDetails, ImageView view, AgentConfiguration agentConfiguration) {
        this.viewDetails = viewDetails;
        this.sessionReplayLocalConfiguration = agentConfiguration.getSessionReplayLocalConfiguration();
        this.sessionReplayConfiguration = agentConfiguration.getSessionReplayConfiguration();
        this.scaleType = view.getScaleType();
        this.backgroundColor = getBackgroundColor(view);
        if( !shouldMaskImage(view)) {
            this.imageData = getImageFromImageView(view);
        }
    }

    /**
     * Extracts the image from an ImageView and converts it to a Base64 encoded string
     * @param imageView The ImageView to extract the image from
     * @return Base64 encoded image data or null if no image is available
     */
    @WorkerThread
    private String getImageFromImageView(ImageView imageView) {
        try {
            Drawable drawable = imageView.getDrawable();
            if (drawable == null) {
                return null;
            }

            String cacheKey = generateCacheKey(drawable, imageView);
            
            String cachedData = imageCache.get(cacheKey);
            if (cachedData != null) {
                Log.d(LOG_TAG, "Cache hit for image: " + cacheKey);
                return cachedData;
            }

            Bitmap bitmap = drawableToBitmap(drawable, imageView);

            if (bitmap != null && !bitmap.isRecycled()) {
                String base64Data = bitmapToBase64(bitmap);
                if (base64Data != null) {
                    imageCache.put(cacheKey, base64Data);
                    Log.d(LOG_TAG, "Cached image data for key: " + cacheKey);
                }
                return base64Data;
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error processing image", e);
            return null;
        }
        
        return null;
    }

    private Bitmap drawableToBitmap(Drawable drawable, ImageView imageView) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }

        if (drawable instanceof LayerDrawable) {
            Drawable foundDrawable = getFirstBitmapDrawable((LayerDrawable) drawable);
            if (foundDrawable instanceof BitmapDrawable) {
                return ((BitmapDrawable) foundDrawable).getBitmap();
            }
        }

        if (drawable instanceof InsetDrawable) {
            Drawable insetDrawable = ((InsetDrawable) drawable).getDrawable();
            if (insetDrawable instanceof BitmapDrawable) {
                return ((BitmapDrawable) insetDrawable).getBitmap();
            }
        }

        return createBitmapFromDrawable(drawable, imageView);
    }

    private Bitmap createBitmapFromDrawable(Drawable drawable, ImageView imageView) {
        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();

        if (width <= 0 || height <= 0) {
            width = imageView.getWidth();
            height = imageView.getHeight();
        }
        
        if (width <= 0 || height <= 0) {
            return null; // Can't create bitmap with invalid dimensions
        }
        
        DisplayMetrics displayMetrics = imageView.getContext().getResources().getDisplayMetrics();
        Bitmap bitmap = Bitmap.createBitmap(displayMetrics, width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.MULTIPLY);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    /**
     * Generates a cache key based on drawable properties
     */
    private String generateCacheKey(Drawable drawable, ImageView imageView) {
        StringBuilder keyBuilder = new StringBuilder();
        
        keyBuilder.append(drawable.getClass().getSimpleName());
        keyBuilder.append("_");
        keyBuilder.append(drawable.hashCode());
        
        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();
        if (width <= 0 || height <= 0) {
            width = imageView.getWidth();
            height = imageView.getHeight();
        }
        keyBuilder.append("_").append(width).append("x").append(height);
        
        keyBuilder.append("_").append(scaleType.name());
        
        return keyBuilder.toString();
    }

    /**
     * Gets the first non-null BitmapDrawable from a LayerDrawable
     */
    private static Drawable getFirstBitmapDrawable(LayerDrawable layerDrawable) {
        for (int i = 0; i < layerDrawable.getNumberOfLayers(); i++) {
            Drawable drawable = layerDrawable.getDrawable(i);
            if (drawable instanceof BitmapDrawable) {
                return drawable;
            }
        }
        return null;
    }

    /**
     * Converts a bitmap to a Base64 encoded string
     */
    @WorkerThread
    private String bitmapToBase64(Bitmap bitmap) {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 10, byteArrayOutputStream);
                } else {
                    @SuppressWarnings("deprecation")
                    Bitmap.CompressFormat format = Bitmap.CompressFormat.WEBP;
                    bitmap.compress(format, 10, byteArrayOutputStream);
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
        return "";
    }

    /**
     * Gets the image data as a data URL for use in CSS or HTML
     */
    public String getImageDataUrl() {
        if (imageData != null) {
            return "data:image/webp;base64," + imageData;
        }
        return null;
    }

    public String getImageData() {
        return imageData;
    }

    public static void clearImageCache() {
        imageCache.evictAll();
        Log.d(LOG_TAG, "Image cache cleared");
    }

    public static String getCacheStats() {
        return String.format("Cache stats - Size: %d, Hits: %d, Misses: %d", 
                imageCache.size(), imageCache.hitCount(), imageCache.missCount());
    }

    @Override
    public List<? extends SessionReplayViewThingyInterface> getSubviews() {
        return subviews;
    }

    @Override
    public void setSubviews(List<? extends SessionReplayViewThingyInterface> subviews) {
        this.subviews = subviews;
    }

    @Override
    public ViewDetails getViewDetails() {
        return viewDetails;
    }

    @Override
    public boolean shouldRecordSubviews() {
        return shouldRecordSubviews;
    }

    @Override
    public String getCssSelector() {
        return viewDetails.getCssSelector();
    }

    @SuppressLint("DefaultLocale")
    @Override
    public String generateCssDescription() {
        StringBuilder cssBuilder = new StringBuilder(viewDetails.generateCssDescription());
        generateImageCss(cssBuilder);
        return cssBuilder.toString();
    }

    @Override
    public String generateInlineCss() {
        StringBuilder cssBuilder = new StringBuilder(viewDetails.generateInlineCSS());
        cssBuilder.append(" ");
        generateImageCss(cssBuilder);
        return cssBuilder.toString();
    }

    private void generateImageCss(StringBuilder cssBuilder) {
        cssBuilder.append("background-color: ");
        cssBuilder.append(this.backgroundColor);
        cssBuilder.append("; ");

        if (imageData != null) {
            cssBuilder.append("background-image: url(");
            cssBuilder.append(getImageDataUrl());
            cssBuilder.append("); ");
        }

        cssBuilder.append("background-size: ");
        cssBuilder.append(getBackgroundSizeFromScaleType());
        cssBuilder.append("; ");
        cssBuilder.append("background-repeat: no-repeat; ");
        cssBuilder.append("background-position: center; ");
    }

    @Override
    public RRWebElementNode generateRRWebNode() {
        Attributes attributes = new Attributes(viewDetails.getCssSelector());
        return new RRWebElementNode(attributes, RRWebElementNode.TAG_TYPE_DIV, viewDetails.getViewId(), new ArrayList<>());
    }

    @Override
    public List<MutationRecord> generateDifferences(SessionReplayViewThingyInterface other) {
        if (!(other instanceof SessionReplayImageViewThingy)) {
            return null;
        }

        Map<String, String> styleDifferences = new HashMap<>();

        ViewDetails otherDetails = (ViewDetails) other.getViewDetails();

        if (!viewDetails.frame.equals(otherDetails.frame)) {
            styleDifferences.put("left", otherDetails.frame.left + "px");
            styleDifferences.put("top", otherDetails.frame.top + "px");
            styleDifferences.put("width", otherDetails.frame.width() + "px");
            styleDifferences.put("height", otherDetails.frame.height() + "px");
        }

        if (viewDetails.backgroundColor != null && !viewDetails.backgroundColor.equals(otherDetails.backgroundColor)) {
            styleDifferences.put("background-color", otherDetails.backgroundColor);
        }

        if (this.getImageData() != null && !this.getImageData().equals(((SessionReplayImageViewThingy) other).getImageData())) {
            styleDifferences.put("background-image"," url(" + ((SessionReplayImageViewThingy) other).getImageDataUrl() + ")");
        }

        Attributes attributes = new Attributes(viewDetails.getCSSSelector());
        attributes.setMetadata(styleDifferences);
        List<MutationRecord> mutations = new ArrayList<>();
        mutations.add(new RRWebMutationData.AttributeRecord(viewDetails.getViewId(), attributes));
        return mutations;
    }

    @Override
    public List<RRWebMutationData.AddRecord> generateAdditionNodes(int parentId) {
        RRWebElementNode node = generateRRWebNode();
        node.attributes.metadata.put("style", generateInlineCss());
        RRWebMutationData.AddRecord addRecord = new RRWebMutationData.AddRecord(
                parentId,
                null,
                node);

        List<RRWebMutationData.AddRecord> adds = new ArrayList<>();
        adds.add(addRecord);
        return adds;
    }

    @Override
    public int getViewId() {
        return viewDetails.viewId;
    }

    @Override
    public int getParentViewId() {
        return viewDetails.parentId;
    }


    private String getBackgroundColor(ImageView view) {
        Drawable background = view.getBackground();
        if (background instanceof ColorDrawable) {
            int color = ((ColorDrawable) background).getColor();
            return String.format("#%06X", (0xFFFFFF & color));
        }
        return "#CCCCCC";
    }

    private String getBackgroundSizeFromScaleType() {
        switch (scaleType) {
            case FIT_XY:
                return "100% 100%";
            case CENTER_CROP:
                return "cover";
            case FIT_CENTER:
            case CENTER_INSIDE:
                return "contain";
            default:
                return "auto";
        }
    }

    protected boolean shouldMaskImage(ImageView view) {
        boolean hasMask = hasMaskingTag(view) || isMaskedByClass(view);
        boolean hasUnMask = false;

        if (Objects.equals(sessionReplayConfiguration.getMode(), "custom")) {
             hasUnMask = hasUnmaskingTag(view) || isUnmaskedByClass(view);
        }

        return (sessionReplayConfiguration.isMaskAllImages() && !hasUnMask) ||
               (!sessionReplayConfiguration.isMaskAllImages() && hasMask);
    }

    private boolean hasMaskingTag(ImageView view) {
        Object viewTag = view.getTag();
        Object privacyTag = view.getTag(R.id.newrelic_privacy);

        return "nr-mask".equals(viewTag) ||
               "nr-mask".equals(privacyTag) ||
               (view.getTag() != null &&
                (sessionReplayConfiguration.shouldMaskViewTag(view.getTag().toString()) ||
                 sessionReplayLocalConfiguration.shouldMaskViewTag(view.getTag().toString())));
    }

    private boolean hasUnmaskingTag(ImageView view) {
        Object viewTag = view.getTag();
        Object privacyTag = view.getTag(R.id.newrelic_privacy);

        return "nr-unmask".equals(viewTag) ||
               "nr-unmask".equals(privacyTag) ||
               (view.getTag() != null &&
                (sessionReplayConfiguration.shouldUnmaskViewTag(view.getTag().toString()) ||
                 sessionReplayLocalConfiguration.shouldUnmaskViewTag(view.getTag().toString())));
    }

    private boolean isMaskedByClass(ImageView view) {
        return checkMaskUnMaskViewClass(sessionReplayConfiguration.getMaskedViewClasses(), view) ||
               checkMaskUnMaskViewClass(sessionReplayLocalConfiguration.getMaskedViewClasses(), view);
    }

    private boolean isUnmaskedByClass(ImageView view) {
        return checkMaskUnMaskViewClass(sessionReplayConfiguration.getUnmaskedViewClasses(), view) ||
               checkMaskUnMaskViewClass(sessionReplayLocalConfiguration.getUnmaskedViewClasses(), view);
    }

    private boolean checkMaskUnMaskViewClass(Set<String> viewClasses, ImageView view) {
        if (viewClasses == null) {
            return false;
        }

        Class<?> clazz = view.getClass();
        while (clazz != null) {
            if (viewClasses.contains(clazz.getName())) {
                return true;
            }
            clazz = clazz.getSuperclass();
        }
        return false;
    }
}