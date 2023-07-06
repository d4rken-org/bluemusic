package eu.darken.bluemusic.onboarding.ui.intro

import android.Manifest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import eu.darken.bluemusic.R
import eu.darken.bluemusic.databinding.FragmentLayoutIntroBinding
import eu.darken.bluemusic.util.Check
import eu.darken.bluemusic.util.viewBinding
import eu.darken.mvpbakery.base.MVPBakery.Companion.builder
import eu.darken.mvpbakery.base.ViewModelRetainer
import eu.darken.mvpbakery.injection.InjectedPresenter
import eu.darken.mvpbakery.injection.PresenterInjectionCallback
import timber.log.Timber
import javax.inject.Inject


class IntroFragment : Fragment(), IntroPresenter.View {

    @Inject lateinit var presenter: IntroPresenter
    val ui: FragmentLayoutIntroBinding by viewBinding()

    private val requestPermissionLauncher = registerForActivityResult(RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            presenter.finishOnboarding(requireContext())
        } else {
            Timber.w("Permission was not granted")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_layout_intro, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.finishOnboarding.setOnClickListener { presenter.finishOnboarding(requireContext()) }
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        builder<IntroPresenter.View, IntroPresenter>()
                .presenterFactory(InjectedPresenter(this))
                .presenterRetainer(ViewModelRetainer(this))
                .addPresenterCallback(PresenterInjectionCallback(this))
                .attach(this)
        super.onActivityCreated(savedInstanceState)
        val actionBar = (activity as AppCompatActivity?)!!.supportActionBar
        Check.notNull(actionBar)
        actionBar!!.setTitle(R.string.app_name)
    }

    override fun requestBluetoothConnectPermission() {
        requestPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
    }

    override fun closeScreen() {
        requireActivity().finish()
    }

    companion object {
        @JvmStatic fun newInstance(): Fragment {
            return IntroFragment()
        }
    }
}