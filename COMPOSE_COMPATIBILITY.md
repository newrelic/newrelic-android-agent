# Jetpack Compose Compatibility — New Relic Mobile Session Replay

This document covers which Jetpack Compose components and patterns are captured, masked, and tracked by the New Relic Mobile Session Replay (MSR) system, based on the test harness and agent source code.

---

## Requirements

| Requirement | Details |
|---|---|
| Minimum API for Session Replay | API 24 (Android 7.0) |
| Compose dependency | `androidx.compose.ui:ui` (runtime required) |
| Framework | `com.newrelic.agent.android` module, Compose integration via `SemanticsNode` introspection |
| Masking modifiers | `Modifier.newRelicMask()`, `Modifier.newRelicUnmask()`, `Modifier.newRelicBlock()` |

---

## How Compose Capture Works

The agent captures Compose views by detecting `AndroidComposeView` instances in the UIKit view hierarchy. From there, `ComposeTreeCapture` accesses the `SemanticsOwner` via reflection to obtain the unmerged root `SemanticsNode`, then recursively traverses the semantics tree.

Compose-specific captors operate alongside traditional Android View captors:
- `ComposeTextViewThingy` — captures styled text with font, color, alignment
- `ComposeEditTextThingy` — captures editable text input with hint text support
- `ComposeImageThingy` — captures images via reflection on BitmapPainter, VectorPainter, and AsyncImagePainter (Coil)
- `ComposeSliderThingy` — captures slider/range input with min/max/step
- `ComposeRadioButtonThingy` — captures radio button selection state

`SessionReplayThingyRecorder` dispatches to the appropriate Thingy based on `SemanticsProperties` present on each node. If a `LayoutNode` has an `interopView` (traditional Android View embedded in Compose), it delegates to the standard Android View recorder.

---

## Component Compatibility

### Text & Input

| Component | Captured | Masked by Default | Masking API | Notes |
|---|---|---|---|---|
| `Text` | Yes | No | `Modifier.newRelicMask()` | Captured as styled text node with font-size, color, alignment, weight, style, family |
| `TextField` | Yes | No | `Modifier.newRelicMask()` | Input text captured; respects `isMaskUserInputText` config; hint text used as fallback |
| `BasicTextField` | Yes | No | `Modifier.newRelicMask()` | Same handling as TextField via `EditableText` semantics |
| `AttributedString` / `AnnotatedString` | Yes | No | `Modifier.newRelicMask()` | Font runs and styling captured |

### Controls

| Component | Captured | Notes |
|---|---|---|
| `Button` | Partial | Renders as generic `<div>` — no dedicated Thingy; touch events tracked separately |
| `IconButton` | Partial | Same as Button — icon image captured if detectable |
| `RadioButton` | Yes | Captured via `ComposeRadioButtonThingy`; selection state tracked; only captured when childless (avoids duplicate text in time pickers) |
| `Slider` | Yes | Captured via `ComposeSliderThingy` as `<input type="range">`; supports continuous and discrete (stepped) modes |
| `RangeSlider` | Partial | ProgressBarRangeInfo captured but dual-thumb not semantically represented |
| `Switch` | No | No dedicated ComposeSwitchThingy — renders as generic view |
| `Checkbox` | No | No dedicated ComposeCheckboxThingy — renders as generic view |
| `FloatingActionButton` | Partial | Captured as generic view; icon/text children captured normally |

### Pickers & Date Inputs

| Component | Captured | Notes |
|---|---|---|
| `DatePicker` (Material3) | Partial | Individual child components (text, buttons) captured; picker structure as generic views |
| `TimePicker` (Material3) | Partial | RadioButton selections captured; overall structure as generic views |
| `DropdownMenu` | Partial | Menu items captured as generic views when visible |
| `ExposedDropdownMenuBox` | Partial | Text field captured; dropdown overlay may not be captured |

### Navigation

| Component | Captured | Notes |
|---|---|---|
| `NavHost` / Navigation Compose | Yes | Screen transitions captured via Activity lifecycle; individual screen content captured normally |
| `NavigationBar` / `BottomNavigation` | Yes | Tab items captured; selection state via underlying components |
| `TabRow` / `ScrollableTabRow` | Yes | Tab content captured; selected indicator as generic view |

### Layout & Containers

