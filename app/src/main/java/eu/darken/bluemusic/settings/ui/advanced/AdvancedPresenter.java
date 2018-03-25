package eu.darken.bluemusic.settings.ui.advanced;

import javax.inject.Inject;

import eu.darken.mvpbakery.base.Presenter;
import eu.darken.mvpbakery.injection.ComponentPresenter;

@AdvancedComponent.Scope
public class AdvancedPresenter extends ComponentPresenter<AdvancedPresenter.View, AdvancedComponent> {

    @Inject
    AdvancedPresenter() {
    }

    public interface View extends Presenter.View {
    }
}
