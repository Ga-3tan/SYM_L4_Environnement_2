package ch.heigvd.iict.sym_labo4.viewmodels

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.data.Data
import no.nordicsemi.android.ble.observer.ConnectionObserver
import androidx.lifecycle.LiveData
import no.nordicsemi.android.ble.callback.DataReceivedCallback
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*


/**
 * Project: Labo4
 * Created by fabien.dutoit on 11.05.2019
 * Updated by fabien.dutoit on 18.10.2021
 * (C) 2019 - HEIG-VD, IICT
 */
class BleOperationsViewModel(application: Application) : AndroidViewModel(application) {

    private var ble = SYMBleManager(application.applicationContext)
    private var mConnection: BluetoothGatt? = null

    // Device live data
    val isConnected = MutableLiveData(false)
    val deviceTemperature = MutableLiveData<Int>()
    val deviceNbClicks = MutableLiveData<Int>()
    val deviceDatetime = MutableLiveData<String>()

    // Services UUIDs
    private val timeServiceUUID = UUID.fromString("00001805-0000-1000-8000-00805f9b34fb")
    private val symServiceUUID  = UUID.fromString("3c0a1000-281d-4b48-b2a7-f15579a1c38f")

    // Characteristic UUIDs
    private val currentTimeCharUUID = UUID.fromString("00002A2B-0000-1000-8000-00805f9b34fb")
    private val integerCharUUID     = UUID.fromString("3c0a1001-281d-4b48-b2a7-f15579a1c38f")
    private val temperatureCharUUID = UUID.fromString("3c0a1002-281d-4b48-b2a7-f15579a1c38f")
    private val buttonClickCharUUID = UUID.fromString("3c0a1003-281d-4b48-b2a7-f15579a1c38f")


