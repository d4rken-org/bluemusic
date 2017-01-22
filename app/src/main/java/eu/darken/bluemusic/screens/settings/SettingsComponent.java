package eu.darken.bluemusic.screens.settings;

import dagger.Subcomponent;
import eu.darken.ommvplib.injection.PresenterComponent;
import eu.darken.ommvplib.injection.fragment.FragmentComponent;
import eu.darken.ommvplib.injection.fragment.FragmentComponentBuilder;


@SettingsScope
@Subcomponent(modules = {})
public interface SettingsComponent extends PresenterComponent<SettingsPresenter.View, SettingsPresenter>, FragmentComponent<SettingsFragment> {
    @Subcomponent.Builder
    interface Builder extends FragmentComponentBuilder<SettingsFragment, SettingsComponent> {

    }
}