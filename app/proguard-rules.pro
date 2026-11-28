# =========================================================================
# PurchyFlow - Production R8 / ProGuard Optimization & Obfuscation Rules
# =========================================================================

# 1. Bytecode Log Stripping: Completely eliminate debug & verbose logs from compiled release bytecode
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# 2. SQLCipher & Native Database Encryption Protection
-keep class net.zetetic.** { *; }
-dontwarn net.zetetic.**
-keepclasseswithmembernames class * {
    native <methods>;
}

# 3. Room Database & Schema Definitions
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# 4. Domain Data Models & Parser Classes (preserve properties for serialization/reflection)
-keep class com.sugarcane.purchyflow.data.model.** { *; }
-keep class com.sugarcane.purchyflow.parser.** { *; }
-keep class com.sugarcane.purchyflow.security.** { *; }

# 5. AndroidX Biometric & Security Vault
-keep class androidx.biometric.** { *; }

# 6. Kotlin Coroutines & Attributes
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
