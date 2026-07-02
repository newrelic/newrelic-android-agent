package com.newrelic.agent.android.sessionReplay.util;

import android.view.View;

import androidx.compose.ui.semantics.SemanticsNode;
import androidx.compose.ui.semantics.SemanticsProperties;

import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.sessionReplay.internal.ReflectionUtils;

import java.util.List;

/**
 * Centralized utility class for MapView detection across different UI frameworks.
 * This class eliminates code duplication by providing a single source of truth
 * for MapView identification logic.
 *
 * Thread Safety: This class is thread-safe for concurrent read access. All detection
 * methods are stateless and use immutable static arrays for pattern matching.
 */
public class MapViewDetectionUtils {
    private static final AgentLog log = AgentLogManager.getAgentLog();

    // Known MapView class names - ordered by likelihood for performance
    private static final String GOOGLE_MAPS_MAPVIEW = "com.google.android.gms.maps.MapView";
    private static final String GOOGLE_MAPS_SUPPORT_MAPVIEW = "com.google.android.gms.maps.SupportMapFragment";

    // Exact class name matches (fastest detection)
    private static final String[] EXACT_MAPVIEW_CLASSES = {
        "com.google.android.gms.maps.MapView",
        "com.google.android.gms.maps.SupportMapFragment",
        "com.google.android.gms.maps.MapFragment",
        "org.osmdroid.views.MapView",  // OSMDroid
        "com.mapbox.maps.MapView",     // Mapbox
        "com.here.sdk.mapview.MapView", // HERE Maps
        "com.baidu.mapapi.map.MapView", // Baidu Maps
        "com.amap.api.maps.MapView"     // AutoNavi/Gaode Maps
    };

    // Pattern matches (more flexible but slower)
    // NOTE: Removed "NavigationView" - too broad, matches androidx.navigation.ui.NavigationView
    // Pre-trimmed patterns for performance - no need to trim on every check
    private static final String[] MAPVIEW_CLASS_PATTERNS = {
        "MapView", "mapview", "GoogleMap", "OSMMapView", "MapFragment",
        "mapfragment", "MapWidget", "GeoView", "TileView"
    };

    // Keywords that might indicate map content in semantic descriptions
    private static final String[] MAP_SEMANTIC_KEYWORDS = {
        "map", "navigation", "location", "geographic", "gps", "route", "directions",
        "satellite", "terrain", "street", "coordinate", "latitude", "longitude",
        "zoom", "pan", "marker", "pin", "waypoint", "geolocation", "cartographic"
    };

    /**
     * Core MapView detection logic based on class hierarchy analysis.
     * Uses optimized detection order: exact matches -> patterns -> hierarchy traversal.
     * This method uses reflection to avoid hard dependencies on mapping SDKs.
     *
     * @param viewClass The class to check for MapView characteristics
     * @return true if the class appears to be a MapView, false otherwise
     */
    public static boolean isMapViewByClass(Class<?> viewClass) {
        if (viewClass == null) {
            return false;
        }

        try {
            String className = viewClass.getName();
            if (className == null || className.trim().isEmpty()) {
                return false;
            }

            // STEP 1: Fast exact class name matches (O(1) for most common cases)
            for (String exactClass : EXACT_MAPVIEW_CLASSES) {
                if (exactClass != null && exactClass.equals(className)) {
                    return true;
                }
            }

            // STEP 2: Pattern matching for flexible detection
            // Patterns are pre-validated and trimmed, so no need for null/empty checks
            for (String pattern : MAPVIEW_CLASS_PATTERNS) {
                if (className.contains(pattern)) {
                    return true;
                }
            }

            // STEP 3: Inheritance hierarchy traversal (most expensive, done last)
            Class<?> currentClass = viewClass;
            int hierarchyDepth = 0;
            final int MAX_HIERARCHY_DEPTH = 50; // Prevent infinite loops

            while (currentClass != null && !currentClass.equals(Object.class) && hierarchyDepth < MAX_HIERARCHY_DEPTH) {
                String superClassName = currentClass.getName();
                if (superClassName == null || superClassName.trim().isEmpty()) {
                    break; // Safety check
                }

                // Check exact matches in hierarchy
                for (String exactClass : EXACT_MAPVIEW_CLASSES) {
                    if (exactClass != null && exactClass.equals(superClassName)) {
                        return true;
                    }
                }

                // Check patterns in hierarchy
                // Patterns are pre-validated, so no need for null/empty checks
                for (String pattern : MAPVIEW_CLASS_PATTERNS) {
                    if (superClassName.contains(pattern)) {
                        return true;
                    }
                }

                currentClass = currentClass.getSuperclass();
                hierarchyDepth++;
            }


        } catch (Exception e) {
            log.debug("Error during MapView class analysis: " + e.getMessage());
        }

        return false;
    }

