package com.newrelic.agent.android.sessionReplay;

import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import com.newrelic.agent.android.sessionReplay.internal.ViewBackgroundHelper;

import java.util.Objects;

public class ViewDetails {
    public final int viewId;
    public final Rect frame;
    public final String backgroundColor;
    public final float alpha;
    public final boolean isHidden;
    public final Drawable backgroundDrawable;
    public final int parentId;
    public final String viewName;
    public final float density;

    public String getCssSelector() {
        return this.viewName + "-" + this.viewId;
    }

    public boolean isVisible() {
        // Equivalent of Swift's frame != .zero requires checking Rect's fields
        return !isHidden && alpha > 0 && (frame.width() > 0 || frame.height() > 0);
    }

    public boolean isClear() {
        // In Android, alpha is typically 0.0 to 1.0
        return alpha <= 1.0f;
    }

    public ViewDetails(View view) {
        // Getting the global visible rectangle of the view
        this.density = view.getContext().getResources().getDisplayMetrics().density;
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        float x = location[0] / density;
        float y = location[1] / density;
        float width = view.getWidth() / density;
        float height = view.getHeight() / density;
        this.frame = new Rect((int) x, (int) y, (int) ((int) x + width), (int) ((int) y + height));

        this.backgroundColor = ViewBackgroundHelper.getBackgroundColor(view);

        this.backgroundDrawable = view.getBackground();

        this.alpha = view.getAlpha();

        // View.GONE and View.INVISIBLE are considered hidden in a session replay context
        this.isHidden = view.getVisibility() == View.GONE || view.getVisibility() == View.INVISIBLE;

        this.viewName = view.getClass().getSimpleName(); // Gets the simple class name

        viewId = getStableId(view);

        if (view.getParent() instanceof ViewGroup) {
            // If the parent is a ViewGroup, we can get its ID
            ViewGroup parent = (ViewGroup) view.getParent();
            parentId = getStableId(parent);
        } else {
            // If the parent is not a ViewGroup, we can still get its ID
            parentId = 0;
        }
    }

    public String getCSSSelector() {
        return this.viewName + "-" + this.viewId;
    }

    public String generateCssDescription() {
        StringBuilder cssString = new StringBuilder();
        String cssSelector = getCSSSelector();

        cssString.append(" #")
                .append(cssSelector)
                .append(" {")
                .append(" ")
                .append(generateInlineCSS());

        return cssString.toString();
    }
    
    public String generateInlineCSS() {
        StringBuilder cssString = new StringBuilder();
        cssString.append(generatePositionCss())
                .append(" ")
                .append(generateBackgroundColorCss());

        return cssString.toString();
    }

    private StringBuilder generatePositionCss() {
        StringBuilder positionStringBuilder = new StringBuilder();

        positionStringBuilder.append("position: fixed;")
                .append("left: ")
                .append(frame.left)
                .append("px;")
                .append("top: ")
                .append(frame.top)
                .append("px;")
                .append("width: ")
                .append(frame.width())
                .append("px;")
                .append("height: ")
                .append(frame.height())
                .append("px;");

        return positionStringBuilder;
    }

    private String generateBackgroundColorCss() {
        StringBuilder backgroundColorStringBuilder = new StringBuilder();
        if (!backgroundColor.isEmpty()) {
            backgroundColorStringBuilder.append("background-color: ")
                    .append(backgroundColor)
                    .append(";");
        }
        // Assuming backgroundDrawable is a Drawable that can be converted to a color
        if (backgroundDrawable != null) {
            // Convert the drawable to a color string (this is a placeholder, actual implementation may vary)
            if(backgroundDrawable instanceof GradientDrawable) {
                ViewBackgroundHelper.getBackGroundFromDrawable(backgroundColorStringBuilder,(GradientDrawable) backgroundDrawable,density);
            }
        }
        return backgroundColorStringBuilder.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ViewDetails that = (ViewDetails) o;
        return viewId == that.viewId &&
                Float.compare(that.alpha, alpha) == 0 &&
                isHidden == that.isHidden &&
                Objects.equals(frame, that.frame) &&
                Objects.equals(backgroundColor, that.backgroundColor) &&
                Objects.equals(viewName, that.viewName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(viewId, frame, backgroundColor, alpha, isHidden, viewName);
    }

    private int getStableId(View view) {
        int keyCode = "NewRelicSessionReplayViewId".hashCode();
        Integer idValue = null;
        idValue = (Integer) view.getTag(keyCode);
        if(idValue == null) {
            idValue = com.newrelic.agent.android.sessionReplay.NewRelicIdGenerator.generateId();
            view.setTag(keyCode, idValue);
        }
        int id = idValue;
        return id;
    }
}

