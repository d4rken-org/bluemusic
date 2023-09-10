package eu.darken.bluemusic.settings.ui.about;

import android.content.Context;
import android.content.pm.PackageInfo;

import java.util.Locale;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import eu.darken.bluemusic.BuildConfig;
import eu.darken.mvpbakery.base.Presenter;
import eu.darken.mvpbakery.injection.ComponentPresenter;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleOnSubscribe;
import io.reactivex.rxjava3.schedulers.Schedulers;
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
    }

    public interface View extends Presenter.View {
        void showVersion(String version);
    }
}
