package eu.darken.bluemusic.monitor.core.worker

import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import eu.darken.bluemusic.common.BuildConfigWrap
import eu.darken.bluemusic.common.coroutine.AppScope
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.flow.setupCommonEventHandlers
import eu.darken.bluemusic.devices.core.DeviceRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MonitorControl @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val workerManager: WorkManager,
    private val dispatcherProvider: DispatcherProvider,
    deviceRepo: DeviceRepo,
) {

    init {
        deviceRepo.devices
            .map { devices ->
                devices.any { device ->
                    device.isConnected && device.requiresMonitor
                }
            }
            .distinctUntilChanged()
            .setupCommonEventHandlers(TAG) { "Device monitor" }
            .onEach { requiresMonitor ->
                if (requiresMonitor) {
                    log(TAG) { "Connected device requires monitor, starting monitor service" }
                    startMonitor()
                }
            }
            .launchIn(appScope)
    }

    suspend fun startMonitor(
        forceStart: Boolean = false,
    ): Unit = withContext(dispatcherProvider.IO) {
        val workerData = Data.Builder().apply {

        }.build()
        log(TAG, VERBOSE) { "Worker data: $workerData" }

        val workRequest = OneTimeWorkRequestBuilder<MonitorWorker>().apply {
            setInputData(workerData)
        }.build()

        log(TAG, VERBOSE) { "Worker request: $workRequest" }

        val operation = workerManager.enqueueUniqueWork(
            "${BuildConfigWrap.APPLICATION_ID}.monitor.worker",
            if (forceStart) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            workRequest,
        )

        operation.result.get()
        log(TAG) { "Monitor start request send." }
    }

    companion object {
        private val TAG = logTag("Monitor", "Control")
    }
}