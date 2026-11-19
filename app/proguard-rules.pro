-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-keep public class * extends android.app.Application
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Room entities, DAOs, and database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class * implements androidx.room.RoomDatabase$Callback
-dontwarn androidx.room.paging.**

# Data models and JSON/DataStore serialization
-keepclassmembers class com.yasinjahantigh.logarhythm.domain.model.** {
    <fields>;
    <init>(...);
}
-keepclassmembers class com.yasinjahantigh.logarhythm.data.** {
    <fields>;
    <init>(...);
}

# Hilt & Dependency Injection
-keep class **.Hilt_* { *; }
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponent
-keep class * implements dagger.hilt.internal.GeneratedComponent
-keep class * implements dagger.hilt.internal.UnstableApi
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
    @dagger.hilt.android.EarlyEntryPoint *;
}

# ViewModels
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Glance App Widget
-keep class * extends androidx.glance.appwidget.GlanceAppWidget
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver

# WorkManager
-keep class * extends androidx.work.ListenableWorker {
    <init>(...);
}

# Media3 Session
-keep class * extends androidx.media3.session.MediaSessionService
-keep class * extends androidx.media3.session.MediaLibraryService

-dontwarn org.slf4j.**

# youtubedl-android launches its Python/yt-dlp payload and deserializes GitHub JSON with Jackson.
-keep class com.yausername.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keep class org.apache.commons.io.** { *; }
-keep class org.apache.commons.compress.** { *; }
-dontwarn com.fasterxml.jackson.**
-dontwarn org.apache.commons.**
-dontwarn java.beans.**
