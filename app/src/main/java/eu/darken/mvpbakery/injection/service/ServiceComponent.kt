package eu.darken.mvpbakery.injection.service

import android.app.Service

import dagger.android.AndroidInjector

interface ServiceComponent<ServiceT : Service> : AndroidInjector<ServiceT> {
    abstract class Builder<ServiceT : Service, ComponentT : ServiceComponent<ServiceT>> : AndroidInjector.Builder<ServiceT>() {
        abstract override fun build(): ComponentT
    }
}
