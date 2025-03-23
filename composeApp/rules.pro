-dontwarn okhttp3.**
-dontwarn org.slf4j.**
-keep class kotlin.**
-keep class kotlinx.**
-keep class io.ktor.**
# Keep `Companion` object fields of serializable classes.
# This avoids serializer lookup through `getDeclaredClasses` as done for named companion objects.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep `serializer()` on companion objects (both default and named) of serializable classes.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `INSTANCE.serializer()` of serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# @Serializable and @Polymorphic are used at runtime for polymorphic serialization.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# Don't print notes about potential mistakes or omissions in the configuration for kotlinx-serialization classes
# See also https://github.com/Kotlin/kotlinx.serialization/issues/1900
-dontnote kotlinx.serialization.**

# Serialization core uses `java.lang.ClassValue` for caching inside these specified classes.
# If there is no `java.lang.ClassValue` (for example, in Android), then R8/ProGuard will print a warning.
# However, since in this case they will not be used, we can disable these warnings
-dontwarn kotlinx.serialization.internal.ClassValueReferences
-dontwarn kotlinx.datetime.**
-dontwarn org.slf4j.**
-keep class org.slf4j.**{ *; }
-keep class com.sun.jna.* { *; }
-keep class * implements com.sun.jna.* { *; }
-dontwarn androidx.compose.material.**
-keep class androidx.compose.material3.** { *; }
-ignorewarnings

-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }

# 保留 ServiceLoader 的服务提供者配置文件
-keepattributes ServiceLoader

# 保留 META-INF/services/ 下的文件
-keep META-INF/services/**

# Coil 3 特定的规则
-keep class coil3.** { *; }
-keep interface coil3.** { *; }

# 特别保留 ServiceLoader 使用的类
-keep class coil3.util.FetcherServiceLoaderTarget { *; }
-keep class * implements coil3.util.FetcherServiceLoaderTarget
-keep class * extends coil3.util.DecoderServiceLoaderTarget { *; }
-keep class * extends coil3.util.FetcherServiceLoaderTarget { *; }

# Ktor 网络组件相关规则
-keep class io.ktor.** { *; }
-keep class kotlinx.coroutines.** { *; }

# 保留所有 ServiceLoader 的实现类
-keepclasseswithmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}

# 保护 ServiceLoader 使用的注解
-keepattributes *Annotation*, Signature, Exception
