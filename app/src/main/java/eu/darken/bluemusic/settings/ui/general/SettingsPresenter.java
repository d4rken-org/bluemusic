package eu.darken.bluemusic.settings.ui.general;

import android.app.Activity;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import eu.darken.bluemusic.util.iap.IAPRepo;
import eu.darken.mvpbakery.base.Presenter;
import eu.darken.mvpbakery.injection.ComponentPresenter;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

@SettingsComponent.Scope
public class SettingsPresenter extends ComponentPresenter<SettingsPresenter.View, SettingsComponent> {

    private final IAPRepo iapRepo;
    private Disposable upgradeSub = Disposable.disposed();
    boolean isProVersion = false;

    @Inject
    SettingsPresenter(IAPRepo iapRepo) {
        this.iapRepo = iapRepo;
    }

    @Override
    public void onBindChange(@Nullable View view) {
        super.onBindChange(view);
        if (getView() != null) {
            upgradeSub = iapRepo.isProVersion()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(isPremiumVersion -> {
                        SettingsPresenter.this.isProVersion = isPremiumVersion;
                        onView(v -> v.updatePremiumState(isPremiumVersion));
                    });
        } else {
            upgradeSub.dispose();
        }
    }

    void onUpgradeClicked(Activity activity) {
        iapRepo.buyProVersion(activity);
    }

    public interface View extends Presenter.View {
        void updatePremiumState(boolean isPremiumVersion);
    }
}
