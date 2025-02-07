package com.genymobile.gnirehtet.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.genymobile.gnirehtet.myadb.AdbConnection
import com.cgutman.adblib.AdbCrypto
import com.genymobile.gnirehtet.myadb.UsbChannel
import com.genymobile.gnirehtet.mydadb.UsbDadb
import com.genymobile.gnirehtet.mygnirehtet.Relay
import java.util.function.Consumer

class UIViewModel : ViewModel() {
    private val mutableCurrentView= MutableLiveData<String>()
    private val mutableDadb = MutableLiveData<UsbDadb>()
    private val mutableActiveConnection = MutableLiveData(false)
    private val mutableForwarder = MutableLiveData<AutoCloseable>()
    private val mutableReverser = MutableLiveData<AutoCloseable>()
    private val mutableRelay = MutableLiveData<Relay>()
    private val mutableRelayThread = MutableLiveData<Thread>()
    private val mutableAdbCrypto = MutableLiveData<AdbCrypto>()
    private val mutableUsbChannel = MutableLiveData<UsbChannel>()
    private val mutableAdbConnection = MutableLiveData<AdbConnection>()
    private val mutableEnableForwardConnection = MutableLiveData<Consumer<Boolean>>()
    private val mutableEnableReverseConnection = MutableLiveData<Consumer<Boolean>>()
    val dadb: LiveData<UsbDadb> get() = mutableDadb
    val currentView: LiveData<String> get() = mutableCurrentView
    val activeConnection: LiveData<Boolean> get() = mutableActiveConnection
    val forwarder: LiveData<AutoCloseable> get() = mutableForwarder
    val reverser: LiveData<AutoCloseable> get() = mutableReverser
    val relay: LiveData<Relay> get() = mutableRelay
    val relayThread: LiveData<Thread> get() = mutableRelayThread
    val adbCrypto: LiveData<AdbCrypto> get() = mutableAdbCrypto
    val usbChannel: LiveData<UsbChannel> get() = mutableUsbChannel
    val adbConnection: LiveData<AdbConnection> get() = mutableAdbConnection
    val enableForwardConnection: LiveData<Consumer<Boolean>> get() = mutableEnableForwardConnection
    val enableReverseConnection: LiveData<Consumer<Boolean>> get() = mutableEnableReverseConnection

    fun changeFragment(fragment: String) {
        mutableCurrentView.value = fragment
    }

    fun setDadb(dadb: UsbDadb?) {
        mutableDadb.postValue(dadb)
    }

    fun setActiveConnection(active: Boolean) {
        mutableActiveConnection.value = active
    }

    fun setForwarder(forwarder: AutoCloseable?) {
        mutableForwarder.value = forwarder
    }

    fun setReverser(reverser: AutoCloseable?) {
        mutableReverser.value = reverser
    }

    fun setRelay(relay: Relay?) {
        mutableRelay.value = relay
    }

    fun setRelayThread(thread: Thread?) {
        mutableRelayThread.value = thread
    }

    fun setAdbCrypto(adbCrypto: AdbCrypto?) {
        mutableAdbCrypto.value = adbCrypto
    }

    fun setUsbChannel(usbChannel: UsbChannel?) {
        mutableUsbChannel.postValue(usbChannel)
    }

    fun setAdbConnection(adbConnection: AdbConnection?) {
        mutableAdbConnection.postValue(adbConnection)
    }

    fun setEnableForwardConnection(consumer: Consumer<Boolean>) {
        mutableEnableForwardConnection.postValue(consumer)
    }

    fun setEnableReverseConnection(consumer: Consumer<Boolean>) {
        mutableEnableReverseConnection.postValue(consumer)
    }
}