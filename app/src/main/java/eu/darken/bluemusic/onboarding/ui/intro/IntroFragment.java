package eu.darken.bluemusic.onboarding.ui.intro;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;
import eu.darken.bluemusic.R;
import eu.darken.bluemusic.util.Check;
import eu.darken.mvpbakery.base.MVPBakery;
import eu.darken.mvpbakery.base.ViewModelRetainer;
import eu.darken.mvpbakery.injection.InjectedPresenter;
import eu.darken.mvpbakery.injection.PresenterInjectionCallback;


public class IntroFragment extends Fragment implements IntroPresenter.View {

    @Inject IntroPresenter presenter;
    Unbinder unbinder;

    public static Fragment newInstance() {
        return new IntroFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View layout = inflater.inflate(R.layout.fragment_layout_intro, container, false);
        unbinder = ButterKnife.bind(this, layout);
        return layout;
    }

    @Override
    public void onDestroyView() {
        if (unbinder != null) unbinder.unbind();
        super.onDestroyView();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        MVPBakery.<IntroPresenter.View, IntroPresenter>builder()
                .presenterFactory(new InjectedPresenter<>(this))
                .presenterRetainer(new ViewModelRetainer<>(this))
                .addPresenterCallback(new PresenterInjectionCallback<>(this))
                .attach(this);
        super.onActivityCreated(savedInstanceState);
        //noinspection ConstantConditions
        final ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        Check.notNull(actionBar);
        actionBar.setTitle(R.string.app_name);
    }

    @OnClick(R.id.finish_onboarding)
    public void onFinishOnboardingClicked(View v) {
        presenter.onFinishOnboardingClicked();
    }

    @Override
    public void closeScreen() {
        //noinspection ConstantConditions
        getActivity().finish();
    }
}
