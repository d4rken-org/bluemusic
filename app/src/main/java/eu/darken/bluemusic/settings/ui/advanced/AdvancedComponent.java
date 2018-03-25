package eu.darken.bluemusic.settings.ui.advanced;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import dagger.Subcomponent;
import eu.darken.mvpbakery.injection.PresenterComponent;
import eu.darken.mvpbakery.injection.fragment.FragmentComponent;


@AdvancedComponent.Scope
@Subcomponent()
public interface AdvancedComponent extends PresenterComponent<AdvancedPresenter.View, AdvancedPresenter>, FragmentComponent<AdvancedFragment> {
    @Subcomponent.Builder
    abstract class Builder extends FragmentComponent.Builder<AdvancedFragment, AdvancedComponent> {

    }

    @Documented
    @javax.inject.Scope
    @Retention(RetentionPolicy.RUNTIME)
    @interface Scope {
    }
}