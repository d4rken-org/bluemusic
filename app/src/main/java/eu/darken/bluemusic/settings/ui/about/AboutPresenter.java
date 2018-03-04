package eu.darken.bluemusic.settings.ui.about;

import android.support.annotation.Nullable;

import javax.inject.Inject;

import eu.darken.mvpbakery.base.Presenter;
import eu.darken.mvpbakery.injection.ComponentPresenter;


@AboutComponent.Scope
public class AboutPresenter extends ComponentPresenter<AboutPresenter.View, AboutComponent> {

    @Inject
    AboutPresenter() {
    }

    @Override
    public void onBindChange(@Nullable View view) {
        super.onBindChange(view);
    }

    public interface View extends Presenter.View {
    }
}