    /**
     * MapView detection for traditional Android Views with unified semantic analysis.
     * Now provides the same comprehensive detection as Compose views.
     *
     * @param view The View to check
     * @return true if the view is identified as a MapView, false otherwise
     */
    public static boolean isMapView(View view) {
        if (view == null) {
            return false;
        }

        // STEP 1: Class-based detection (fast path)
        if (isMapViewByClass(view.getClass())) {
            return true;
        }

        // STEP 2: Semantic analysis (same as Compose views now)
        if (checkViewSemantics(view)) {
            return true;
        }

        // STEP 3: Content analysis for custom implementations
        if (checkViewContent(view)) {
            return true;
        }

        return false;
    }

    /**
     * MapView detection for Jetpack Compose SemanticsNode.
     * Uses the same unified detection strategy as traditional Views for consistency.
     *
     * @param semanticsNode The SemanticsNode to check
     * @return true if the node represents a MapView, false otherwise
     */
    public static boolean isMapView(SemanticsNode semanticsNode) {
        if (semanticsNode == null) {
            return false;
        }

        // STEP 1: AndroidView interop detection (Compose-specific)
        if (checkAndroidViewInterop(semanticsNode)) {
            return true;
        }

        // STEP 2: Semantic properties analysis (unified with traditional Views)
        if (checkMapSemantics(semanticsNode)) {
            return true;
        }

        return false;
    }

