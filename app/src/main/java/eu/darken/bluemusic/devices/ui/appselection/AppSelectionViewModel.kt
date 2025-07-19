package eu.darken.bluemusic.devices.ui.appselection

import android.content.Context
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.common.apps.AppInfo
import eu.darken.bluemusic.common.apps.AppRepo
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.navigation.NavigationController
import eu.darken.bluemusic.common.ui.ViewModel4
import eu.darken.bluemusic.devices.core.DeviceAddr
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.observeDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart

@HiltViewModel(assistedFactory = AppSelectionViewModel.Factory::class)
class AppSelectionViewModel @AssistedInject constructor(
    @Assisted private val deviceAddress: DeviceAddr,
    @ApplicationContext private val context: Context,
    private val deviceRepo: DeviceRepo,
    private val appRepo: AppRepo,
    private val dispatcherProvider: DispatcherProvider,
    private val navCtrl: NavigationController,
) : ViewModel4(dispatcherProvider, logTag("Devices", "AppSelection", "VM"), navCtrl) {

    data class State(
        val deviceName: String = "",
        val apps: List<AppInfo> = emptyList(),
        val filteredApps: List<AppInfo> = emptyList(),
        val selectedPackages: Set<String> = emptySet(),
        val searchQuery: String = "",
        val isLoading: Boolean = true,
    )

    private val searchQueryFlow = MutableStateFlow("")
    private val allAppsFlow = MutableStateFlow<List<AppInfo>>(emptyList())
    private val selectedPackagesFlow = MutableStateFlow<Set<String>>(emptySet())

    val state = combine(
        deviceRepo.observeDevice(deviceAddress).filterNotNull(),
        allAppsFlow.onStart { loadApps() },
        searchQueryFlow,
        selectedPackagesFlow,
    ) { device, apps, searchQuery, selectedPkgs ->
        val filteredApps = if (searchQuery.isBlank()) {
            apps
        } else {
            apps.filter {
                it.label.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }

        State(
            deviceName = device.label,
            apps = apps,
            filteredApps = filteredApps,
            selectedPackages = selectedPkgs,
            searchQuery = searchQuery,
            isLoading = false,
        )
    }.onStart {
        // Initialize selected packages from device config
        val device = deviceRepo.observeDevice(deviceAddress).filterNotNull().first()
        selectedPackagesFlow.value = device.launchPkgs.toSet()
    }.asStateFlow()

    private suspend fun loadApps() = launch {
        log(tag) { "Loading apps..." }
        appRepo.apps.first().let { appInfos ->
            val apps = appInfos.toList().sortedBy { it.label.lowercase() }
            allAppsFlow.value = apps
            log(tag) { "Loaded ${apps.size} launchable apps" }
        }
    }

    fun onSearchQueryChanged(query: String) = launch {
        log(tag) { "Search query changed: $query" }
        searchQueryFlow.value = query
    }

    fun onAppToggled(packageName: String) = launch {
        log(tag) { "App toggled: $packageName" }
        val currentSelection = selectedPackagesFlow.value
        val newSelection = if (packageName in currentSelection) {
            currentSelection - packageName
        } else {
            currentSelection + packageName
        }
        selectedPackagesFlow.value = newSelection

        // Automatically save the selection to the device repository
        log(tag) { "Saving selection: $newSelection" }
        deviceRepo.updateDevice(deviceAddress) { oldConfig ->
            oldConfig.copy(launchPkgs = newSelection.toList())
        }
    }


    fun onClearSelection() = launch {
        log(tag) { "Clearing app selection" }
        selectedPackagesFlow.value = emptySet()

        // Automatically save the cleared selection to the device repository
        log(tag) { "Saving cleared selection" }
        deviceRepo.updateDevice(deviceAddress) { oldConfig ->
            oldConfig.copy(launchPkgs = emptyList())
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(deviceAddress: DeviceAddr): AppSelectionViewModel
    }
}