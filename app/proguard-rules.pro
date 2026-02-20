# ========================================
# Marathon ProGuard Rules
# ========================================

# --- 디버그 스택 트레이스 보존 ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Kotlin ---
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# --- Coroutines ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# --- Room ---
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**
-keep class com.example.healthcare.data.local.entity.** { *; }
-keep class com.example.healthcare.data.local.dao.** { *; }

# --- Hilt / Dagger ---
-dontwarn dagger.internal.codegen.**
-keepclassmembers,allowobfuscation class * {
    @javax.inject.* <fields>;
    @javax.inject.* <init>(...);
    @dagger.* <fields>;
    @dagger.* <init>(...);
}
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }

# --- Google Maps ---
-keep class com.google.android.gms.maps.** { *; }
-keep interface com.google.android.gms.maps.** { *; }

# --- Google Play Services ---
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# --- Health Connect ---
-keep class androidx.health.connect.** { *; }
-dontwarn androidx.health.connect.**

# --- Compose ---
-dontwarn androidx.compose.**

# --- Data classes (Serialization 보존) ---
-keep class com.example.healthcare.domain.model.** { *; }
-keep class com.example.history.RunningRecord { *; }
-keep class com.example.history.RunningState { *; }
-keep class com.example.history.LocationPoint { *; }

# --- Enum 보존 ---
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
