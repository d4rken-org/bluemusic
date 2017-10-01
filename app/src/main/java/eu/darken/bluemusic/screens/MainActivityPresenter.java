package eu.darken.bluemusic.screens;

import android.support.annotation.Nullable;

import javax.inject.Inject;

import eu.darken.bluemusic.core.service.BinderHelper;
import eu.darken.ommvplib.injection.ComponentPresenter;
import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;

public class MainActivityPresenter extends ComponentPresenter<MainActivityView, MainActivityComponent> {

    private final BinderHelper binderHelper;
    private Disposable serviceSub = Disposables.disposed();

    @Inject
    public MainActivityPresenter(BinderHelper binderHelper) {
        this.binderHelper = binderHelper;
    }

    @Override
    public void onBindChange(@Nullable MainActivityView view) {
        super.onBindChange(view);
        if (getView() != null) {
            serviceSub = binderHelper.getBinder().subscribe();
        } else {
            serviceSub.dispose();
        }
    }
}
