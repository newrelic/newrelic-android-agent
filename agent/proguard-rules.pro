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
