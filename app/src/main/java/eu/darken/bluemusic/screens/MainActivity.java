package eu.darken.bluemusic.screens;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.view.MenuItem;

import java.util.Map;

import javax.inject.Inject;
import javax.inject.Provider;

import butterknife.ButterKnife;
import eu.darken.bluemusic.App;
import eu.darken.bluemusic.R;
import eu.darken.bluemusic.core.BlueMusicService;
import eu.darken.bluemusic.screens.volumes.VolumesFragment;
import eu.darken.ommvplib.injection.ComponentPresenterActivity;
import eu.darken.ommvplib.injection.fragment.FragmentComponent;
import eu.darken.ommvplib.injection.fragment.FragmentComponentBuilder;
import eu.darken.ommvplib.injection.fragment.FragmentComponentBuilderSource;
import timber.log.Timber;


public class MainActivity extends ComponentPresenterActivity<MainActivityView, MainActivityPresenter, MainActivityComponent>
        implements MainActivityView, FragmentComponentBuilderSource {

    @Inject Map<Class<? extends Fragment>, Provider<FragmentComponentBuilder>> componentBuilders;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_layout_main);
        ButterKnife.bind(this);

        Fragment introFragment = getSupportFragmentManager().findFragmentById(R.id.frame_content);
        if (introFragment == null) introFragment = VolumesFragment.newInstance();
        getSupportFragmentManager().beginTransaction().replace(R.id.frame_content, introFragment).commit();

        Intent service = new Intent(this, BlueMusicService.class);
        final ComponentName componentName = startService(service);
        if (componentName != null) Timber.v("Service is already running.");
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public <FragmentT extends Fragment, BuilderT extends FragmentComponentBuilder<FragmentT, ? extends FragmentComponent<FragmentT>>>
    BuilderT getComponentBuilder(Class<FragmentT> activityClass) {
        //noinspection unchecked
        return (BuilderT) componentBuilders.get(activityClass).get();
    }

    @Override
    protected MainActivityComponent createComponent() {
        MainActivityComponent.Builder builder = App.Injector.INSTANCE.getComponentBuilder(MainActivity.class);
        return builder.build();
    }

    @Override
    public void inject(@NonNull MainActivityComponent component) {
        super.inject(component);
        component.injectMembers(this);
    }

    @Override
    public Class<? extends MainActivityPresenter> getTypeClazz() {
        return MainActivityPresenter.class;
    }
}