package eu.darken.bluemusic.screens.settings;

import android.app.Activity;
import android.support.annotation.Nullable;

import javax.inject.Inject;

import eu.darken.bluemusic.IAPHelper;
import eu.darken.bluemusic.core.service.BinderHelper;
import eu.darken.ommvplib.base.Presenter;
import eu.darken.ommvplib.injection.ComponentPresenter;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;
import io.reactivex.schedulers.Schedulers;

@SettingsComponent.Scope
public class SettingsPresenter extends ComponentPresenter<SettingsPresenter.View, SettingsComponent> {

    private final BinderHelper binderHelper;
    private final IAPHelper iapHelper;
    private Disposable serviceSub = Disposables.disposed();
    private Disposable upgradeSub = Disposables.disposed();
    boolean isProVersion = false;

    @Inject
    SettingsPresenter(BinderHelper binderHelper, IAPHelper iapHelper) {
        this.binderHelper = binderHelper;
        this.iapHelper = iapHelper;
    }

    @Override
    public void onBindChange(@Nullable View view) {
        super.onBindChange(view);
        if (getView() != null) {
            upgradeSub = iapHelper.isProVersion()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(isPremiumVersion -> {
                        SettingsPresenter.this.isProVersion = isPremiumVersion;
                        onView(v -> v.updatePremiumState(isPremiumVersion));
                    });
            serviceSub = binderHelper.getBinder().subscribe();
        } else {
            upgradeSub.dispose();
            serviceSub.dispose();
        }
    }

    void onUpgradeClicked(Activity activity) {
        iapHelper.buyProVersion(activity);
    }

    public interface View extends Presenter.View {
        void updatePremiumState(boolean isPremiumVersion);
    }
}
