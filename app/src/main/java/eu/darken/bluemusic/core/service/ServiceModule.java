package eu.darken.bluemusic.core.service;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import javax.inject.Named;

import dagger.Module;
import dagger.Provides;

@Module
public class ServiceModule {
    @Provides
    @Named("VolumeObserver")
    Handler volumeHandler() {
        HandlerThread handlerThread = new HandlerThread("VolumeObserver");
        handlerThread.start();
        Looper looper = handlerThread.getLooper();
        return new Handler(looper);
    }

}
