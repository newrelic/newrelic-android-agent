package com.newrelic.agent.android.sessionReplay.internal;

import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.text.Spannable;

import androidx.compose.ui.Modifier;
import androidx.compose.ui.geometry.Offset;
import androidx.compose.ui.geometry.Size;
import androidx.compose.ui.layout.LayoutCoordinates;
import androidx.compose.ui.layout.Placeable;
import androidx.compose.ui.node.LayoutNode;
import androidx.compose.ui.semantics.SemanticsNode;
import androidx.compose.ui.unit.IntSize;

import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Utility class for reflection-based operations
 * Used to access private/internal fields and methods from Android and Compose UI classes
 */
public class ReflectionUtils {

    private static final AgentLog log = AgentLogManager.getAgentLog();
    private static final String TAG = "ReflectionUtils";

    /**
     * Gets the fill paint from a GradientDrawable using reflection
     * @param gradientDrawable The GradientDrawable to extract the fill paint from
     * @return The Paint object or null if reflection fails or gradientDrawable is null
     */
    public static Paint getFillPaint(GradientDrawable gradientDrawable) {
        if (gradientDrawable == null) {
            return null;
        }

        try {
            Field mFillPaintField = GradientDrawable.class.getDeclaredField("mFillPaint");
            mFillPaintField.setAccessible(true);
            return (Paint) mFillPaintField.get(gradientDrawable);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            log.debug(TAG + ": Failed to get fill paint from GradientDrawable: " + e.getMessage());
            return null;
        }
    }

    /**
     * Gets the stroke paint from a GradientDrawable using reflection
     * @param gradientDrawable The GradientDrawable to extract the stroke paint from
     * @return The Paint object or null if reflection fails or gradientDrawable is null
     */
    public static Paint getStrokePaint(GradientDrawable gradientDrawable) {
        if (gradientDrawable == null) {
            return null;
        }

        try {
            Field mStrokePaintField = GradientDrawable.class.getDeclaredField("mStrokePaint");
            mStrokePaintField.setAccessible(true);
            return (Paint) mStrokePaintField.get(gradientDrawable);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            log.debug(TAG + ": Failed to get stroke paint from GradientDrawable: " + e.getMessage());
            return null;
        }
    }

    /**
     * Gets the LayoutNode from a SemanticsNode using reflection
     * @param semanticsNode The SemanticsNode to extract the LayoutNode from
     * @return The LayoutNode or null if reflection fails
     */
    @androidx.compose.ui.InternalComposeUiApi
    public static LayoutNode getLayoutNode(SemanticsNode semanticsNode) {
        try {
            Field layoutNodeField = SemanticsNode.class.getDeclaredField("layoutNode");
            layoutNodeField.setAccessible(true);
            return (LayoutNode) layoutNodeField.get(semanticsNode);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            log.debug(TAG + ": Failed to get LayoutNode from SemanticsNode: " + e.getMessage());
            return null;
        }
    }

    /**
     * Gets the Placeable from a LayoutNode using reflection
     * @param layoutNode The LayoutNode to extract the Placeable from
     * @return The Placeable or null if reflection fails
     */
    @androidx.compose.ui.InternalComposeUiApi
    public static Placeable getPlaceable(LayoutNode layoutNode) {
        try {
            Field innerLayerCoordinatorField = LayoutNode.class.getDeclaredField("_innerLayerCoordinator");
            innerLayerCoordinatorField.setAccessible(true);
            return (Placeable) innerLayerCoordinatorField.get(layoutNode);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            log.debug(TAG + ": Failed to get Placeable from LayoutNode: " + e.getMessage());
            return null;
        }
    }

