package eu.darken.bluemusic.screens.settings;

import android.support.annotation.Nullable;

import javax.inject.Inject;

import eu.darken.bluemusic.core.service.BinderHelper;
import eu.darken.ommvplib.base.Presenter;
import eu.darken.ommvplib.injection.ComponentPresenter;
import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;

@SettingsComponent.Scope
public class SettingsPresenter extends ComponentPresenter<SettingsPresenter.View, SettingsComponent> {

    private final BinderHelper binderHelper;
    private Disposable serviceSub = Disposables.disposed();

    @Inject
    SettingsPresenter(BinderHelper binderHelper) {
        this.binderHelper = binderHelper;
    }

    @Override
    public void onBindChange(@Nullable View view) {
        super.onBindChange(view);
        if (getView() != null) {
            serviceSub = binderHelper.getBinder().subscribe();
        } else {
            serviceSub.dispose();
        }
    }

    public interface View extends Presenter.View {
    }
}
