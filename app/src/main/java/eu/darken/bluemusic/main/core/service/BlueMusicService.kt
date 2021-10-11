package eu.darken.bluemusic.main.core.service

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.SparseArray
import androidx.annotation.RequiresApi
import com.bugsnag.android.Bugsnag
import eu.darken.bluemusic.App
import eu.darken.bluemusic.R
import eu.darken.bluemusic.bluetooth.core.BluetoothEventReceiver
import eu.darken.bluemusic.bluetooth.core.BluetoothSource
import eu.darken.bluemusic.bluetooth.core.FakeSpeakerDevice
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.main.core.audio.AudioStream
import eu.darken.bluemusic.main.core.audio.StreamHelper
import eu.darken.bluemusic.main.core.audio.VolumeObserver
import eu.darken.bluemusic.main.core.database.DeviceManager
import eu.darken.bluemusic.main.core.database.ManagedDevice
import eu.darken.bluemusic.main.core.service.modules.EventModule
import eu.darken.bluemusic.main.core.service.modules.VolumeModule
import eu.darken.bluemusic.settings.core.Settings
import eu.darken.bluemusic.util.ApiHelper
import eu.darken.bluemusic.util.WakelockMan
import eu.darken.bluemusic.util.ui.RetryWithDelay
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposables
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject


class BlueMusicService : Service(), VolumeObserver.Callback {
    @Inject lateinit var deviceManager: DeviceManager
    @Inject lateinit var bluetoothSource: BluetoothSource
    @Inject lateinit var streamHelper: StreamHelper
    @Inject lateinit var volumeObserver: VolumeObserver
    @Inject lateinit var settings: Settings
    @Inject lateinit var serviceHelper: ServiceHelper
    @Inject lateinit var wakelockMan: WakelockMan
    @Inject lateinit var eventModuleMap: Map<Class<out EventModule>, @JvmSuppressWildcards EventModule>
    @Inject lateinit var volumeModuleMap: Map<Class<out VolumeModule>, @JvmSuppressWildcards VolumeModule>

    private val eventScheduler = Schedulers.from(Executors.newSingleThreadExecutor())
    private val volumeScheduler = Schedulers.from(Executors.newSingleThreadExecutor())
    private var notificationSub = Disposables.disposed()
    private var isActiveSub = Disposables.disposed()
    private val onGoingConnections = LinkedHashMap<String, CompositeDisposable>()

