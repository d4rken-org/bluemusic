package eu.darken.bluemusic.screens;


import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import dagger.Subcomponent;
import eu.darken.ommvplib.injection.PresenterComponent;
import eu.darken.ommvplib.injection.activity.ActivityComponent;

@MainActivityComponent.Scope
@Subcomponent(modules = {
        MainActivityFragmentBinderModule.class
})
public interface MainActivityComponent extends ActivityComponent<MainActivity>, PresenterComponent<MainActivityView, MainActivityPresenter> {

    @Subcomponent.Builder
    abstract class Builder extends ActivityComponent.Builder<MainActivity, MainActivityComponent> {

    }

    @javax.inject.Scope
    @Retention(RetentionPolicy.RUNTIME)
    @interface Scope {
    }
}
