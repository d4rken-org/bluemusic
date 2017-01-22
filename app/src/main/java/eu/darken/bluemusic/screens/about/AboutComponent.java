package eu.darken.bluemusic.screens.about;

import dagger.Subcomponent;
import eu.darken.ommvplib.injection.PresenterComponent;
import eu.darken.ommvplib.injection.fragment.FragmentComponent;
import eu.darken.ommvplib.injection.fragment.FragmentComponentBuilder;


@AboutScope
@Subcomponent(modules = {})
public interface AboutComponent extends PresenterComponent<AboutPresenter.View, AboutPresenter>, FragmentComponent<AboutFragment> {
    @Subcomponent.Builder
    interface Builder extends FragmentComponentBuilder<AboutFragment, AboutComponent> {

    }
}