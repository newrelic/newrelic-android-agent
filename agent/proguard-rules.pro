-keep class com.newrelic.agent.android.** { *; }
-dontwarn com.newrelic.agent.android.**
-keepattributes Exceptions, Signature, InnerClasses, LineNumberTable, SourceFile, EnclosingMethod

##
## NewRelic Gradle plugin 7.x requires the following additions:
##

-keepattributes Signature
-keep class com.newrelic.com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.newrelic.com.google.gson.reflect.TypeToken
# For using GSON @Expose annotation
-keepattributes *Annotation*

## Apache HTTP client
-keep class org.apache.http.** { *; }
-keep interface org.apache.http.** { *; }

## OKHttp 2
-keep class com.squareup.okhttp.** { *; }
-keep interface com.squareup.okhttp.** { *; }

## OKHttp 3
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

## Retrofit
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }

## Gson
-keep class com.google.gson.** { *; }
-keep interface com.google.gson.** { *; }


# Add any project specific keep options here:
# CSSBackgroundDrawable (React Native 0.74+)
-keep class com.facebook.react.uimanager.drawable.CSSBackgroundDrawable {
    int mColor;
}

# ReactViewBackgroundDrawable (React Native older versions)
-keep class com.facebook.react.views.view.ReactViewBackgroundDrawable {
    int mColor;
}

# BackgroundDrawable (React Native middle versions)
-keep class com.facebook.react.uimanager.drawable.BackgroundDrawable {
    int backgroundColor;
}

# CompositeBackgroundDrawable (React Native various versions)
-keep class com.facebook.react.uimanager.drawable.CompositeBackgroundDrawable {
    android.graphics.drawable.Drawable background;
}

##
## Runtime hybrid-framework detection (PlatformDetector) looks these classes up by name, so R8 must not
## rename them: a Flutter release build otherwise turns FlutterActivity into "X.e" and the app is reported
## as native. -keepnames only stops the renaming; unused classes can still be removed, and a class the app
## does not contain matches nothing. Keep in sync with PlatformDetector (PlatformDetectorKeepRulesTest).
##

# Capacitor
-keepnames class com.getcapacitor.BridgeActivity
-keepnames class com.newrelic.capacitor.plugin.NewRelicCapacitorPlugin

# Cordova
-keepnames class org.apache.cordova.CordovaActivity
-keepnames class com.newrelic.cordova.plugin.NewRelicCordovaPlugin

# React Native
-keepnames class com.facebook.react.ReactActivity
-keepnames interface com.facebook.react.ReactApplication
-keepnames class com.NewRelic.NRMModularAgentPackage

# Flutter
-keepnames class io.flutter.embedding.android.FlutterActivity
-keepnames class io.flutter.embedding.android.FlutterFragmentActivity
-keepnames class io.flutter.app.FlutterActivity
-keepnames class com.newrelic.newrelic_mobile.NewrelicMobilePlugin

# Unity
-keepnames class com.unity3d.player.UnityPlayerActivity
