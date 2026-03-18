package com.newrelic.agent.android.sessionReplay;

import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect; // Equivalent to CGRect for basic representation
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View; // Use Android's View class
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import com.newrelic.agent.android.sessionReplay.internal.ViewBackgroundHelper;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Objects; // For hashCode and equals

public class ViewDetails {
    public final int viewId;
    public Rect frame; // Changed from final to support testing
    public final String backgroundColor;
    public final float alpha;
    public final boolean isHidden;
    public final Drawable backgroundDrawable;
    public final int parentId;
    public final String viewName;
    public final float density;
    public final float elevation;
    public final float outlineRadius;
    public final boolean clipsContent;


    // Computed property: cssSelector
    public String getCssSelector() {
        return this.viewName + "-" + this.viewId;
    }

    // Computed property: isVisible
    public boolean isVisible() {
        // Equivalent of Swift's frame != .zero requires checking Rect's fields
        return !isHidden && alpha > 0 && (frame.width() > 0 || frame.height() > 0);
    }

    // Computed property: isClear
    public boolean isClear() {
        // In Android, alpha is typically 0.0 to 1.0
        return alpha <= 1.0f;
    }

    // Equivalent of Swift's init(view: UIView)
    public ViewDetails(View view) {
        // Getting the global visible rectangle of the view
        this.density = view.getContext().getResources().getDisplayMetrics().density;
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        float x = location[0] / density;
        float y = location[1] / density;
        float width = view.getWidth() / density;
        float height = view.getHeight() / density;
        this.frame = new Rect((int) x, (int) y, (int) ((int)  x+width), (int) ((int) y+height));



        this.backgroundColor = ViewBackgroundHelper.getBackgroundColor(view);

        this.backgroundDrawable = view.getBackground();

        this.alpha = view.getAlpha();

        // Elevation produces shadows in Android's Material Design rendering.
        // Convert from Android px to CSS px (divide by density).
        this.elevation = view.getElevation() / density;

        // Extract corner radius from the view's outline. This handles
        // Material dialogs, CardViews, and any view using outline-based
        // clipping — without hardcoding radius values per view type.
        this.outlineRadius = getOutlineRadius(view);

        // ViewGroups clip children by default (clipChildren=true). When a
        // container clips, its CSS equivalent is overflow: hidden.
        this.clipsContent = (view instanceof ViewGroup) && ((ViewGroup) view).getClipChildren();

        // View.GONE and View.INVISIBLE are considered hidden in a session replay context
        this.isHidden = view.getVisibility() == View.GONE || view.getVisibility() == View.INVISIBLE;

        // Equivalent of Swift's String(describing: type(of: view))
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

    // Getters for the final properties
    public int getViewId() {
        return viewId;
    }

    public Rect getFrame() {
        return frame;
    }

    public String getBackgroundColor() {
        return backgroundColor;
    }

    public float getAlpha() {
        return alpha;
    }

    public boolean isHidden() {
        return isHidden;
    }

    public String getViewName() {
        return viewName;
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
                .append(generateBackgroundColorCss())
                .append(generateElevationCss())
                .append(clipsContent ? " overflow: hidden;" : "");

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
        // If GradientDrawable didn't produce a border-radius, fall back to
        // the outline radius. This handles Material dialogs, CardViews with
        // non-GradientDrawable backgrounds, and any outline-clipped view.
        if (outlineRadius > 0 && backgroundColorStringBuilder.indexOf("border-radius") == -1) {
            backgroundColorStringBuilder.append(String.format(Locale.US, " border-radius: %.1fpx;", outlineRadius));
        }
        return backgroundColorStringBuilder.toString();
    }

    /**
     * Converts Android elevation to a CSS box-shadow approximation.
     * Android elevation creates both an ambient shadow (all around) and a key light
     * shadow (below). This approximates with a single box-shadow using the elevation
     * value to control blur radius and vertical offset.
     */
    private String generateElevationCss() {
        if (elevation <= 0) {
            return "";
        }
        // Approximate Material Design shadow:
        // - Horizontal offset: 0
        // - Vertical offset: half the elevation (key light from above)
        // - Blur radius: elevation value
        // - Color: semi-transparent black (Material Design ambient + key light blend)
        return String.format(Locale.US, " box-shadow: 0px %.1fpx %.1fpx rgba(0, 0, 0, 0.24);",
                elevation * 0.5f, elevation);
    }

    // Implementing hashCode and equals for equivalence to Swift's implicit Hashable for structs with Hashable members
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

    /**
     * Extracts the corner radius from a view's outline provider.
     * Works for Material dialogs, CardViews, and any view that uses an
     * outline-based clip. Returns 0 if no radius is available.
     */
    private float getOutlineRadius(View view) {
        try {
            ViewOutlineProvider provider = view.getOutlineProvider();
            if (provider == null) {
                return 0;
            }
            Outline outline = new Outline();
            provider.getOutline(view, outline);
            float radius = outline.getRadius();
            if (radius > 0) {
                return radius / density;
            }
        } catch (Exception e) {
            // OutlineProvider.getOutline can throw if the view isn't laid out
        }
        return 0;
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

