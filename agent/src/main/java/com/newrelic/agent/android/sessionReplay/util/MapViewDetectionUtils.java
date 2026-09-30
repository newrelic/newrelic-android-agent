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
 * Thread Safety: This class is designed to be thread-safe for concurrent read access.
 * All detection methods are stateless and use immutable static arrays for pattern matching.
 * However, the reflection-based operations (especially for Compose interop) may not be
 * fully thread-safe depending on the underlying JVM implementation. For maximum safety,
 * consider synchronizing calls to isMapView() methods in multi-threaded environments.
 *
 * Performance Considerations:
 * - Class-based detection is O(1) for exact matches, O(n) for pattern matching
 * - Hierarchy traversal is O(h) where h is the inheritance depth (max 50)
 * - Reflection operations have higher overhead and should be used sparingly
 * - Results are not cached, so frequent calls with same inputs will repeat work
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

    // Highly specific keywords that strongly indicate map content
    // These are much more restrictive to avoid false positives with common UI text
    private static final String[] MAP_SEMANTIC_KEYWORDS = {
        "mapview", "googlemap", "mapfragment", "mapwidget", "geoview",
        "coordinate", "latitude", "longitude", "geolocation", "cartographic",
        "satellite view", "terrain view", "map marker", "map pin", "map zoom"
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
            java.util.Set<Class<?>> visitedClasses = new java.util.HashSet<>(); // Prevent circular references

            while (currentClass != null && !currentClass.equals(Object.class)
                   && hierarchyDepth < MAX_HIERARCHY_DEPTH
                   && !visitedClasses.contains(currentClass)) {

                // Add to visited set to prevent infinite loops with circular references
                visitedClasses.add(currentClass);

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

                try {
                    Class<?> nextClass = currentClass.getSuperclass();
                    if (nextClass == currentClass) {
                        // Defensive check: if getSuperclass returns same class, break to prevent infinite loop
                        break;
                    }
                    currentClass = nextClass;
                } catch (Exception e) {
                    // Handle any unexpected exceptions during class hierarchy traversal
                    break;
                }

                hierarchyDepth++;
            }


        } catch (Exception e) {
            // Silently handle errors - class analysis should not fail the detection
        }

        return false;
    }

    /**
     * MapView detection for traditional Android Views.
     * Prioritizes class-based detection to avoid false positives from semantic matching.
     *
     * @param view The View to check
     * @return true if the view is identified as a MapView, false otherwise
     */
    public static boolean isMapView(View view) {
        if (view == null) {
            return false;
        }

        // STEP 1: Class-based detection (authoritative - trust this completely)
        if (isMapViewByClass(view.getClass())) {
            return true;
        }

        // STEP 2: Only check semantics for non-text views to avoid false positives
        // Skip semantic checks for TextView, EditText, ImageView to prevent common UI from being misclassified
        if (isTextOrImageView(view)) {
            return false;
        }

        // STEP 3: Highly constrained semantic analysis (secondary evidence only)
        if (checkViewSemanticsConstrained(view)) {
            return true;
        }

        return false;
    }

    /**
     * MapView detection for Jetpack Compose SemanticsNode.
     * Prioritizes AndroidView interop detection to avoid false positives from semantic matching.
     *
     * @param semanticsNode The SemanticsNode to check
     * @return true if the node represents a MapView, false otherwise
     */
    public static boolean isMapView(SemanticsNode semanticsNode) {
        if (semanticsNode == null) {
            return false;
        }

        // STEP 1: AndroidView interop detection (authoritative - trust this completely)
        if (checkAndroidViewInterop(semanticsNode)) {
            return true;
        }

        // STEP 2: Skip semantic checks for nodes with text content to avoid false positives
        if (hasTextContent(semanticsNode)) {
            return false;
        }

        // STEP 3: Highly constrained semantic analysis (secondary evidence only)
        if (checkMapSemanticsConstrained(semanticsNode)) {
            return true;
        }

        return false;
    }

    /**
     * Checks if a View is a TextView, EditText, or ImageView that should be excluded from semantic analysis.
     * These views commonly contain text like "Location", "Map", etc. that would cause false positives.
     *
     * @param view The View to check
     * @return true if this is a text or image view that should skip semantic analysis
     */
    private static boolean isTextOrImageView(View view) {
        return view instanceof android.widget.TextView ||
               view instanceof android.widget.ImageView;
    }

    /**
     * Checks if a Compose SemanticsNode has text content that would likely cause false positives.
     *
     * @param semanticsNode The SemanticsNode to check
     * @return true if this node has text content and should skip semantic analysis
     */
    private static boolean hasTextContent(SemanticsNode semanticsNode) {
        try {
            // Check if it has text content
            if (semanticsNode.getConfig().contains(SemanticsProperties.INSTANCE.getText())) {
                return true;
            }

            // Check if it has editable text content
            if (semanticsNode.getConfig().contains(SemanticsProperties.INSTANCE.getEditableText())) {
                return true;
            }

            // Check if it has content description that might contain common words
            if (semanticsNode.getConfig().contains(SemanticsProperties.INSTANCE.getContentDescription())) {
                return true;
            }
        } catch (Exception e) {
            // If we can't determine, err on the side of caution and skip semantic analysis
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
            // Silently handle interop errors - should not prevent other detection methods
        }

        return false;
    }

    /**
     * Highly constrained semantic analysis for Compose nodes.
     * Only applies to non-text nodes and requires very specific keywords to avoid false positives.
     *
     * @param semanticsNode The SemanticsNode to analyze
     * @return true if semantic properties strongly suggest this is map content, false otherwise
     */
    private static boolean checkMapSemanticsConstrained(SemanticsNode semanticsNode) {
        try {
            // Only check very specific semantic properties that are unlikely to cause false positives
            // This method should only be called for non-text nodes

            // Check for test tags or resource IDs that might indicate maps
            // This is more reliable than content descriptions or text content
            String testTag = getTestTag(semanticsNode);
            if (testTag != null && containsMapKeywordsStrict(testTag)) {
                return true;
            }

            // Future enhancements could include:
            // - Role-based detection for specific map roles
            // - Custom semantic properties set by mapping libraries
            // - Other non-textual semantic hints

        } catch (Exception e) {
            // Silently handle semantic analysis errors
        }

        return false;
    }

    /**
     * Attempts to get a test tag from a SemanticsNode using reflection.
     * Test tags are more reliable indicators than content descriptions.
     *
     * @param semanticsNode The SemanticsNode to check
     * @return The test tag string if available, null otherwise
     */
    private static String getTestTag(SemanticsNode semanticsNode) {
        try {
            // Try to get test tag using SemanticsProperties
            if (semanticsNode.getConfig().contains(SemanticsProperties.INSTANCE.getTestTag())) {
                return semanticsNode.getConfig().get(SemanticsProperties.INSTANCE.getTestTag());
            }
        } catch (Exception e) {
            // Ignore errors accessing test tags
        }
        return null;
    }

    /**
     * Highly constrained semantic analysis for traditional Android Views.
     * Only applies to non-text/image views and requires very specific indicators to avoid false positives.
     *
     * @param view The View to analyze semantically
     * @return true if semantic properties strongly suggest this is map content, false otherwise
     */
    private static boolean checkViewSemanticsConstrained(View view) {
        try {
            // Only check very specific semantic properties that are unlikely to cause false positives
            // This method should only be called for non-text/image views

            // Check resource name hints (more reliable than content descriptions)
            try {
                int id = view.getId();
                if (id != View.NO_ID) {
                    String resourceName = view.getResources().getResourceEntryName(id);
                    if (resourceName != null && containsMapKeywordsStrict(resourceName)) {
                        return true;
                    }
                }
            } catch (android.content.res.Resources.NotFoundException e) {
                // Resource ID not found - this is normal for programmatically created views
            } catch (Exception e) {
                // Other unexpected exceptions during resource access
            }

            // Check tag for very specific map-related hints (not general keywords)
            Object tag = view.getTag();
            if (tag instanceof String && containsMapKeywordsStrict((String) tag)) {
                return true;
            }

        } catch (Exception e) {
            // Silently handle semantic analysis errors
        }

        return false;
    }

    /**
     * Checks if a text string contains highly specific map-related keywords.
     * Uses strict matching to avoid false positives with common UI text.
     *
     * @param text The text to analyze
     * @return true if the text contains very specific map-related keywords, false otherwise
     */
    private static boolean containsMapKeywordsStrict(String text) {
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