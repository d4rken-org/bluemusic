package eu.darken.bluemusic.devices.ui.appselection

import android.content.Context
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.common.AppTool
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
    private val appTool: AppTool,
    private val dispatcherProvider: DispatcherProvider,
    private val navCtrl: NavigationController,
) : ViewModel4(dispatcherProvider, logTag("Devices", "AppSelection", "VM"), navCtrl) {

    data class State(
        val deviceName: String = "",
        val apps: List<AppTool.Item> = emptyList(),
        val filteredApps: List<AppTool.Item> = emptyList(),
        val selectedPackages: Set<String> = emptySet(),
        val searchQuery: String = "",
        val isLoading: Boolean = true,
    )

    private val searchQueryFlow = MutableStateFlow("")
    private val allAppsFlow = MutableStateFlow<List<AppTool.Item>>(emptyList())
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
                it.appName.contains(searchQuery, ignoreCase = true) ||
                        it.pkgName?.contains(searchQuery, ignoreCase = true) == true
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
        val apps = appTool.getApps()
            .filter { it.pkgName != null }
            .filter { hasLaunchIntent(it.pkgName!!) }
            .sortedBy { it.appName.lowercase() }
        allAppsFlow.value = apps
        log(tag) { "Loaded ${apps.size} launchable apps" }
    }

    private fun hasLaunchIntent(packageName: String): Boolean {
        return try {
            context.packageManager.getLaunchIntentForPackage(packageName) != null
        } catch (e: Exception) {
            false
        }
    }

    fun onSearchQueryChanged(query: String) = launch {
        log(tag) { "Search query changed: $query" }
        searchQueryFlow.value = query
    }

    fun onAppToggled(packageName: String) = launch {
        log(tag) { "App toggled: $packageName" }
        val currentSelection = selectedPackagesFlow.value
        selectedPackagesFlow.value = if (packageName in currentSelection) {
            currentSelection - packageName
        } else {
            currentSelection + packageName
        }
    }

    fun onSaveSelection() = launch {
        log(tag) { "Saving selection: ${selectedPackagesFlow.value}" }
        deviceRepo.updateDevice(deviceAddress) { oldConfig ->
            oldConfig.copy(launchPkgs = selectedPackagesFlow.value.toList())
        }
        navUp()
    }

    fun onClearSelection() = launch {
        log(tag) { "Clearing app selection" }
        selectedPackagesFlow.value = emptySet()
    }

    @AssistedFactory
    interface Factory {
        fun create(deviceAddress: DeviceAddr): AppSelectionViewModel
    }
}