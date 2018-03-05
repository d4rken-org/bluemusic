package eu.darken.bluemusic.settings.ui.about;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.support.annotation.Nullable;

import com.bugsnag.android.Bugsnag;
import com.bugsnag.android.Client;

import java.lang.reflect.Field;
import java.util.Locale;

import javax.inject.Inject;

import eu.darken.bluemusic.BuildConfig;
import eu.darken.mvpbakery.base.Presenter;
import eu.darken.mvpbakery.injection.ComponentPresenter;
import io.reactivex.Single;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import timber.log.Timber;


@AboutComponent.Scope
public class AboutPresenter extends ComponentPresenter<AboutPresenter.View, AboutComponent> {

    private final Context context;

    @Inject
    AboutPresenter(Context context) {
        this.context = context;
    }

    @Override
    public void onBindChange(@Nullable View view) {
        super.onBindChange(view);
        if (view == null) return;
        Single
                .create((SingleOnSubscribe<String>) emitter -> {
                    PackageInfo info = context.getPackageManager().getPackageInfo(BuildConfig.APPLICATION_ID, 0);
                    emitter.onSuccess(String.format(Locale.US, "Version %s (%d)", info.versionName, info.versionCode));
                })
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(s -> onView(v -> v.showVersion(s)), Timber::e);
        Single
                .create((SingleOnSubscribe<String>) emitter -> {
                    final Field userField = Client.class.getDeclaredField("user");
                    userField.setAccessible(true);
                    final Object user = userField.get(Bugsnag.getClient());
                    final Field idField = user.getClass().getDeclaredField("id");
                    idField.setAccessible(true);
                    final String id = (String) idField.get(user);
                    emitter.onSuccess(id);
                })
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(id -> onView(v -> v.showInstallID(id)), Timber::e);
    }

    public interface View extends Presenter.View {
        void showInstallID(String id);

        void showVersion(String version);
    }
}
