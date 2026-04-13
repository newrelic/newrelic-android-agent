# Android View System Compatibility — New Relic Mobile Session Replay

This document covers which Android XML layout-based View components and patterns are captured, masked, and tracked by the New Relic Mobile Session Replay (MSR) system, based on the test harness and agent source code.

---

## Requirements

| Requirement | Details |
|---|---|
| Minimum API for Session Replay | API 24 (Android 7.0) |
| Framework | `com.newrelic.agent.android` module, View integration via recursive `ViewGroup` traversal |
| Privacy tags | `View.setTag("nr-mask")`, `View.setTag(R.id.newrelic_privacy, "nr-block")` |
| Masking config | `SessionReplayConfiguration` (server) + `SessionReplayLocalConfiguration` (runtime) |

---

## How View Capture Works

The agent captures the Android View hierarchy by recursively traversing `ViewGroup` children starting from the Activity's root view. `SessionReplayCapture` walks the tree, filtering invisible views and applying privacy rules, then delegates each view to `SessionReplayThingyRecorder` which selects the appropriate specialized Thingy class based on `instanceof` checks.

View-specific captors:
- `SessionReplayTextViewThingy` — captures styled text with font, color, alignment
- `SessionReplayEditTextThingy` — captures editable text input with password detection
- `SessionReplayImageViewThingy` — captures images as Base64-encoded data URLs with caching
- `SessionReplayCompoundButtonThingy` — captures RadioButton, CheckBox, Switch, ToggleButton with checked state
- `SessionReplaySeekBarThingy` — captures SeekBar position with min/max range
- `SessionReplaySliderThingy` — captures Material Design Slider via reflection
- `SessionReplayProgressBarThingy` — captures determinate and indeterminate progress

The `instanceof` dispatch order (most-specific first):
```
EditText → CompoundButton → AbsSeekBar → ProgressBar → ImageView → TextView → View (generic)
```

If a view is an `AndroidComposeView` (Compose embedded in XML), it delegates to `ComposeTreeCapture` for Compose-specific handling.

---

## Component Compatibility

### Text & Input

| Component | Captured | Masked by Default | Masking API | Notes |
|---|---|---|---|---|
| `TextView` | Yes | Yes (`maskApplicationText`) | `nr-mask` / `nr-unmask` tag | Font size, color, alignment, family, weight captured; React Native `ReactTextView` supported |
| `EditText` | Yes | Yes (`maskUserInputText`) | `nr-mask` / `nr-unmask` tag | Hint text captured separately; password fields always masked |
| `AutoCompleteTextView` | Yes | Yes | Inherits from EditText | Captured as EditText |
| `TextInputEditText` | Yes | Yes | Inherits from EditText | Material wrapper; captured as EditText |
| `SecureField` (password) | Yes | Always | Cannot unmask | Detected via InputType bits (0x80, 0x90, 0xe0, 0x10 variations) |

### Controls

| Component | Captured | HTML Mapping | Notes |
|---|---|---|---|
| `RadioButton` | Yes | `<label>` + `<input type="radio">` | Checked state tracked; label text captured |
| `CheckBox` | Yes | `<label>` + `<input type="checkbox">` | Checked state tracked; label text captured |
| `Switch` | Yes | `<label>` + `<input type="checkbox" data-nr-type="toggle">` | Tagged for player-side toggle rendering |
| `SwitchCompat` | Yes | `<label>` + `<input type="checkbox" data-nr-type="toggle">` | AndroidX AppCompat variant; same toggle tagging |
| `ToggleButton` | Yes | `<label>` + `<input type="checkbox" data-nr-type="toggle">` | On/off text captured as label |
| `SeekBar` | Yes | `<input type="range">` | Progress, min (API 26+), max captured; min defaults to 0 on API < 26 |
| `RatingBar` | Yes | `<input type="range">` | Extends AbsSeekBar; captured as range input |
| `Material Slider` | Yes | `<input type="range">` | Detected via reflection on `com.google.android.material.slider.BaseSlider` |
| `Button` | Partial | `<div>` with text | Captured as TextView (inherits from it); no button-specific semantics |

### Progress Indicators

| Component | Captured | HTML Mapping | Notes |
|---|---|---|---|
| `ProgressBar` (determinate) | Yes | `<progress value="X" max="Y">` | Current progress and max captured |
| `ProgressBar` (indeterminate) | Yes | `<progress>` | No value attribute; browser renders animated indicator |
| `ContentLoadingProgressBar` | Yes | `<progress>` | Inherits from ProgressBar |

