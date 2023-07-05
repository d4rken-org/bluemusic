package eu.darken.bluemusic.util;

import android.os.Build;

public class ApiHelper {
    public static int SDK_INT = Build.VERSION.SDK_INT;

    /**
     * @return if >=23
     */
    public static boolean hasMarshmallow() {
        return SDK_INT >= Build.VERSION_CODES.M;
    }

    /**
     * @return if >=26
     */
    public static boolean hasOreo() {
        return Build.VERSION.RELEASE.equals("O") || SDK_INT >= Build.VERSION_CODES.O;
    }

    /**
     * @return if >=29
     */
    public static boolean hasAndroid10() {
        return "Q".equals(Build.VERSION.RELEASE) || "10".equals(Build.VERSION.RELEASE) || SDK_INT >= 29;
    }

    /**
     * @return if >=29
     */
    public static boolean hasAndroid13() {
        return "13".equals(Build.VERSION.RELEASE) || SDK_INT >= 33;
    }
}
