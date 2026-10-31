# NETVISION ProGuard Rules

# Room Database
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

# Kotlin Coroutines
-keepclassmembers class kotlinx.coroutines.** { *; }

# Models
-keep class com.netvision.app.models.** { *; }
-keep class com.netvision.app.database.** { *; }
