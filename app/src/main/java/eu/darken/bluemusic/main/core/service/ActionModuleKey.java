package eu.darken.bluemusic.main.core.service;

import java.lang.annotation.Target;

import dagger.MapKey;
import dagger.internal.Beta;

import static java.lang.annotation.ElementType.METHOD;

@Beta
@MapKey
@Target(METHOD)
public @interface ActionModuleKey {
    Class<? extends ActionModule> value();
}
