package eu.darken.bluemusic.util;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.bugsnag.android.Bugsnag;
import com.bugsnag.android.Severity;

import androidx.fragment.app.Fragment;
import timber.log.Timber;

public class ActivityUtil {

    public static void tryStartActivity(Activity activity, Intent intent) {
        tryStartActivity(activity, () -> activity.startActivity(intent));
    }

    public static void tryStartActivity(Fragment fragment, Intent intent) {
        tryStartActivity(fragment.requireContext(), () -> fragment.startActivity(intent));
    }

    public static void tryStartActivity(Context context, Runnable runnable) {
        try {
            runnable.run();
        } catch (ActivityNotFoundException e) {
            Bugsnag.notify(e, Severity.WARNING);
            String msg = "No compatible app found.";
            Timber.e(e, msg);
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
        }
    }

}
