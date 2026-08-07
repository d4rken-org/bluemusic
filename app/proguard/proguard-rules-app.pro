-keep class eu.darken.bluemusic.BuildConfig { *; }
-dontobfuscate

-keep public interface eu.darken.bluemusic.bluetooth.core.SourceDevice {*;}
# Play Core KTX references this compile-time-only GMS annotation not on the runtime classpath
-dontwarn com.google.android.gms.common.annotation.NoNullnessRewrite
