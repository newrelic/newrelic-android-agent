# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep New Relic classes
-keep class com.newrelic.** { *; }
-dontwarn com.newrelic.**

# Keep Compose
-dontwarn androidx.compose.**
