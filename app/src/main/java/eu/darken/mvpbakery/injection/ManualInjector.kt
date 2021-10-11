package eu.darken.mvpbakery.injection

import dagger.android.AndroidInjector

interface ManualInjector<T> : AndroidInjector<T> {
    operator fun get(instance: T): AndroidInjector<T>
}
