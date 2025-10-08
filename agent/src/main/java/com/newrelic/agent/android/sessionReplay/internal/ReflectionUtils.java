package com.newrelic.agent.android.sessionReplay.internal;

import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;

import androidx.compose.ui.Modifier;
import androidx.compose.ui.layout.Placeable;
import androidx.compose.ui.node.LayoutNode;
import androidx.compose.ui.semantics.SemanticsNode;

import java.lang.reflect.Field;

/**
 * Utility class for reflection-based operations
 * Used to access private/internal fields and methods from Android and Compose UI classes
 */
public class ReflectionUtils {

    /**
     * Gets the fill paint from a GradientDrawable using reflection
     * @param gradientDrawable The GradientDrawable to extract the fill paint from
     * @return The Paint object or null if reflection fails
     */
    public static Paint getFillPaint(GradientDrawable gradientDrawable) {
        try {
            Field mFillPaintField = GradientDrawable.class.getDeclaredField("mFillPaint");
            mFillPaintField.setAccessible(true);
            return (Paint) mFillPaintField.get(gradientDrawable);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Gets the stroke paint from a GradientDrawable using reflection
     * @param gradientDrawable The GradientDrawable to extract the stroke paint from
     * @return The Paint object or null if reflection fails
     */
    public static Paint getStrokePaint(GradientDrawable gradientDrawable) {
        try {
            Field mStrokePaintField = GradientDrawable.class.getDeclaredField("mStrokePaint");
            mStrokePaintField.setAccessible(true);
            return (Paint) mStrokePaintField.get(gradientDrawable);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
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
            e.printStackTrace();
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
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Gets the top padding from a Compose PaddingElement Modifier using reflection
     * @param modifier The Modifier to extract the top padding from
     * @return The top padding value or 0 if reflection fails
     * @throws ClassNotFoundException if PaddingElement class is not found
     */
    public static float getPaddingTop(Modifier modifier) throws ClassNotFoundException {
        Class<?> paddingClass = Class.forName("androidx.compose.foundation.layout.PaddingElement");
        try {
            Field topField = paddingClass.getDeclaredField("top");
            topField.setAccessible(true);
            return (float) topField.get(modifier);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Gets the bottom padding from a Compose PaddingElement Modifier using reflection
     * @param modifier The Modifier to extract the bottom padding from
     * @return The bottom padding value or 0 if reflection fails
     * @throws ClassNotFoundException if PaddingElement class is not found
     */
    public static float getPaddingBottom(Modifier modifier) throws ClassNotFoundException {
        Class<?> paddingClass = Class.forName("androidx.compose.foundation.layout.PaddingElement");
        try {
            Field bottomField = paddingClass.getDeclaredField("bottom");
            bottomField.setAccessible(true);
            return (float) bottomField.get(modifier);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Gets the start padding from a Compose PaddingElement Modifier using reflection
     * @param modifier The Modifier to extract the start padding from
     * @return The start padding value or 0 if reflection fails
     * @throws ClassNotFoundException if PaddingElement class is not found
     */
    public static float getPaddingStart(Modifier modifier) throws ClassNotFoundException {
        Class<?> paddingClass = Class.forName("androidx.compose.foundation.layout.PaddingElement");
        try {
            Field startField = paddingClass.getDeclaredField("start");
            startField.setAccessible(true);
            return (float) startField.get(modifier);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Gets the end padding from a Compose PaddingElement Modifier using reflection
     * @param modifier The Modifier to extract the end padding from
     * @return The end padding value or 0 if reflection fails
     * @throws ClassNotFoundException if PaddingElement class is not found
     */
    public static float getPaddingEnd(Modifier modifier) throws ClassNotFoundException {
        Class<?> paddingClass = Class.forName("androidx.compose.foundation.layout.PaddingElement");
        try {
            Field endField = paddingClass.getDeclaredField("end");
            endField.setAccessible(true);
            return (float) endField.get(modifier);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
            return 0;
        }
    }
}