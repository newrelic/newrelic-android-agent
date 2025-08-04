package com.newrelic.agent.android.sessionReplay.internal;

import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable; // Use androidx annotation

import java.lang.reflect.Field;

public class ViewBackgroundHelper {

    /**
     * Attempts to get the background color of a View, covering several common Drawable types.
     *
     * @param view The View to inspect.
     * @return A 4-component hexadecimal string (RRGGBBAA) representing the background color,
     *         or null if the color cannot be determined or the view has no background.
     */
    @NonNull
    public static String getBackgroundColor(View view) {
        Drawable background = view.getBackground();

        if (background == null) {
            return ""; // No background drawable
        }


        // Check for background tint list
        if (view.getBackgroundTintList() != null) {
            return toRGBAHexString(view.getBackgroundTintList().getColorForState(new int[]{android.R.attr.state_enabled}, 0));
        }
        // Delegate to a helper method to handle different drawable types
        return getDrawableColor(background);
    }

    /**
     * Recursively attempts to extract a single color from a Drawable.
     *
     * @param drawable The Drawable to inspect.
     * @return A 4-component hexadecimal string (RRGGBBAA) representing the color,
     *         or null if the color cannot be determined.
     */
    @NonNull
    private static String getDrawableColor(Drawable drawable) {
        if (drawable instanceof ColorDrawable) {
            int color = ((ColorDrawable) drawable).getColor();
            return toRGBAHexString(color);
        } else if (drawable instanceof GradientDrawable) {
            GradientDrawable gradientDrawable = (GradientDrawable) drawable;
            // GradientDrawables can have multiple colors for gradients.
            // We'll try to get a solid color if it exists.
            // Note: Getting a *single* color from a complex gradient is not always meaningful.
            // This attempts to get the start color for simplicity or the single color if not a gradient.

            // For API 24+
            if (gradientDrawable.getColors() != null && gradientDrawable.getColors().length > 0) {
                return toRGBAHexString(gradientDrawable.getColors()[0]); // Get the first color in a gradient
            }

            // Fallback for older APIs or solid color GradientDrawable
            if (gradientDrawable.getColor() != null) {
                return toRGBAHexString(gradientDrawable.getColor().getDefaultColor()); // Get the solid color
            }

        } else if (drawable instanceof InsetDrawable) {
            // InsetDrawable wraps another drawable, try to get the color from the wrapped drawable
            return getDrawableColor(((InsetDrawable) drawable).getDrawable());
        } else if (drawable instanceof RippleDrawable) {
            // RippleDrawable has different states and layers.
            // The underlying content is often the drawable at index 0 or 1.
            // This is a simplified approach, the actual background color might be complex.
            RippleDrawable rippleDrawable = (RippleDrawable) drawable;
            // On some APIs, the content might be at index 1
            if (rippleDrawable.getNumberOfLayers() > 1) {
                return getDrawableColor(rippleDrawable.getDrawable(1));
            }
        } else if (drawable instanceof LayerDrawable) {
            // LayerDrawable is a stack of drawables.
            // The background color might be the color of the bottom layer.
            LayerDrawable layerDrawable = (LayerDrawable) drawable;
            if (layerDrawable.getNumberOfLayers() > 0) {
                // Get the bottom layer
                Drawable bottomLayer = layerDrawable.getDrawable(0);
                return getDrawableColor(bottomLayer);
            }
        } else if (drawable instanceof BitmapDrawable) {
            // BitmapDrawable represents an image. It doesn't have a single background color.
            // You might return a default color or null in this case.
            // Returning null indicates we can't get a meaningful single color.
            return "";
        }



        // Handle other drawable types as needed
        // You might encounter StateListDrawable, ShapeDrawable, etc.

        return ""; // Unable to determine color for this drawable type
    }

    /**
     * Converts an Android Color integer to an RGBA hexadecimal string.
     *
     * @param color The Android Color integer.
     * @return An RGBA hexadecimal string (RRGGBBAA).
     */
    private static String toRGBAHexString(int color) {
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        int alpha = Color.alpha(color);
        return String.format("#%02x%02x%02x%02x", red, green, blue, alpha);
    }



    public static void getBackGroundFromDrawable(StringBuilder backgroundColorStringBuilder, GradientDrawable backgroundDrawable,float density) {

        // Extract corner radius
        float[] cornerRadii = backgroundDrawable.getCornerRadii();
        if (cornerRadii != null) {
            backgroundColorStringBuilder.append(" border-radius: ").append(getPixel(cornerRadii[0],density)).append("px;");
        } else {
            float cornerRadius = getPixel(backgroundDrawable.getCornerRadius(),density);
            if (cornerRadius > 0) {
                backgroundColorStringBuilder.append(" border-radius: ").append(cornerRadius).append("px;");
            }
        }


        Paint strokePaint = getStrokePaint(backgroundDrawable);
        if (strokePaint != null) {
            backgroundColorStringBuilder.append(" border:").append(getPixel(strokePaint.getStrokeWidth(), density)).append("px").append(" solid #").append(Integer.toHexString(strokePaint.getColor()).substring(2)).append(";");
        }
    }

    public static Paint getFillPaint(GradientDrawable gradientDrawable) {
        try {
            Field mFillPaintField = GradientDrawable.class.getDeclaredField("mFillPaint");
            mFillPaintField.setAccessible(true);
            return (Paint)mFillPaintField.get(gradientDrawable);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Paint getStrokePaint(GradientDrawable gradientDrawable) {
        try {
            Field mStrokePaintPaintField = GradientDrawable.class.getDeclaredField("mStrokePaint");
            mStrokePaintPaintField.setAccessible(true);
            return (Paint)mStrokePaintPaintField.get(gradientDrawable);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }
    private static float getPixel(float value, float density) {
        return  (value /density);
    }
}