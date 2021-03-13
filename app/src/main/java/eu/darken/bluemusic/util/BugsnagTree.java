package eu.darken.bluemusic.util;

import android.util.Log;

import com.bugsnag.android.Event;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import eu.darken.bluemusic.AppComponent;
import timber.log.Timber;

@AppComponent.Scope
public class BugsnagTree extends Timber.DebugTree {
    private static final int BUFFER_SIZE = 300;

    // Adding one to the initial size accounts for the add before remove.
    private final Deque<String> buffer = new ArrayDeque<>(BUFFER_SIZE + 1);

    @Inject
    public BugsnagTree() {
    }

    @Override
    protected void log(int priority, String tag, String message, Throwable t) {
        message = System.currentTimeMillis() + " " + priorityToString(priority) + "/" + tag + ": " + message;
        synchronized (buffer) {
            buffer.addLast(message);
            if (buffer.size() > BUFFER_SIZE) {
                buffer.removeFirst();
            }
        }
    }

    public void update(@NonNull Event event) {
        synchronized (buffer) {
            int i = 1;
            for (String message : buffer) event.addMetadata("Log", String.format(Locale.US, "%03d", i++), message);
            event.addMetadata("Log", String.format(Locale.US, "%03d", i), Log.getStackTraceString(event.getOriginalError()));
        }
    }

    private static String priorityToString(int priority) {
        switch (priority) {
            case Log.ERROR:
                return "E";
            case Log.WARN:
                return "W";
            case Log.INFO:
                return "I";
            case Log.DEBUG:
                return "D";
            case Log.VERBOSE:
                return "V";
            default:
                return String.valueOf(priority);
        }
    }
}
