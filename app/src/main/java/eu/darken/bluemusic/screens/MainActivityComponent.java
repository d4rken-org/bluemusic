package eu.darken.bluemusic.screens;


import dagger.Subcomponent;
import eu.darken.ommvplib.injection.PresenterComponent;
import eu.darken.ommvplib.injection.activity.ActivityComponent;
import eu.darken.ommvplib.injection.activity.ActivityComponentBuilder;

@MainScope
@Subcomponent(modules = {
        MainActivityFragmentBinderModule.class
})
public interface MainActivityComponent extends ActivityComponent<MainActivity>, PresenterComponent<MainActivityView, MainActivityPresenter> {

    @Subcomponent.Builder
    interface Builder extends ActivityComponentBuilder<MainActivity, MainActivityComponent> {

    }
}
