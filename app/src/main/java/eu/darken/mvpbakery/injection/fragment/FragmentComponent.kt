package eu.darken.mvpbakery.injection.fragment

import androidx.fragment.app.Fragment
import dagger.android.AndroidInjector

interface FragmentComponent<FragmentT : Fragment> : AndroidInjector<FragmentT> {
    abstract class Builder<FragmentT : Fragment, ComponentT : FragmentComponent<FragmentT>> : AndroidInjector.Builder<FragmentT>() {
        abstract override fun build(): ComponentT
    }
}
