# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile


# ---- LogaRhythm release rules (R8 is enabled) ----
# Room/Hilt/Coil/Media3/Glance/DataStore ship their own consumer rules. These are belt-and-braces
# for the app's own persisted / reflected types and for readable crash reports.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keep class com.example.musicplayer.data.room.** { *; }
-keep class com.example.musicplayer.domain.model.** { *; }
-dontwarn org.slf4j.**
