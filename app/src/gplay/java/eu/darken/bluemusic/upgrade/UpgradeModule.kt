package eu.darken.bluemusic.upgrade

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import eu.darken.bluemusic.upgrade.core.UpgradeRepoGplay
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
abstract class UpgradeModule {
    @Binds
    @Singleton
    abstract fun control(gplay: UpgradeRepoGplay): UpgradeRepo

}