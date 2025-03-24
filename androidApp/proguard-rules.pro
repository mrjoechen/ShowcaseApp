-dontwarn okhttp3.**
-dontwarn org.slf4j.**
-keep class kotlin.**
-keep class kotlinx.**
-keep class io.ktor.**
# 保留带有 @Serializable 注解的类
-keep class ** implements kotlinx.serialization.Serializable {}

# 保留 Kotlin 序列化所需的内部类
-keepclassmembers class ** {
    *** Companion;
}

# 保留序列化的内部类
-keepnames class kotlinx.serialization.internal.**

# 保留自动生成的序列化类（防止其混淆）
-keepclassmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}


# 保留 @Serializable 注解本身
-keepattributes *Annotation*

# 忽略 @Serializable 注解
-dontwarn kotlinx.serialization.Serializable

# 保留所有带有 @SerialName 注解的字段
-keepclassmembers class ** {
    @kotlinx.serialization.SerialName <fields>;
}

# 保留所有注解，包括 @SerialName
-keepattributes *Annotation*
# 保留 @SerialName 注解的字段
-keep @interface kotlinx.serialization.SerialName

# 保留使用了 @SerialName 注解的类和字段
-keepclassmembers class ** {
    @kotlinx.serialization.SerialName *;
}

# 保留字段的原始名称，以确保序列化时不会混淆字段名
-keepclassmembers class ** {
    @kotlinx.serialization.Serializable *;
}

# 保留序列化时生成的内部类（避免字段或类名被混淆）
-keep class kotlinx.serialization.internal.** { *; }

# 保留自定义字段名称（通过 @SerialName 指定的字段名）
-keepnames class ** {
    @kotlinx.serialization.SerialName *;
}


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
# disable optimisation for descriptor field because in some versions of ProGuard, optimization generates incorrect bytecode that causes a verification error
# see https://github.com/Kotlin/kotlinx.serialization/issues/2719
-keepclassmembers public class **$$serializer {
    private ** descriptor;
}

# 忽略任何与序列化相关的警告
-dontwarn kotlinx.serialization.**

# 保留 Kotlin 标准库相关类
-keep class kotlin.** { *; }


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

# 特别保留 ServiceLoader 使用的类
-keep class coil3.util.FetcherServiceLoaderTarget { *; }
-keep class * implements coil3.util.FetcherServiceLoaderTarget
-keep class * extends coil3.util.DecoderServiceLoaderTarget { *; }
-keep class * extends coil3.util.FetcherServiceLoaderTarget { *; }