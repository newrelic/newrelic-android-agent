package com.newrelic.agent.android.sessionReplay.compose

/**
 * Constants used throughout the Compose Session Replay functionality
 */
object ComposeSessionReplayConstants {

    // Privacy Tags
    object PrivacyTags {
        const val MASK = "nr-mask"
        const val UNMASK = "nr-unmask"
    }

    // Session Replay Modes
    object Modes {
        const val CUSTOM = "custom"
    }

    // HTML/CSS Constants
    object CSS {
        // CSS Properties
        const val BACKGROUND_COLOR = "background-color"
        const val BACKGROUND_IMAGE = "background-image"
        const val BACKGROUND_SIZE = "background-size"
        const val BACKGROUND_REPEAT = "background-repeat"
        const val BACKGROUND_POSITION = "background-position"
        const val COLOR = "color"
        const val FONT_FAMILY = "font-family"
        const val FONT_SIZE = "font-size"
        const val FONT_WEIGHT = "font-weight"
        const val FONT_STYLE = "font-style"
        const val TEXT_ALIGN = "text-align"
        const val LINE_HEIGHT = "line-height"
        const val WHITE_SPACE = "white-space"
        const val WORD_WRAP = "word-wrap"
        const val LEFT = "left"
        const val TOP = "top"
        const val WIDTH = "width"
        const val HEIGHT = "height"
        const val STYLE = "style"
        const val PLACEHOLDER = "placeholder"

        // CSS Values
        const val PRE_WRAP = "pre-wrap"
        const val BREAK_WORD = "break-word"
        const val NORMAL = "normal"
        const val BOLD = "bold"
        const val ITALIC = "italic"
        const val SANS_SERIF = "sans-serif"
        const val SERIF = "serif"
        const val MONOSPACE = "monospace"
        const val CURSIVE = "cursive"
        const val CENTER = "center"
        const val RIGHT = "right"
        const val LEFT_ALIGN = "left"
        const val PX_UNIT = "px"

        // Background Size Values
        const val COVER = "cover"
        const val CONTAIN = "contain"
        const val AUTO = "auto"
        const val FULL_SIZE = "100% 100%"
        const val FULL_WIDTH_AUTO_HEIGHT = "100% auto"
        const val AUTO_WIDTH_FULL_HEIGHT = "auto 100%"
        const val NO_REPEAT = "no-repeat"

        // Font Families (Full CSS declarations)
        const val FONT_FAMILY_DEFAULT = "font-family: sans-serif; font-weight: normal; font-style: normal;"
        const val FONT_FAMILY_SANS_SERIF = "font-family: sans-serif; font-weight: normal; font-style: normal;"
        const val FONT_FAMILY_SERIF = "font-family: serif; font-weight: normal; font-style: normal;"
        const val FONT_FAMILY_MONOSPACE = "font-family: monospace; font-weight: normal; font-style: normal;"
        const val FONT_FAMILY_CURSIVE = "font-family: cursive; font-weight: normal; font-style: normal;"

        // Font Weights
        const val WEIGHT_LIGHT = "300"
        const val WEIGHT_MEDIUM = "500"
        const val WEIGHT_SEMI_BOLD = "600"
        const val WEIGHT_EXTRA_BOLD = "800"
        const val WEIGHT_BLACK = "900"
    }

    // HTML Element Types
    object HtmlElements {
        const val INPUT_TYPE_TEXT = "text"
    }

    // Painter/Modifier Class Names
    object PainterClassNames {
        const val PAINTER = "Painter"
        const val FOUNDATION_IMAGE = "foundation.Image"
        const val PAINTER_MODIFIER = "PainterModifier"
        const val ASYNC_IMAGE_PAINTER = "AsyncImagePainter"
    }

    // Reflection Field Names
    object ReflectionFields {
        const val BACKGROUND_ELEMENT_CLASS = "androidx.compose.foundation.BackgroundElement"
        const val COLOR_FIELD = "color"
    }

    // Default Values
    object Defaults {
        const val DEFAULT_FONT_SIZE = 14.0f
        const val DEFAULT_FONT_NAME = "default"
        const val DEFAULT_TEXT_COLOR = "000000"
        const val DEFAULT_BACKGROUND_COLOR = "FFFFFF"
        const val DEFAULT_ICON_SIZE = 24
        const val DEFAULT_PLACEHOLDER_SIZE = 100
        const val EM_TO_PX_MULTIPLIER = 16.0f
    }

    // Masking
    object Masking {
        const val MASK_CHARACTER = "*"
    }

    // Log Tags
    object LogTags {
        const val COMPOSE_IMAGE = "ComposeImageThingy"
    }

    // Cache Keys
    object CacheKeys {
        const val SEPARATOR = "_"
        const val DIMENSION_SEPARATOR = "x"
    }

    // Semantics Property Keys
    object SemanticsKeys {
        const val NEW_RELIC_PRIVACY = "NewRelicPrivacy"
    }

    // Format Strings
    object Formats {
        const val FLOAT_TWO_DECIMAL = "%.2f"
        const val FLOAT_TWO_DECIMAL_PX = "%.2fpx"
        const val URL_FORMAT = "url(%s)"
        const val COLOR_HEX_PREFIX = "#"
        const val SEMICOLON_SPACE = "; "
        const val COLON_SPACE = ": "
    }
}