    //Services and Characteristics of the SYM Pixl
    private var timeService: BluetoothGattService? = null
    private var symService: BluetoothGattService? = null
    private var currentTimeChar: BluetoothGattCharacteristic? = null
    private var integerChar: BluetoothGattCharacteristic? = null
    private var temperatureChar: BluetoothGattCharacteristic? = null
    private var buttonClickChar: BluetoothGattCharacteristic? = null

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "onCleared")
        ble.disconnect()
    }

    fun connect(device: BluetoothDevice) {
        Log.d(TAG, "User request connection to: $device")
        if (!isConnected.value!!) {
            ble.connect(device)
                    .retry(1, 100)
                    .useAutoConnect(false)
                    .enqueue()
        }
    }

    fun disconnect() {
        Log.d(TAG, "User request disconnection")
        ble.disconnect()
        mConnection?.disconnect()
    }

    fun readTemperature(): Boolean {
        if (!isConnected.value!! || temperatureChar == null)
            return false
        else
            return ble.readTemperature()
    }

    fun sendInt(number: Int): Boolean {
        if (!isConnected.value!! || integerChar == null)
            return false
        else
            return ble.sendInt(number)
    }

    fun updateDatetime(): Boolean {
        if(!isConnected.value!! || currentTimeChar == null)
            return false
        else
            return ble.updateDatetime()
    }

    private val bleConnectionObserver: ConnectionObserver = object : ConnectionObserver {
        override fun onDeviceConnecting(device: BluetoothDevice) {
            Log.d(TAG, "onDeviceConnecting")
            isConnected.value = false
        }

        override fun onDeviceConnected(device: BluetoothDevice) {
            Log.d(TAG, "onDeviceConnected")
            isConnected.value = true
        }

        override fun onDeviceDisconnecting(device: BluetoothDevice) {
            Log.d(TAG, "onDeviceDisconnecting")
            isConnected.value = false
        }

        override fun onDeviceReady(device: BluetoothDevice) {
            Log.d(TAG, "onDeviceReady")
        }

        override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
            Log.d(TAG, "onDeviceFailedToConnect")
        }

        override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
            if(reason == ConnectionObserver.REASON_NOT_SUPPORTED) {
                Log.d(TAG, "onDeviceDisconnected - not supported")
                Toast.makeText(getApplication(), "Device not supported - implement method isRequiredServiceSupported()", Toast.LENGTH_LONG).show()
            }
            else
                Log.d(TAG, "onDeviceDisconnected")
            isConnected.value = false
        }

    }

    private inner class SYMBleManager(applicationContext: Context) : BleManager(applicationContext) {
        /**
         * BluetoothGatt callbacks object.
         */
        private var mGattCallback: BleManagerGattCallback? = null

        public override fun getGattCallback(): BleManagerGattCallback {
            //we initiate the mGattCallback on first call, singleton
            if (mGattCallback == null) {
                mGattCallback = object : BleManagerGattCallback() {

                    public override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
                        mConnection = gatt //trick to force disconnection

                        // Vérification des services et des caractéristiques
                        for (i in gatt.services){
                            if (i.uuid == timeServiceUUID){
                                timeService = i
                                for (j in i.characteristics) {
                                    if (j.uuid == currentTimeCharUUID){
                                        currentTimeChar = j
                                    }
                                }
                            }
                            else if (i.uuid == symServiceUUID){
                                symService = i
                                for (j in i.characteristics) {
                                    if (j.uuid == integerCharUUID){
                                        integerChar = j
                                    }
                                    if (j.uuid == buttonClickCharUUID){
                                        buttonClickChar = j
                                    }
                                    if (j.uuid == temperatureCharUUID){
                                        temperatureChar = j
                                    }
                                }
                            }
                        }

                        // Check references
                        if (currentTimeChar == null ||
                            integerChar     == null ||
                            temperatureChar == null ||
                            buttonClickChar == null) {
                            return false
                        }
                        return true
                    }

                    override fun initialize() {

                        // Number of buttons clicked notification and the corresponding callback.
                        setNotificationCallback(buttonClickChar).with { _: BluetoothDevice?, data: Data ->
                            deviceNbClicks.setValue(data.getIntValue(Data.FORMAT_UINT8, 0))
                        }
                        enableNotifications(buttonClickChar).enqueue()

                        // Datetime notification and the corresponding callback.
                        setNotificationCallback(currentTimeChar).with { _: BluetoothDevice?, data: Data ->
                            //Date
                            val year = data.getIntValue(Data.FORMAT_UINT16, 0).toString()
                            val month = data.getIntValue(Data.FORMAT_UINT8, 2).toString()
                            val day = data.getIntValue(Data.FORMAT_UINT8, 3).toString()

                            //Hours
                            val hour = data.getIntValue(Data.FORMAT_UINT8, 4).toString()
                            val minutes = data.getIntValue(Data.FORMAT_UINT8, 5).toString()
                            val seconds = data.getIntValue(Data.FORMAT_UINT8, 6).toString()

                            deviceDatetime.setValue("$day/$month/$year $hour:$minutes:$seconds")
                        }
                        enableNotifications(currentTimeChar).enqueue()
                    }

                    override fun onServicesInvalidated() {
                        //we reset services and characteristics
                        timeService = null
                        currentTimeChar = null
                        symService = null
                        integerChar = null
                        temperatureChar = null
                        buttonClickChar = null
                    }
                }
            }
            return mGattCallback!!
        }

        fun readTemperature(): Boolean {
            return if(temperatureChar != null) {
                readCharacteristic(temperatureChar).with { _: BluetoothDevice?, data: Data ->
                    deviceTemperature.setValue(
                        data.getIntValue(Data.FORMAT_UINT16, 0)!! / 10
                    )
                }.enqueue()
                true
            }else{
                false
            }
        }

        fun sendInt(value: Int): Boolean {
            return if(integerChar != null) {
                integerChar!!.setValue(value, Data.FORMAT_UINT32, 0)
                writeCharacteristic(integerChar, integerChar!!.value, WRITE_TYPE_DEFAULT).enqueue()
                true
            } else{
                false
            }
        }

        fun updateDatetime(): Boolean {
            // We use the system calendar to setup the current datetime
            val calendar: Calendar = Calendar.getInstance()

            // Hours
            val hour: Int = calendar.get(Calendar.HOUR_OF_DAY)
            val minutes: Int = calendar.get(Calendar.MINUTE)
            val seconds: Int = calendar.get(Calendar.SECOND)

            // Date
            val day: Int = calendar.get(Calendar.DAY_OF_MONTH)
            val month: Int = calendar.get(Calendar.MONTH) + 1
            val year: Int = calendar.get(Calendar.YEAR)

            return if(currentTimeChar != null){
                currentTimeChar!!.setValue(year,    Data.FORMAT_UINT16, 0)
                currentTimeChar!!.setValue(month,   Data.FORMAT_UINT8, 2)
                currentTimeChar!!.setValue(day,     Data.FORMAT_UINT8, 3)
                currentTimeChar!!.setValue(hour,    Data.FORMAT_UINT8, 4)
                currentTimeChar!!.setValue(minutes, Data.FORMAT_UINT8, 5)
                currentTimeChar!!.setValue(seconds, Data.FORMAT_UINT8, 6)
                writeCharacteristic(currentTimeChar, currentTimeChar!!.value, WRITE_TYPE_DEFAULT).enqueue()
                true
            }else{
                false
            }

        }
    }

    companion object {
        private val TAG = BleOperationsViewModel::class.java.simpleName
    }

    init {
        ble.setConnectionObserver(bleConnectionObserver)
    }

}