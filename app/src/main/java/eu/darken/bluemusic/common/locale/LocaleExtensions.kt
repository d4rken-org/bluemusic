package eu.darken.bluemusic.common.locale

import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import java.util.Locale

@RequiresApi(Build.VERSION_CODES.N)
fun LocaleList.toList(): List<Locale> = List(this.size()) { index -> this.get(index) }

val LocaleList.primary: Locale
    @RequiresApi(Build.VERSION_CODES.N)
    get() = get(0)