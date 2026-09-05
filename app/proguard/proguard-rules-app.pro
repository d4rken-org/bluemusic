-keep class eu.darken.bluemusic.BuildConfig { *; }

-keep public interface eu.darken.bluemusic.bluetooth.core.SourceDevice {*;}
# Play Core KTX references this compile-time-only GMS annotation not on the runtime classpath
-dontwarn com.google.android.gms.common.annotation.NoNullnessRewrite

# Throwable.localized() shows the exception's simpleName in the error dialog title.
-keepnames class * extends java.lang.Throwable
