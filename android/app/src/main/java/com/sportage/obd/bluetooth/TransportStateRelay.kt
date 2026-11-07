package com.sportage.obd.bluetooth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One stable observable for the lifetime of the service, even when SPP/BLE changes. */
class TransportStateRelay(private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = mutableState.asStateFlow()
    private var collector: Job? = null
    private var generation = 0L

    @Synchronized
    fun attach(source: StateFlow<ConnectionState>, address: String) {
        collector?.cancel()
        val token = ++generation
        mutableState.value = ConnectionState.Connecting(address)
        collector = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            source.collect { next ->
                synchronized(this@TransportStateRelay) {
                    // A fresh transport initially emits Disconnected before connect starts.
                    if (token == generation &&
                        !(next is ConnectionState.Disconnected &&
                            mutableState.value is ConnectionState.Connecting)
                    ) mutableState.value = next
                }
            }
        }
    }

    @Synchronized
    fun publish(state: ConnectionState) {
        mutableState.value = state
    }

    @Synchronized
    fun detach() {
        generation++
        collector?.cancel()
        collector = null
        mutableState.value = ConnectionState.Disconnected
    }
}
