package eu.darken.bluemusic.screens.volumes;

import dagger.Subcomponent;
import eu.darken.ommvplib.injection.PresenterComponent;


@VolumesrScope
@Subcomponent(modules = {VolumesModule.class})
public interface VolumesComponent extends PresenterComponent<VolumesView, VolumesPresenter> {

}