    private val ringerPermission = object : BroadcastReceiver() {
        @RequiresApi(api = Build.VERSION_CODES.M)
        override fun onReceive(context: Context, intent: Intent) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            Timber.d("isNotificationPolicyAccessGranted()=%b", notificationManager.isNotificationPolicyAccessGranted)
        }
    }

    private val binder = MBinder()

    override fun onCreate() {
        Timber.v("onCreate()")
        (application as App).serviceInjector().inject(this)
        super.onCreate()

        for (id in AudioStream.Id.values()) {
            volumeObserver.addCallback(id, this)
        }
        contentResolver.registerContentObserver(android.provider.Settings.System.CONTENT_URI, true, volumeObserver)

        if (ApiHelper.hasMarshmallow()) {
            registerReceiver(ringerPermission, IntentFilter(NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED))
        }

        notificationSub = deviceManager.devices()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { stringManagedDeviceMap ->
                    val connected = ArrayList<ManagedDevice>()
                    for (d in stringManagedDeviceMap.values) {
                        if (d.isActive) connected.add(d)
                    }
                    serviceHelper.updateActiveDevices(connected)
                }
        isActiveSub = bluetoothSource.isEnabled
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { isActive -> if (!isActive) serviceHelper.stop() }
    }

    override fun onDestroy() {
        Timber.v("onDestroy()")
        contentResolver.unregisterContentObserver(volumeObserver)
        if (ApiHelper.hasMarshmallow()) {
            unregisterReceiver(ringerPermission)
        }
        notificationSub.dispose()
        isActiveSub.dispose()
        wakelockMan.tryRelease()
        super.onDestroy()
    }

    internal inner class MBinder : Binder()

    override fun onBind(intent: Intent): IBinder? = binder

    override fun onUnbind(intent: Intent): Boolean {
        Timber.v("onUnbind(intent=%s)", intent)
        return true
    }

    override fun onRebind(intent: Intent) {
        Timber.v("onRebind(intent=%s)", intent)
        super.onRebind(intent)
    }

    @SuppressLint("ThrowableNotAtBeginning")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.v("onStartCommand-STARTED(intent=%s, flags=%d, startId=%d)", intent, flags, startId)
        serviceHelper.start()
        if (intent == null) {
            Timber.w("Intent was null")
            serviceHelper.stop()
        } else if (intent.hasExtra(BluetoothEventReceiver.EXTRA_DEVICE_EVENT)) {
            val event = intent.getParcelableExtra<SourceDevice.Event>(BluetoothEventReceiver.EXTRA_DEVICE_EVENT)!!
            val retryWithDelay = RetryWithDelay(300, 1000)
            bluetoothSource.reloadConnectedDevices()
                    .subscribeOn(eventScheduler)
                    .observeOn(eventScheduler)
                    .map<Map<String, SourceDevice>> { connectedDevices ->
                        if (event.type == SourceDevice.Event.Type.CONNECTED && !connectedDevices.containsKey(event.address)) {
                            Timber.w("%s not fully connected, retrying (#%d).", event.device.label, retryWithDelay.retryCount)
                            serviceHelper.updateMessage("${getString(R.string.description_waiting_for_devicex, event.device.label)} (#${retryWithDelay.retryCount})")
                            throw MissingDeviceException(event)
                        }
                        return@map connectedDevices
                    }
                    .retryWhen(retryWithDelay)
                    .flatMap {
                        deviceManager.devices().firstOrError().map<ManagedDevice> { managedDevices ->
                            return@map managedDevices[event.address] ?: throw UnmanagedDeviceException(event)
                        }
                    }
                    .map { managedDevice -> ManagedDevice.Action(managedDevice, event.type) }
                    .flatMap { action ->
                        return@flatMap when {
                            action.type == SourceDevice.Event.Type.CONNECTED -> {
                                serviceHelper.updateMessage(getString(R.string.label_reaction_delay))
                                var reactionDelay = action.device.actionDelay
                                if (reactionDelay == null) reactionDelay = Settings.DEFAULT_REACTION_DELAY
                                Timber.d("Delaying reaction to %s by %d ms.", action, reactionDelay)
                                Single.timer(reactionDelay, TimeUnit.MILLISECONDS).map { action }
                            }
                            else -> Single.just(action)
                        }
                    }
                    .doOnSuccess { action ->
                        Timber.d("Acting on %s", action)
                        serviceHelper.updateMessage(getString(R.string.label_status_adjusting_volumes))

                        val newDevice = action.device
                        val priorityArray = SparseArray<MutableList<EventModule>>()
                        for ((_, value) in eventModuleMap) {
                            val priority = value.priority
                            var list: MutableList<EventModule>? = priorityArray.get(priority)
                            if (list == null) {
                                list = ArrayList()
                                priorityArray.put(priority, list)
                            }
                            list.add(value)
                        }

                        for (i in 0 until priorityArray.size()) {
                            val currentPriorityModules = priorityArray.get(priorityArray.keyAt(i))
                            Timber.d("%d event modules at priority %d", currentPriorityModules.size, priorityArray.keyAt(i))

                            val latch = CountDownLatch(currentPriorityModules.size)
                            for (module in currentPriorityModules) {
                                Completable
                                        .fromRunnable {
                                            Timber.v("Event module %s HANDLE-START", module)
                                            module.handle(newDevice, event)
                                            Timber.v("Event module %s HANDLE-STOP", module)
                                        }
                                        .subscribeOn(Schedulers.io())
                                        .doOnSubscribe { disp ->
                                            Timber.d("Running event module %s", module)
                                            val comp = onGoingConnections[event.address]
                                            if (comp != null) {
                                                Timber.v("Existing CompositeDisposable, adding %s to %s", module, comp)
                                                comp.add(disp)
                                            }
                                        }
                                        .doFinally {
                                            latch.countDown()
                                            Timber.d("Event module %s finished", module)
                                        }
                                        .subscribe({ }, { e ->
                                            Timber.e(e, "Event module error")
                                            Bugsnag.notify(e)
                                        })
                            }
                            try {
                                latch.await()
                            } catch (e: InterruptedException) {
                                Timber.w("Was waiting for %d event modules at priority %d but was INTERRUPTED", currentPriorityModules.size, priorityArray.keyAt(i))
                                break
                            }

                        }
                    }
                    .doOnSubscribe { disposable ->
                        Timber.d("Subscribed %s", event)

                        if (event.type == SourceDevice.Event.Type.CONNECTED) {
                            val compositeDisposable = CompositeDisposable()
                            compositeDisposable.add(disposable)
                            onGoingConnections[event.address] = compositeDisposable
                        } else if (event.type == SourceDevice.Event.Type.DISCONNECTED) {
                            val eventActions = onGoingConnections.remove(event.address)
                            if (eventActions != null) {
                                Timber.d("%s disconnected, canceling on-going event (%d actions)", event.address, eventActions.size())
                                eventActions.dispose()
                            }
                        }
                    }
                    .doOnDispose { Timber.d("Disposed %s", event) }
                    .doFinally {
                        val remove = onGoingConnections.remove(event.address)
                        Timber.d("%s finished, removed: %s", event.address, remove)

                        // Do we need to keep the service running?
                        deviceManager.devices().firstOrError().subscribeOn(Schedulers.computation())
                                .map { deviceMap ->
                                    Timber.d("Active devices: %s", deviceMap)
                                    val msgBuilder = StringBuilder()
                                    var listening = false
                                    var locking = false
                                    var waking = false
                                    for (d in deviceMap.values) {
                                        if (!d.isActive) continue
                                        if (d.address == FakeSpeakerDevice.ADDR) continue

                                        if (!listening && settings.isVolumeChangeListenerEnabled) {
                                            listening = true
                                            Timber.d("Keep running because we are listening for changes")
                                            msgBuilder.append(getString(R.string.label_volume_listener))
                                        }
                                        if (!locking && d.volumeLock) {
                                            locking = true
                                            Timber.d("Keep running because the device wants volume lock: %s", d)
                                            if (msgBuilder.isNotEmpty()) msgBuilder.append(",\n")
                                            msgBuilder.append(getString(R.string.label_volume_lock))
                                        }
                                        if (!waking && d.keepAwake) {
                                            waking = true
                                            Timber.d("Keep running because the device wants keep awake: %s", d)
                                            if (msgBuilder.isNotEmpty()) msgBuilder.append(",\n")
                                            msgBuilder.append(getString(R.string.label_keep_awake))
                                        }
                                    }
                                    val keepRunning = listening || locking || waking
                                    return@map Pair(keepRunning, msgBuilder.toString())
                                }
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe { (keepRunning, message) ->
                                    if (keepRunning) {
                                        serviceHelper.updateMessage(message)
                                    } else {
                                        serviceHelper.stop()
                                        wakelockMan.tryRelease()
                                    }
                                }
                    }
                    .subscribe { action, throwable ->
                        Timber.d("action=%s, throwable=%s", action, throwable)
                        if (throwable != null && throwable !is UnmanagedDeviceException && throwable !is MissingDeviceException) {
                            Timber.e(throwable, "Device error")
                            Bugsnag.notify(throwable)
                        }
                    }

        } else if (ServiceHelper.STOP_ACTION == intent.action) {
            Timber.d("Stopping service, currently %d on-going events, killing them.", onGoingConnections.size)
            val tmp = HashMap(onGoingConnections)
            onGoingConnections.clear()
            for ((_, value) in tmp) {
                value.dispose()
            }

            serviceHelper.stop()
        } else {
            serviceHelper.stop()
        }

        Timber.v("onStartCommand-END(intent=%s, flags=%d, startId=%d)", intent, flags, startId)
        return START_NOT_STICKY
    }

    override fun onVolumeChanged(id: AudioStream.Id, volume: Int) {
        Single
                .create<SparseArray<MutableList<VolumeModule>>> { emitter ->
                    val priorityArray = SparseArray<MutableList<VolumeModule>>()
                    for ((_, value) in volumeModuleMap) {
                        val priority = value.priority
                        var list: MutableList<VolumeModule>? = priorityArray.get(priority)
                        if (list == null) {
                            list = ArrayList()
                            priorityArray.put(priority, list)
                        }
                        list.add(value)
                    }
                    emitter.onSuccess(priorityArray)
                }
                .subscribeOn(volumeScheduler).observeOn(volumeScheduler)
                .subscribe({ priorityArray ->
                    for (i in 0 until priorityArray.size()) {
                        val currentPriorityModules = priorityArray.get(priorityArray.keyAt(i))
                        Timber.d("%d volume modules at priority %d", currentPriorityModules.size, priorityArray.keyAt(i))

                        val latch = CountDownLatch(currentPriorityModules.size)
                        for (module in currentPriorityModules) {
                            Completable
                                    .fromRunnable {
                                        Timber.v("Volume module %s HANDLE-START", module)
                                        module.handle(id, volume)
                                        Timber.v("Volume module %s HANDLE-STOP", module)
                                    }
                                    .subscribeOn(Schedulers.io())
                                    .doFinally {
                                        latch.countDown()
                                        Timber.v("Volume module %s finished", module)
                                    }
                                    .subscribe({ }, { e ->
                                        Timber.e(e, "Volume module error")
                                        Bugsnag.notify(e)
                                    })
                        }
                        try {
                            latch.await()
                        } catch (e: InterruptedException) {
                            Timber.w("Was waiting for %d volume modules at priority %d but was INTERRUPTED", currentPriorityModules.size, priorityArray.keyAt(i))
                            break
                        }

                    }

                }, { e ->
                    Timber.e(e, "Event module error")
                    Bugsnag.notify(e)
                })
    }
}