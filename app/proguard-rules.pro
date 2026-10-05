# ProGuard / R8 Rules for One Keyboard

# Preserve source file & line numbers for clean crash logs
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 1. Barcode Keyboard Components & Services
-keep class com.example.barcodekeyboard.service.** { *; }
-keep class com.example.barcodekeyboard.ui.** { *; }
-keep class com.example.barcodekeyboard.data.** { *; }
-keep class com.example.barcodekeyboard.core.** { *; }

# 2. CameraX
-dontwarn androidx.camera.**
-keep class androidx.camera.** { *; }
-keep interface androidx.camera.** { *; }
-keep class androidx.camera.view.** { *; }

# 3. Google ML Kit Barcode Scanning & Vision
-dontwarn com.google.mlkit.**
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode.** { *; }
-keep class com.google.android.gms.vision.** { *; }
-keep class com.google.android.gms.common.** { *; }

# 4. Material Components & AndroidX
-dontwarn com.google.android.material.**
-keep class com.google.android.material.** { *; }
-dontwarn androidx.preference.**
-keep class androidx.preference.** { *; }

# 5. Kotlin Coroutines & Reflection
-dontwarn kotlinx.coroutines.**
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}