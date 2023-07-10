-keep class com.newrelic.agent.android.** { *; }
-dontwarn com.newrelic.agent.android.**
-keepattributes Exceptions, Signature, InnerClasses, LineNumberTable, SourceFile, EnclosingMethod

##
## NewRelic Gradle plugin 7.x requires the following additions:
##

# Retain generic signatures of TypeToken and its subclasses with R8 version 3.0 and higher.
# -keepattributes Signature
# -keep class com.newrelic.com.google.gson.reflect.TypeToken { *; }
# -keep class * extends com.newrelic.com.google.gson.reflect.TypeToken

# For using GSON @Expose annotation
# -keepattributes *Annotation*

