package eu.darken.bluemusic.util;


import com.bugsnag.android.BeforeNotify;

import javax.inject.Inject;

import eu.darken.bluemusic.BuildConfig;
import eu.darken.bluemusic.core.settings.Settings;
import eu.darken.bluemusic.util.dagger.ApplicationScope;

@ApplicationScope
public class BugsnagErrorHandler implements BeforeNotify {
    private final Settings settings;
    private final BugsnagTree bugsnagTree;

    @Inject
    public BugsnagErrorHandler(Settings settings, BugsnagTree bugsnagTree) {
        this.settings = settings;
        this.bugsnagTree = bugsnagTree;
    }

    @Override
    public boolean run(com.bugsnag.android.Error error) {
        if (!settings.isBugReportingEnabled()) return false;
        bugsnagTree.update(error);

        return !BuildConfig.DEBUG;
    }
}
