# --- kotlinx.serialization ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class ru.tipowk.tunvpn.**$$serializer { *; }
-keepclassmembers class ru.tipowk.tunvpn.** {
    *** Companion;
}
-keepclasseswithmembers class ru.tipowk.tunvpn.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- JNI: hev-socks5-tunnel native methods ---
-keep class com.v2ray.ang.service.TProxyService {
    native <methods>;
    *;
}

# --- libv2ray (Xray-core Go library) ---
-keep class libv2ray.** { *; }

# --- Koin ---
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# --- Android VPN Service ---
-keep class ru.tipowk.tunvpn.vpn.TunVpnService { *; }
-keep class ru.tipowk.tunvpn.MainActivity { *; }
-keep class ru.tipowk.tunvpn.TunVpnApp { *; }

# --- Compose / Kotlin ---
-dontwarn kotlin.**
-dontwarn kotlinx.**
-dontwarn androidx.**