### Images

| Component | Captured | Masked by Default | Notes |
|---|---|---|---|
| `ImageView` | Yes | Yes (`maskAllImages`) | Base64 PNG data URL; 1MB LRU cache; scale type mapped to CSS `background-size` |
| `ImageButton` | Yes | Yes | Inherits from ImageView |
| `AppCompatImageView` | Yes | Yes | AndroidX wrapper; inherits from ImageView |
| `ShapeableImageView` | Yes | Yes | Material wrapper; inherits from ImageView |

**Image scale type mapping:**

| Android ScaleType | CSS `background-size` |
|---|---|
| `FIT_XY` | `100% 100%` |
| `CENTER_CROP` | `cover` |
| `FIT_CENTER` / `CENTER_INSIDE` / `FIT_START` / `FIT_END` | `contain` |
| `CENTER` | `contain` (default) |

### Layout & Containers

| Component | Captured | Notes |
|---|---|---|
| `LinearLayout` | Yes | Captured as generic `<div>`; children captured recursively |
| `RelativeLayout` | Yes | Captured as generic `<div>` |
| `FrameLayout` | Yes | Captured as generic `<div>` |
| `ConstraintLayout` | Yes | Captured as generic `<div>` |
| `CoordinatorLayout` | Yes | Captured as generic `<div>` |
| `ScrollView` / `HorizontalScrollView` | Yes | Content captured; scroll position not encoded |
| `NestedScrollView` | Yes | Content captured; scroll position not encoded |
| `RecyclerView` | Yes | Visible items captured; off-screen items not in snapshot |
| `ListView` / `GridView` | Yes | Visible items captured |
| `ViewPager` / `ViewPager2` | Yes | Current page content captured |
| `CardView` | Yes | Background color and elevation captured |
| `TabLayout` | Yes | Tab items captured as child views |
| `Toolbar` / `ActionBar` | Yes | Title and navigation elements captured |
| `BottomNavigationView` | Yes | Menu items captured as child views |

### Visuals

| Component | Captured | Notes |
|---|---|---|
| `View` (plain) | Yes | Position, size, background color, alpha |
| `WebView` | Partial | Rendered as generic view; web content NOT replayed as DOM |
| `SurfaceView` / `TextureView` | No | GPU-rendered surfaces not capturable |
| `MapView` | No | Renders as generic view; map tiles not captured |
| `VideoView` | No | Renders as generic view; video content not captured |
| `Canvas` (custom `onDraw`) | No | Custom drawing not captured; view frame captured as generic `<div>` |

---

## Masking & Privacy API

### Privacy Tags

Apply tags directly on views to control replay behavior:

```java
// Via general tag (XML or programmatic)
view.setTag("nr-mask");
view.setTag("nr-unmask");
view.setTag("nr-block");

// Via privacy-specific tag
view.setTag(R.id.newrelic_privacy, "nr-mask");
view.setTag(R.id.newrelic_privacy, "nr-block");
```

```xml
<!-- In XML layout -->
<TextView
    android:tag="nr-mask"
    android:text="Sensitive info" />
```

| Tag | Effect |
|---|---|
| `nr-mask` | Text replaced with asterisks; images replaced with gray placeholder |
| `nr-unmask` | Overrides config-level masking for this subtree |
| `nr-block` | Entire subtree replaced with black rectangle; no children captured |

### Programmatic Masking Rules

```java
// Mask/unmask by class name
SessionReplayLocalConfiguration config = agentConfiguration.getSessionReplayLocalConfiguration();
config.addMaskViewClass("com.example.SensitiveView");
config.addUnmaskViewClass("com.example.PublicView");

// Mask/unmask by view tag
config.addMaskViewTag("sensitive_content");
config.addUnmaskViewTag("public_content");

// Text masking strategy
config.setTextMaskingStrategy(TextMaskingStrategy.MASK_ALL_TEXT);
config.setTextMaskingStrategy(TextMaskingStrategy.MASK_USER_INPUT_TEXT);
config.setTextMaskingStrategy(TextMaskingStrategy.MASK_NO_TEXT);

// Touch masking
config.setMaskAllUserTouches(true);
```

### Server Configuration (`SessionReplayConfiguration`)

