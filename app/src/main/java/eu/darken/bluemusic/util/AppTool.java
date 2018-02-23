package eu.darken.bluemusic.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import eu.darken.bluemusic.AppComponent;
import timber.log.Timber;

@AppComponent.Scope
public class AppTool {
    private final Context context;

    @Inject
    public AppTool(Context context) {
        this.context = context;
    }

    public List<Item> getApps() {
        List<Item> items = new ArrayList<>();
        final List<PackageInfo> installedPackages = context.getPackageManager().getInstalledPackages(0);
        for (PackageInfo pkg : installedPackages) {
            items.add(new Item(context, pkg));
        }
        Collections.sort(items, (a1, a2) -> a1.getAppName().compareTo(a2.getAppName()));
        return items;
    }

    public static String getLabel(Context context, String pkg) throws PackageManager.NameNotFoundException {
        final ApplicationInfo applicationInfo = context.getPackageManager().getApplicationInfo(pkg, 0);
        return applicationInfo.loadLabel(context.getPackageManager()).toString();
    }

    public void launch(String pkg) {
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(pkg);
        if (intent == null) {
            intent = tryGetLauncherIntent(pkg);
            Timber.d("No default launch intent, was launcher=%b", intent != null);
        }
        if (intent == null) {
            intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("market://details?id=" + pkg));
            Timber.d("No default launch intent, default to opening Google Play");
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        Timber.i("Launching: %s", intent);
        context.startActivity(intent);
    }

    @Nullable
    Intent tryGetLauncherIntent(String pkg) {
        if (!getLauncherPkgs().contains(pkg)) return null;
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN, null);
        launcherIntent.addCategory(Intent.CATEGORY_HOME);
        return launcherIntent;
    }

    Collection<String> getLauncherPkgs() {
        Set<String> launchers = new HashSet<>();
        final Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_HOME);
        for (ResolveInfo info : context.getPackageManager().queryIntentActivities(mainIntent, 0)) {
            if (info.activityInfo == null) continue;
            launchers.add(info.activityInfo.packageName);
        }
        return launchers;
    }

    public static class Item {
        private final String pkgName;
        private String appName;

        protected Item() {
            pkgName = null;
            appName = "-";
        }

        public Item(Context context, PackageInfo packageInfo) {
            this.pkgName = packageInfo.packageName;
            try {
                this.appName = getLabel(context, pkgName);
            } catch (Exception e) {
                this.appName = "???";
            }
        }

        public static Item empty() {
            return new Item();
        }

        public String getAppName() {
            return appName;
        }

        public String getPackageName() {
            return pkgName;
        }
    }
}
