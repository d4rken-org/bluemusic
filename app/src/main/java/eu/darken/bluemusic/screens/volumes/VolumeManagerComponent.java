package eu.darken.bluemusic.screens.volumes;

import dagger.Subcomponent;
import eu.darken.ommvplib.injection.PresenterComponent;


@VolumeManagerScope
@Subcomponent(modules = {VolumeManagerModule.class})
public interface VolumeManagerComponent extends PresenterComponent<VolumeManagerView, VolumeManagerPresenter> {

}
