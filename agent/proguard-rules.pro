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