| Component | Captured | Notes |
|---|---|---|
| `Column` / `Row` / `Box` | Yes | Layout containers captured as transparent view nodes; children captured recursively |
| `LazyColumn` / `LazyRow` | Yes | Visible items captured; off-screen items not in snapshot (by design) |
| `LazyVerticalGrid` / `LazyHorizontalGrid` | Yes | Visible grid items captured |
| `Scaffold` | Yes | Top bar, bottom bar, FAB, and content all captured |
| `Card` | Yes | Captured with background color and corner radius |
| `Surface` | Yes | Captured with background styling |
| `ScrollableColumn` / `verticalScroll` | Yes | Content captured; scroll position not encoded |

### Visuals & Media

| Component | Captured | Masked by Default | Notes |
|---|---|---|---|
| `Image` (painterResource) | Yes | No | Captured via `ComposeImageThingy`; bitmap extracted from BitmapPainter |
| `Image` (vectorResource) | Yes | No | VectorPainter bitmap extracted via reflection on cached bitmap |
| `Image` (Coil AsyncImage) | Yes | No | AsyncImagePainter support via `ComposePainterReflectionUtils`; loading state may show placeholder |
| `Icon` (Material) | Partial | No | Captured if MeasurePolicy contains "Image"; otherwise generic view |
| `Canvas` | No | — | Custom drawing not captured; renders as empty generic view |
| `Shape` (via `Modifier.background`/`Modifier.clip`) | Partial | No | Shape affects background color extraction; not rendered as structured SVG |
| `LinearProgressIndicator` | No | — | No dedicated Compose ProgressBar Thingy |
| `CircularProgressIndicator` | No | — | No dedicated Compose ProgressBar Thingy |

### Interop

| Component | Captured | Notes |
|---|---|---|
| `AndroidView` (View in Compose) | Yes | Detected via `layoutNode.getInteropView()`; delegates to Android View recorder |
| Compose in Fragment/Activity | Yes | `AndroidComposeView` detected in standard view hierarchy traversal |

---

## Masking & Privacy API

### Compose Modifiers (`NewRelicModifiers.kt`)

```kotlin
// Mask content — text replaced with asterisks, images with placeholder
Modifier.newRelicMask()

// Unmask content — override parent masking for this subtree
Modifier.newRelicUnmask()

// Block entire subtree — replaced with opaque black rectangle in replay
Modifier.newRelicBlock()
```

### Semantics Property (`NewRelicSemanticsProperties.kt`)

For advanced use cases, the privacy tag can be set directly via semantics:

```kotlin
Modifier.semantics {
    newRelicPrivacy = "nr-mask"   // or "nr-unmask" or "nr-block"
}
```

### Privacy Tags

| Tag | Constant | Effect |
|---|---|---|
| `nr-mask` | `PrivacyTags.MASK` | Text masked with asterisks; images replaced with gray placeholder |
| `nr-unmask` | `PrivacyTags.UNMASK` | Clears masking for this subtree |
| `nr-block` | `PrivacyTags.BLOCK` | Entire subtree replaced with black rectangle; children excluded from replay |

### Global Configuration

| Config Property | Effect |
|---|---|
| `isMaskUserInputText` | Masks all editable text input (TextField, BasicTextField) |
| `isMaskApplicationText` | Masks all static text (Text, AnnotatedString) |
| `isMaskAllImages` | Replaces all images with gray placeholder (#CCCCCC) |
| `mode = "custom"` | Enables per-view privacy tag checking |

### Masking Inheritance Rules

Masking settings propagate **down** the Compose tree with a security-first approach:

| Scenario | Behavior |
|---|---|
| Parent `nr-mask`, no child override | All descendant content masked |
| Parent `nr-mask`, child `nr-unmask` | **Parent MASK wins** — child remains masked (security-first) |
| Parent `nr-unmask`, child `nr-mask` | Child subtree masked; siblings unmasked |
| Parent `nr-block` | Entire subtree replaced with black rectangle; no children captured |
| No tag on parent or child | Both remain untagged; global config applies |

> **Note:** Unlike iOS where child `NRConditionalMaskView` can override parent masking, Android Compose uses a security-first model where MASK always dominates UNMASK in the hierarchy.

### Implementation Detail

Privacy tags are resolved during `ComposeTreeCapture.captureChildren()` traversal. Tags are written into the `SemanticsConfiguration` of each node during capture, enabling O(1) lookups downstream via `ComposePrivacyUtils.getEffectivePrivacyTag()`.

---

## Pause / Resume Recording (Confidential Screens)

```kotlin
@Composable
fun ConfidentialScreen() {
    DisposableEffect(Unit) {
        NewRelic.pauseReplay()
        onDispose {
            NewRelic.recordReplay()
        }
    }
    
    Text("Sensitive Data")
}
```
