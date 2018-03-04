package eu.darken.bluemusic.settings.ui.general;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import dagger.Subcomponent;
import eu.darken.mvpbakery.injection.PresenterComponent;
import eu.darken.mvpbakery.injection.fragment.support.SupportFragmentComponent;


@SettingsComponent.Scope
@Subcomponent()
public interface SettingsComponent extends PresenterComponent<SettingsPresenter.View, SettingsPresenter>, SupportFragmentComponent<SettingsFragment> {
    @Subcomponent.Builder
    abstract class Builder extends SupportFragmentComponent.Builder<SettingsFragment, SettingsComponent> {

    }

    @Documented
    @javax.inject.Scope
    @Retention(RetentionPolicy.RUNTIME)
    @interface Scope {
    }
}