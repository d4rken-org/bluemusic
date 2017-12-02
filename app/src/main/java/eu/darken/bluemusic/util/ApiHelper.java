package eu.darken.bluemusic.util;

import android.os.Build;

public class ApiHelper {
    public static int SDK_INT = Build.VERSION.SDK_INT;

    /**
     * @return if >=26
     */
    public static boolean hasOreo() {
        return Build.VERSION.RELEASE.equals("O") || SDK_INT >= Build.VERSION_CODES.O;
    }
}
