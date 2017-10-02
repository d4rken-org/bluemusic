package eu.darken.bluemusic.core.service;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import javax.inject.Inject;

import io.reactivex.Observable;
import io.reactivex.ObservableOnSubscribe;

public class BinderHelper {

    private final Context context;

    @Inject
    public BinderHelper(Context context) {
        this.context = context;
    }


    public Observable<BlueMusicService.MBinder> getBinder() {
        final ServiceConnection[] serviceConnection = new ServiceConnection[1];
        return Observable
                .create((ObservableOnSubscribe<BlueMusicService.MBinder>) e -> {
                    Intent service = ServiceHelper.getIntent(context);
                    serviceConnection[0] = new ServiceConnection() {
                        @Override
                        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                            e.onNext((BlueMusicService.MBinder) iBinder);
                        }

                        @Override
                        public void onServiceDisconnected(ComponentName componentName) {
                            e.onComplete();
                        }
                    };
                    context.bindService(service, serviceConnection[0], Context.BIND_AUTO_CREATE);
                })
                .doOnDispose(() -> context.unbindService(serviceConnection[0]));
    }
}
