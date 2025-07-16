package eu.darken.bluemusic.bluetooth.core

import kotlinx.coroutines.flow.first

suspend fun BluetoothRepo.currentState() = state.first()