package com.newrelic.agent.android.sessionReplay;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.ImageView;


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
    private List<? extends SessionReplayViewThingyInterface> subviews = new ArrayList<>();
    private ViewDetails viewDetails;

    public boolean shouldRecordSubviews = false;
    private ImageView.ScaleType scaleType;
    private String backgroundColor;
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
    private String getImageFromImageView(ImageView imageView) {
        try {
            Drawable drawable = imageView.getDrawable();
            if (drawable == null) {
                return null;
            }

            Bitmap bitmap = null;
            
            // If it's already a BitmapDrawable, extract the bitmap directly
            if (drawable instanceof BitmapDrawable) {
                bitmap = ((BitmapDrawable) drawable).getBitmap();
            } else if (drawable instanceof LayerDrawable){

                Drawable drawable1 = getFirstDrawable((LayerDrawable) drawable);
                try{
                if (drawable1 instanceof BitmapDrawable) {
                    bitmap = ((BitmapDrawable) drawable1).getBitmap();
                } } catch (Exception e) {
                    // Log error if needed, but don't crash
                    Log.e(LOG_TAG, "Error extracting bitmap from InsetDrawable", e);
                }


            }else if (drawable instanceof InsetDrawable){
                try {
                    Drawable drawable1 = ((InsetDrawable) drawable).getDrawable();
                    if(drawable1 != null)
                      bitmap = ((BitmapDrawable) drawable1).getBitmap();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Error extracting bitmap from InsetDrawable", e);
                }
            }
            else {
                // For other drawable types, draw it onto a canvas to create a bitmap
                int width = drawable.getIntrinsicWidth();
                int height = drawable.getIntrinsicHeight();
                
                // Handle cases where drawable doesn't have intrinsic dimensions
                if (width <= 0 || height <= 0) {
                    width = imageView.getWidth();
                    height = imageView.getHeight();
                }
                
                if (width <= 0 || height <= 0) {
                    return null; // Can't create bitmap with invalid dimensions
                }
                DisplayMetrics displayMetrics = imageView.getContext().getResources().getDisplayMetrics();
                bitmap = Bitmap.createBitmap(displayMetrics,width, height, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.MULTIPLY);
                drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                drawable.draw(canvas);
            }

            if (bitmap != null && !bitmap.isRecycled()) {
                return bitmapToBase64(bitmap);
            }
        } catch (Exception e) {
            // Log error if needed, but don't crash
            return null;
        }
        
        return null;
    }

    /**
     * Gets the first non-null drawable from a LayerDrawable
     * @param layerDrawable The LayerDrawable to search
     * @return The first non-null Drawable, or null if none found
     */
    private static Drawable getFirstDrawable(LayerDrawable layerDrawable) {
        for (int i = 0; i < layerDrawable.getNumberOfLayers(); i++) {
            Drawable drawable = layerDrawable.getDrawable(i);
            if (drawable != null) {
                return drawable;
            }
        }
        return null;
    }

    /**
     * Converts a bitmap to a Base64 encoded string
     * @param bitmap The bitmap to convert
     * @return Base64 encoded string representation of the bitmap
     */
    private String bitmapToBase64(Bitmap bitmap) {
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 10, byteArrayOutputStream);
            } else {
                bitmap.compress(Bitmap.CompressFormat.WEBP, 10, byteArrayOutputStream);
            }
            byte[] byteArray = byteArrayOutputStream.toByteArray();
            return Base64.encodeToString(byteArray, Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Gets the image data as a data URL for use in CSS or HTML
     * @return Data URL string or null if no image data is available
     */
    public String getImageDataUrl() {
        if (imageData != null) {
            return "data:image/webp;base64," + imageData;
        }
        return null;
    }

    /**
     * Gets the raw Base64 encoded image data
     * @return Base64 encoded image string or null if no image data is available
     */
    public String getImageData() {
        return imageData;
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
        cssBuilder.append("}");

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
        
        // If we have image data, use it as background image
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
        return new RRWebElementNode(attributes, RRWebElementNode.TAG_TYPE_DIV, viewDetails.getViewId(), Collections.emptyList());
    }

    @Override
    public List<MutationRecord> generateDifferences(SessionReplayViewThingyInterface other) {
        // Make sure this is not null and is of the same type
        if (!(other instanceof SessionReplayImageViewThingy)) {
            return null;
        }

        // Create a map to store style differences
        Map<String, String> styleDifferences = new HashMap<>();

        // Compare frames
        if (!viewDetails.frame.equals(other.getViewDetails().frame)) {
            styleDifferences.put("left", other.getViewDetails().frame.left + "px");
            styleDifferences.put("top", other.getViewDetails().frame.top + "px");
            styleDifferences.put("width", other.getViewDetails().frame.width() + "px");
            styleDifferences.put("height", other.getViewDetails().frame.height() + "px");
        }

        // Compare background colors if available
        if (viewDetails.backgroundColor != null && other.getViewDetails().backgroundColor != null) {
            if (!viewDetails.backgroundColor.equals(other.getViewDetails().backgroundColor)) {
                styleDifferences.put("background-color", other.getViewDetails().backgroundColor);
            }
        } else if (other.getViewDetails().backgroundColor != null) {
            styleDifferences.put("background-color", other.getViewDetails().backgroundColor);
        }

        // Compare Image Data
        if (this.getImageData() != null && ((SessionReplayImageViewThingy) other).getImageData() != null) {
            if (!this.getImageData().equals(((SessionReplayImageViewThingy) other).getImageData())) {
                styleDifferences.put("background-image"," url(" + ((SessionReplayImageViewThingy) other).getImageDataUrl() + ")");
            }
        }

        // Create and return a MutationRecord with the style differences
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

    private String getBackgroundColor(ImageView view) {
        Drawable background = view.getBackground();
        if (background != null) {
            return "#FF474C"; // Placeholder color, you might want to implement a method to extract actual color
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
        // Check if view has tags that prevent masking
        Object viewTag = view.getTag();
        Object privacyTag = view.getTag(R.id.newrelic_privacy);
        boolean hasMask = ("nr-mask".equals(viewTag) || "nr-mask".equals(privacyTag)) || (view.getTag() != null && (sessionReplayConfiguration.shouldMaskViewTag(view.getTag().toString()) || sessionReplayLocalConfiguration.shouldMaskViewTag(view.getTag().toString()))) || checkMaskUnMaskViewClass(sessionReplayConfiguration.getMaskedViewClasses(),view) || checkMaskUnMaskViewClass(sessionReplayLocalConfiguration.getMaskedViewClasses(),view);
        boolean hasUnMask = false;
        if(Objects.equals(sessionReplayConfiguration.getMode(), "custom")) {
             hasUnMask = ("nr-unmask".equals(viewTag) || "nr-unmask".equals(privacyTag)) || (view.getTag() != null && (sessionReplayConfiguration.shouldUnmaskViewTag(view.getTag().toString()) || sessionReplayLocalConfiguration.shouldUnmaskViewTag(view.getTag().toString()))) || checkMaskUnMaskViewClass(sessionReplayConfiguration.getUnmaskedViewClasses(), view) || checkMaskUnMaskViewClass(sessionReplayLocalConfiguration.getUnmaskedViewClasses(), view);
        }
        return  (sessionReplayConfiguration.isMaskAllImages() && !hasUnMask) || (!sessionReplayConfiguration.isMaskAllImages() && hasMask);
    }

    private boolean checkMaskUnMaskViewClass(Set<String> viewClasses, ImageView view) {

        Class clazz = view.getClass();

        while (clazz!= null) {
            if (viewClasses != null && viewClasses.contains(clazz.getName())) {
                return true;
            }
            clazz = clazz.getSuperclass();
        }
        return false;
    }
}