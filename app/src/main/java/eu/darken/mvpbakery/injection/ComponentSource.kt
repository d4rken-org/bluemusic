package eu.darken.mvpbakery.injection

import dagger.android.AndroidInjector
import dagger.internal.Preconditions.checkNotNull
import javax.inject.Inject
import javax.inject.Provider


class ComponentSource<T : Any> @Inject constructor(private val injectorFactories: Map<Class<out T>, @JvmSuppressWildcards Provider<AndroidInjector.Factory<out T>>>)
    : ManualInjector<T> {

    override fun inject(instance: T) {
        get(instance).inject(instance)
    }

    override fun get(instance: T): AndroidInjector<T> {
        val factoryProvider = injectorFactories[instance.javaClass]
                ?: throw ClassNotFoundException("No injector available for $instance")

        val factory = factoryProvider.get() as AndroidInjector.Factory<T>

        return checkNotNull(factory.create(instance), "%s.create(I) should not return null.", factory.javaClass.canonicalName)
    }
}
