package eu.darken.bluemusic.screens.volumes;

import dagger.Subcomponent;
import eu.darken.ommvplib.injection.PresenterComponent;
import eu.darken.ommvplib.injection.fragment.FragmentComponent;
import eu.darken.ommvplib.injection.fragment.FragmentComponentBuilder;


@VolumesrScope
@Subcomponent(modules = {VolumesModule.class})
public interface VolumesComponent extends PresenterComponent<VolumesView, VolumesPresenter>, FragmentComponent<VolumesFragment> {
    @Subcomponent.Builder
    interface Builder extends FragmentComponentBuilder<VolumesFragment, VolumesComponent> {
        VolumesComponent.Builder module(VolumesModule module);
    }
}
