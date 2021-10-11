package eu.darken.mvpbakery.injection.activity

import android.app.Activity

import dagger.android.AndroidInjector

interface ActivityComponent<ActivityT : Activity> : AndroidInjector<ActivityT> {
    abstract class Builder<ActivityT : Activity, ComponentT : ActivityComponent<ActivityT>> : AndroidInjector.Builder<ActivityT>() {
        abstract override fun build(): ComponentT
    }
}
