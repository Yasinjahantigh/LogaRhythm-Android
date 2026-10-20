-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-keep public class * extends android.app.Application
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Hilt generates components reflectively, and app classes are kept to stay safe across releases.
-keep class com.yasinjahantigh.logarhythm.** { *; }
-keep class **.Hilt_* { *; }
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponent
-keep class * implements dagger.hilt.internal.GeneratedComponent
-keep class * implements dagger.hilt.internal.UnstableApi
-keepclassmembers class * {
    @dagger.hilt.android.EarlyEntryPoint *;
}

-dontwarn org.slf4j.**