    /**
     * Checks if a Compose SemanticsNode contains an AndroidView with a MapView.
     * Uses reflection to access the interop view without hard dependencies.
     *
     * @param semanticsNode The SemanticsNode to check
     * @return true if an AndroidView MapView is detected, false otherwise
     */
    private static boolean checkAndroidViewInterop(SemanticsNode semanticsNode) {
        try {
            // Get LayoutNode using reflection utility
            Object layoutNode = ReflectionUtils.getLayoutNode(semanticsNode);

            if (layoutNode != null) {
                // Try to get the interop view
                Object interopView = ReflectionUtils.getInteropView(layoutNode);

                if (interopView instanceof View) {
                    View view = (View) interopView;
                    Class<?> viewClass = view.getClass();
                    if (viewClass != null && isMapViewByClass(viewClass)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Error checking AndroidView interop for MapView: " + e.getMessage());
        }

        return false;
    }

    /**
     * Analyzes semantic properties to detect map-related content.
     * Uses the same unified keyword detection as traditional Views.
     *
     * @param semanticsNode The SemanticsNode to analyze
     * @return true if semantic properties suggest this is map content, false otherwise
     */
    private static boolean checkMapSemantics(SemanticsNode semanticsNode) {
        try {
            // Check content description for map-related keywords
            if (semanticsNode.getConfig().contains(SemanticsProperties.INSTANCE.getContentDescription())) {
                List<String> contentDescriptions = semanticsNode.getConfig()
                    .get(SemanticsProperties.INSTANCE.getContentDescription());

                if (contentDescriptions != null) {
                    for (String description : contentDescriptions) {
                        if (containsMapKeywords(description)) {
                            return true;
                        }
                    }
                }
            }

            // Check text content if available
            if (semanticsNode.getConfig().contains(SemanticsProperties.INSTANCE.getText())) {
                List<androidx.compose.ui.text.AnnotatedString> textList = semanticsNode.getConfig()
                    .get(SemanticsProperties.INSTANCE.getText());

                if (textList != null) {
                    for (androidx.compose.ui.text.AnnotatedString annotatedString : textList) {
                        String text = annotatedString.getText();
                        if (containsMapKeywords(text)) {
                            return true;
                        }
                    }
                }
            }

            // Check editable text content if available
            if (semanticsNode.getConfig().contains(SemanticsProperties.INSTANCE.getEditableText())) {
                androidx.compose.ui.text.AnnotatedString editableText = semanticsNode.getConfig()
                    .get(SemanticsProperties.INSTANCE.getEditableText());

                if (editableText != null) {
                    String text = editableText.getText();
                    if (containsMapKeywords(text)) {
                        return true;
                    }
                }
            }

            // Future enhancements could include:
            // - Role-based detection
            // - Custom semantic properties
            // - Test tags or other identifiers
            // - State descriptions

        } catch (Exception e) {
            log.debug("Error checking semantic properties for MapView: " + e.getMessage());
        }

        return false;
    }

    /**
     * Performs semantic analysis on traditional Android Views.
     * This brings traditional views up to parity with Compose semantic detection.
     *
     * @param view The View to analyze semantically
     * @return true if semantic properties suggest this is map content, false otherwise
     */
    private static boolean checkViewSemantics(View view) {
        try {
            // Check content description (accessibility)
            CharSequence contentDescription = view.getContentDescription();
            if (contentDescription != null && containsMapKeywords(contentDescription.toString())) {
                return true;
            }

            // Check tag for semantic hints
            Object tag = view.getTag();
            if (tag instanceof String && containsMapKeywords((String) tag)) {
                return true;
            }

            // Check for resource name hints (if available)
            try {
                int id = view.getId();
                if (id != View.NO_ID) {
                    String resourceName = view.getResources().getResourceEntryName(id);
                    if (resourceName != null && containsMapKeywords(resourceName)) {
                        return true;
                    }
                }
            } catch (android.content.res.Resources.NotFoundException e) {
                // Resource ID not found - this is normal for programmatically created views
            } catch (Exception e) {
                // Other unexpected exceptions during resource access
            }

        } catch (Exception e) {
            log.debug("Error during traditional View semantic analysis: " + e.getMessage());
        }

        return false;
    }

    /**
     * Performs content analysis on Views that might contain map-related content.
     * This checks for textual hints that might indicate map functionality.
     *
     * @param view The View to analyze
     * @return true if content suggests this is map-related, false otherwise
     */
    private static boolean checkViewContent(View view) {
        try {
            // For TextViews, check the actual text content
            if (view instanceof android.widget.TextView) {
                android.widget.TextView textView = (android.widget.TextView) view;
                CharSequence text = textView.getText();
                if (text != null && containsMapKeywords(text.toString())) {
                    return true;
                }

                // Check hint text as well
                CharSequence hint = textView.getHint();
                if (hint != null && containsMapKeywords(hint.toString())) {
                    return true;
                }
            }

            // For ImageViews, check content description for map-related descriptions
            if (view instanceof android.widget.ImageView) {
                CharSequence contentDesc = view.getContentDescription();
                if (contentDesc != null && containsMapKeywords(contentDesc.toString())) {
                    return true;
                }
            }

        } catch (Exception e) {
            log.debug("Error during View content analysis: " + e.getMessage());
        }

        return false;
    }

    /**
     * Checks if a text string contains map-related keywords.
     * Uses case-insensitive matching for robust detection.
     *
     * @param text The text to analyze
     * @return true if the text contains map-related keywords, false otherwise
     */
    private static boolean containsMapKeywords(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        String lowerText = text.toLowerCase().trim();

        for (String keyword : MAP_SEMANTIC_KEYWORDS) {
            if (lowerText.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Creates a performance-optimized preview string for logging.
     * Uses StringBuilder to avoid multiple string concatenations.
     *
     * @param text The text to create a preview for
     * @return A safe preview string for logging
     */
    private static String createPreviewString(String text) {
        if (text == null || text.length() == 0) {
            return "[empty]";
        }

        if (text.length() <= 50) {
            return text;
        }

        // Use StringBuilder for efficient string building
        StringBuilder preview = new StringBuilder(54);
        preview.append(text, 0, 50);
        preview.append("...");
        return preview.toString();
    }

    /**
     * Adds a custom MapView class pattern for detection.
     * This allows applications to register custom MapView implementations.
     *
     * @param classNamePattern The class name pattern to recognize as a MapView
     * @throws IllegalArgumentException if classNamePattern is null, empty, or invalid
     */
    public static void addCustomMapViewPattern(String classNamePattern) {
        // Validate input parameter
        if (classNamePattern == null) {
            throw new IllegalArgumentException("Class name pattern cannot be null");
        }

        String trimmedPattern = classNamePattern.trim();
        if (trimmedPattern.isEmpty()) {
            throw new IllegalArgumentException("Class name pattern cannot be empty or whitespace-only");
        }

        // Additional validation for potentially unsafe patterns
        if (trimmedPattern.length() > 500) {
            throw new IllegalArgumentException("Class name pattern too long (max 500 characters): " + trimmedPattern.length());
        }

        // Note: In a full implementation, this would add to a dynamic list
        // For now, this is a placeholder for future extensibility
    }

}