    /**
     * Generic helper method to extract a padding field from a Compose PaddingElement Modifier
     * @param modifier The Modifier to extract padding from
     * @param fieldName The name of the padding field ("top", "bottom", "start", "end")
     * @return The padding value or 0 if reflection fails
     * @throws ClassNotFoundException if PaddingElement class is not found
     */
    private static float getPaddingField(Modifier modifier, String fieldName) throws ClassNotFoundException {
        Class<?> paddingClass = Class.forName("androidx.compose.foundation.layout.PaddingElement");
        try {
            Field field = paddingClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(modifier);
            return value != null ? (float) value : 0;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            log.debug(TAG + ": Failed to get padding field '" + fieldName + "' from PaddingElement: " + e.getMessage());
            return 0;
        } catch (ClassCastException e) {
            log.debug(TAG + ": Failed to cast padding field '" + fieldName + "' to float: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Gets the top padding from a Compose PaddingElement Modifier using reflection
     * @param modifier The Modifier to extract the top padding from
     * @return The top padding value or 0 if reflection fails
     * @throws ClassNotFoundException if PaddingElement class is not found
     */
    public static float getPaddingTop(Modifier modifier) throws ClassNotFoundException {
        return getPaddingField(modifier, "top");
    }

    /**
     * Gets the bottom padding from a Compose PaddingElement Modifier using reflection
     * @param modifier The Modifier to extract the bottom padding from
     * @return The bottom padding value or 0 if reflection fails
     * @throws ClassNotFoundException if PaddingElement class is not found
     */
    public static float getPaddingBottom(Modifier modifier) throws ClassNotFoundException {
        return getPaddingField(modifier, "bottom");
    }

    /**
     * Gets the start padding from a Compose PaddingElement Modifier using reflection
     * @param modifier The Modifier to extract the start padding from
     * @return The start padding value or 0 if reflection fails
     * @throws ClassNotFoundException if PaddingElement class is not found
     */
    public static float getPaddingStart(Modifier modifier) throws ClassNotFoundException {
        return getPaddingField(modifier, "start");
    }

    /**
     * Gets the end padding from a Compose PaddingElement Modifier using reflection
     * @param modifier The Modifier to extract the end padding from
     * @return The end padding value or 0 if reflection fails
     * @throws ClassNotFoundException if PaddingElement class is not found
     */
    public static float getPaddingEnd(Modifier modifier) throws ClassNotFoundException {
        return getPaddingField(modifier, "end");
    }

    // React Native BackgroundDrawable support
    // Supports multiple React Native versions:
    // 1. BackgroundDrawable (with backgroundColor field) - current/middle versions
    // 2. CSSBackgroundDrawable (with mColor field) - newer versions (0.74+)
    // 3. ReactViewBackgroundDrawable (with mColor field) - older versions
    // 4. CompositeBackgroundDrawable (wraps BackgroundDrawable) - various versions
    private static Class<?> rnBackgroundDrawableClass = null;
    private static Field rnBackgroundColorField = null;
    private static Class<?> rnCompositeBackgroundDrawableClass = null;
    private static Field rnCompositeBackgroundField = null;
    private static Class<?> rnCSSBackgroundDrawableClass = null;
    private static Field rnCSSBackgroundColorField = null;
    private static Class<?> rnReactViewBackgroundDrawableClass = null;
    private static Field rnReactViewBackgroundColorField = null;
    private static boolean rnReflectionInitialized = false;
    private static boolean rnReflectionFailed = false;

    /**
     * Initializes reflection for React Native's BackgroundDrawable classes.
     * This is called lazily on first use and cached for subsequent calls.
     * Supports multiple React Native versions by trying to load different drawable classes.
     */
    private static void initializeReactNativeReflection() {
        if (rnReflectionInitialized || rnReflectionFailed) {
            return;
        }

        boolean foundAtLeastOne = false;

        try {
            // Try to initialize CSSBackgroundDrawable reflection (newer React Native versions 0.74+)
            try {
                rnCSSBackgroundDrawableClass = Class.forName("com.facebook.react.uimanager.drawable.CSSBackgroundDrawable");
                rnCSSBackgroundColorField = rnCSSBackgroundDrawableClass.getDeclaredField("mColor");
                rnCSSBackgroundColorField.setAccessible(true);
                foundAtLeastOne = true;
            } catch (ClassNotFoundException e) {
                // CSSBackgroundDrawable not present - older React Native version
                log.debug(TAG + ": CSSBackgroundDrawable not found (not a newer React Native app)");
            } catch (NoSuchFieldException e) {
                // mColor field doesn't exist in CSSBackgroundDrawable
                log.warn(TAG + ": CSSBackgroundDrawable found but mColor field missing: " + e.getMessage());
            }

            // Try to initialize ReactViewBackgroundDrawable reflection (older React Native versions)
            try {
                rnReactViewBackgroundDrawableClass = Class.forName("com.facebook.react.views.view.ReactViewBackgroundDrawable");
                rnReactViewBackgroundColorField = rnReactViewBackgroundDrawableClass.getDeclaredField("mColor");
                rnReactViewBackgroundColorField.setAccessible(true);
                foundAtLeastOne = true;
            } catch (ClassNotFoundException e) {
                // ReactViewBackgroundDrawable not present - newer React Native version
                log.debug(TAG + ": ReactViewBackgroundDrawable not found (not an older React Native app)");
            } catch (NoSuchFieldException e) {
                // mColor field doesn't exist in ReactViewBackgroundDrawable
                log.warn(TAG + ": ReactViewBackgroundDrawable found but mColor field missing: " + e.getMessage());
            }

            // Try to initialize BackgroundDrawable reflection (middle React Native versions)
            try {
                rnBackgroundDrawableClass = Class.forName("com.facebook.react.uimanager.drawable.BackgroundDrawable");
                rnBackgroundColorField = rnBackgroundDrawableClass.getDeclaredField("backgroundColor");
                rnBackgroundColorField.setAccessible(true);
                foundAtLeastOne = true;
            } catch (ClassNotFoundException e) {
                // BackgroundDrawable not present in this React Native version
                log.debug(TAG + ": BackgroundDrawable not found (not a middle-version React Native app)");
            } catch (NoSuchFieldException e) {
                // backgroundColor field doesn't exist in BackgroundDrawable
                log.warn(TAG + ": BackgroundDrawable found but backgroundColor field missing: " + e.getMessage());
            }

            // Try to initialize CompositeBackgroundDrawable reflection (various versions)
            try {
                rnCompositeBackgroundDrawableClass = Class.forName("com.facebook.react.uimanager.drawable.CompositeBackgroundDrawable");
                rnCompositeBackgroundField = rnCompositeBackgroundDrawableClass.getDeclaredField("background");
                rnCompositeBackgroundField.setAccessible(true);
            } catch (ClassNotFoundException e) {
                // CompositeBackgroundDrawable might not exist in all React Native versions
                // This is fine, we'll just handle other BackgroundDrawable types
                log.debug(TAG + ": CompositeBackgroundDrawable not found (not used in this React Native version)");
            } catch (NoSuchFieldException e) {
                // background field doesn't exist in CompositeBackgroundDrawable
                log.warn(TAG + ": CompositeBackgroundDrawable found but background field missing: " + e.getMessage());
            }

            if (foundAtLeastOne) {
                rnReflectionInitialized = true;
                log.debug(TAG + ": React Native reflection initialized successfully");
            } else {
                // No React Native drawable classes found - not a React Native app
                rnReflectionFailed = true;
                log.debug(TAG + ": No React Native drawable classes found - not a React Native app");
            }
        } catch (Exception e) {
            // Any other reflection error
            log.error(TAG + ": Unexpected error during React Native reflection initialization: " + e.getMessage());
            rnReflectionFailed = true;
        }
    }

    /**
     * Checks if a drawable is a React Native background drawable.
     * Supports multiple React Native versions:
     * - CSSBackgroundDrawable (newer versions 0.74+)
     * - ReactViewBackgroundDrawable (older versions)
     * - BackgroundDrawable (middle versions)
     * - CompositeBackgroundDrawable (various versions)
     *
     * @param drawable The drawable to check
     * @return true if the drawable is a React Native background drawable, false otherwise
     */
    public static boolean isReactNativeBackgroundDrawable(android.graphics.drawable.Drawable drawable) {
        if (drawable == null) {
            return false;
        }

        initializeReactNativeReflection();

        if (!rnReflectionInitialized) {
            return false;
        }

        // Check for CompositeBackgroundDrawable (can wrap any of the other types)
        if (rnCompositeBackgroundDrawableClass != null &&
            rnCompositeBackgroundDrawableClass.isInstance(drawable)) {
            return true;
        }

        // Check for CSSBackgroundDrawable (newer React Native)
        if (rnCSSBackgroundDrawableClass != null &&
            rnCSSBackgroundDrawableClass.isInstance(drawable)) {
            return true;
        }

        // Check for ReactViewBackgroundDrawable (older React Native)
        if (rnReactViewBackgroundDrawableClass != null &&
            rnReactViewBackgroundDrawableClass.isInstance(drawable)) {
            return true;
        }

        // Check for BackgroundDrawable (middle React Native versions)
        if (rnBackgroundDrawableClass != null &&
            rnBackgroundDrawableClass.isInstance(drawable)) {
            return true;
        }

        return false;
    }

    /**
     * Extracts the background color from React Native's background drawable using reflection.
     * This handles React Native views which use custom BackgroundDrawable implementations.
     * Supports multiple React Native versions:
     * - CSSBackgroundDrawable (newer versions 0.74+) - extracts from 'mColor' field
     * - ReactViewBackgroundDrawable (older versions) - extracts from 'mColor' field
     * - BackgroundDrawable (middle versions) - extracts from 'backgroundColor' field
     * - CompositeBackgroundDrawable (various versions) - unwraps and extracts from wrapped drawable
     *
     * @param drawable The drawable to extract the color from (must be a React Native background drawable)
     * @return The background color as an Integer, or null if extraction fails or drawable is not a React Native drawable
     */
    public static Integer getReactNativeBackgroundColor(android.graphics.drawable.Drawable drawable) {
        if (drawable == null) {
            return null;
        }

        initializeReactNativeReflection();

        if (!rnReflectionInitialized) {
            return null;
        }

        try {
            // Check if it's a CompositeBackgroundDrawable first
            // CompositeBackgroundDrawable wraps another BackgroundDrawable in a "background" field
            if (rnCompositeBackgroundDrawableClass != null &&
                rnCompositeBackgroundField != null &&
                rnCompositeBackgroundDrawableClass.isInstance(drawable)) {

                // Get the wrapped BackgroundDrawable from the "background" field
                Object backgroundDrawable = rnCompositeBackgroundField.get(drawable);

                if (backgroundDrawable != null) {
                    // Recursively extract color from the wrapped drawable
                    return getReactNativeBackgroundColor((android.graphics.drawable.Drawable) backgroundDrawable);
                }
            }

            // Check if it's a CSSBackgroundDrawable (newer React Native 0.74+)
            if (rnCSSBackgroundDrawableClass != null &&
                rnCSSBackgroundColorField != null &&
                rnCSSBackgroundDrawableClass.isInstance(drawable)) {
                return rnCSSBackgroundColorField.getInt(drawable);
            }

            // Check if it's a ReactViewBackgroundDrawable (older React Native)
            if (rnReactViewBackgroundDrawableClass != null &&
                rnReactViewBackgroundColorField != null &&
                rnReactViewBackgroundDrawableClass.isInstance(drawable)) {
                return rnReactViewBackgroundColorField.getInt(drawable);
            }

            // Check if it's a BackgroundDrawable (middle React Native versions)
            if (rnBackgroundDrawableClass != null &&
                rnBackgroundColorField != null &&
                rnBackgroundDrawableClass.isInstance(drawable)) {
                return rnBackgroundColorField.getInt(drawable);
            }
        } catch (IllegalAccessException e) {
            log.debug(TAG + ": Failed to access React Native background color field: " + e.getMessage());
        } catch (Exception e) {
            log.error(TAG + ": Unexpected error extracting React Native background color: " + e.getMessage());
        }

        return null;
    }

    /**
     * Gets the Spannable text from a React Native TextView using reflection.
     * React Native's ReactTextView stores styled text in a private mSpanned field.
     *
     * @param textView The TextView to extract the Spannable from (must be ReactTextView)
     * @return The Spannable object or null if extraction fails
     */
    public static Spannable getReactNativeSpannable(android.widget.TextView textView) {
        if (textView == null) {
            return null;
        }

        try {
            // First try the public getSpanned() method if it exists
            try {
                java.lang.reflect.Method getSpannedMethod = textView.getClass().getMethod("getSpanned");
                Object result = getSpannedMethod.invoke(textView);
                if (result instanceof Spannable) {
                    return (Spannable) result;
                }
            } catch (NoSuchMethodException e) {
                // Method doesn't exist, fall through to field access
                log.debug(TAG + ": getSpanned() method not found, trying field access");
            }

            // Try to access the private mSpanned field
            java.lang.reflect.Field mSpannedField = textView.getClass().getDeclaredField("mSpanned");
            mSpannedField.setAccessible(true);
            Object spannedObject = mSpannedField.get(textView);

            if (spannedObject instanceof Spannable) {
                return (Spannable) spannedObject;
            }
        } catch (NoSuchFieldException e) {
            log.debug(TAG + ": mSpanned field not found in TextView (not a ReactTextView): " + e.getMessage());
        } catch (IllegalAccessException e) {
            log.debug(TAG + ": Cannot access mSpanned field: " + e.getMessage());
        } catch (Exception e) {
            log.error(TAG + ": Unexpected error getting React Native Spannable: " + e.getMessage());
        }

        return null;
    }

}