| Property | Default | Effect |
|---|---|---|
| `maskApplicationText` | `true` | Masks all static text (TextView) |
| `maskUserInputText` | `true` | Masks all editable text (EditText) |
| `maskAllImages` | `true` | Replaces all images with gray placeholder |
| `maskAllUserTouches` | `false` | Suppresses touch coordinate recording |
| `mode` | `"default"` | `"custom"` enables tag-based and class-based masking rules |

### Custom Masking Rules (Server-Provided)

When `mode = "custom"`, the server can push rules:

```json
{
  "identifier": "class",
  "type": "mask",
  "operator": "equals",
  "name": ["com.example.SensitiveView"]
}
```

Rules populate `maskedViewClasses`, `unmaskedViewClasses`, `maskedViewTags`, `unmaskedViewTags` sets at runtime.

### Masking Precedence

Masking is resolved in this priority order (highest to lowest):

1. **Password fields** — Always masked (InputType detection); cannot be overridden
2. **`nr-block` tag** — Entire subtree blocked; stops traversal
3. **Explicit `nr-mask` / `nr-unmask` tags** — Per-view overrides
4. **Class-based rules** — `maskedViewClasses` / `unmaskedViewClasses`
5. **Tag-based rules** — `maskedViewTags` / `unmaskedViewTags`
6. **Global config defaults** — `maskApplicationText`, `maskUserInputText`, `maskAllImages`

### Masking Inheritance Rules

Masking tags propagate **down** the view hierarchy with a security-first approach:

| Scenario | Behavior |
|---|---|
| Parent `nr-mask`, no child tag | All descendants inherit masking |
| Parent `nr-mask`, child `nr-unmask` | **Parent MASK wins** (security-first) |
| Parent `nr-unmask`, child `nr-mask` | Child subtree masked; siblings unmasked |
| Parent `nr-block` | Entire subtree blocked; no children captured |
| No tags | Global configuration defaults apply |

---

## View Filtering

Views are excluded from capture when any of these conditions are true:

| Condition | Check |
|---|---|
| Not visible on screen | `getGlobalVisibleRect()` returns false |
| Visibility GONE or INVISIBLE | `getVisibility() != View.VISIBLE` |
| Fully transparent | `getAlpha() <= 0` |

---

## Pause / Resume Recording (Confidential Screens)

```java
// In Activity or Fragment
@Override
protected void onResume() {
    super.onResume();
    NewRelic.pauseReplay();
}

@Override
protected void onPause() {
    super.onPause();
    NewRelic.recordReplay();
}
```

---

## Touch Tracking

Touch events are captured via the Curtains library, which intercepts `Window.Callback.dispatchTouchEvent()`:

| Event | Captured Data |
|---|---|
| `ACTION_DOWN` | Touch coordinates, target view ID, timestamp |
| `ACTION_MOVE` | Movement coordinates, timestamp |
| `ACTION_UP` | Release coordinates, target view ID, timestamp |

Touch tracking respects:
- `maskAllUserTouches` — suppresses all touch coordinate recording
- `nr-block` tag on ancestor — touch target masked (ID = -1)
- `ViewPrivacyUtils.hasBlockedAncestor()` — walks parent chain for blocked views

---

## Known Limitations

| Limitation | Details |
|---|---|
| WebView content | Web content inside WebView is not replayed as DOM; only the WebView frame is captured |
| SurfaceView / TextureView | GPU-rendered surfaces cannot be captured |
| Custom `onDraw()` | Custom Canvas drawing is not captured; view frame rendered as generic `<div>` |
| MapView | Map tiles are not captured; renders as generic view |
| VideoView | Video content is not captured; renders as generic view |
| Material Slider (reflection) | Detection uses class name reflection; may fail if class is obfuscated or absent |
| SeekBar `getMin()` | Requires API 26+; defaults to 0 on API 24-25 |
| RecyclerView off-screen items | Only visible items captured; items outside viewport excluded |
| Scroll position | ScrollView/RecyclerView scroll offset not encoded in frame data |
| Large images | Base64 encoding of large bitmaps may impact performance; 1MB cache limit |
| Password detection | Only detects standard Android InputType bits; custom password indicators not detected |
| React Native | Special handling for `ReactTextView` text color extraction; may fail silently |
| Button semantics | Buttons captured as TextView with no button-specific HTML element |
| Fragment transitions | View capture is Activity-scoped; Fragment views captured as part of Activity hierarchy |

---
