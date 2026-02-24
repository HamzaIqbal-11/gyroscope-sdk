//package com.earnscape.gyroscopesdk
//
//import android.content.Context
//import android.hardware.Sensor
//import android.hardware.SensorEvent
//import android.hardware.SensorEventListener
//import android.hardware.SensorManager
//import android.util.Log
//
//class GyroscopeSDK(private val context: Context) {
//
//    private val sensorManager: SensorManager by lazy {
//        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
//    }
//
//    private var eventListener: SensorEventListener? = null
//
//    /**
//     * Start listening to gyroscope
//     *
//     * @param samplingRate Use: SensorManager.SENSOR_DELAY_FASTEST, GAME, UI, NORMAL
//     *                     GAME (~50–100 Hz) is usually best balance for smoothness vs battery
//     * @param autoLog      Automatically print values to Logcat (tag = "GyroscopeSDK")
//     * @param onData       Optional callback with GyroData every time new values arrive
//     */
//    fun start(
//        samplingRate: Int = SensorManager.SENSOR_DELAY_GAME,
//        autoLog: Boolean = true,
//        onData: ((GyroData) -> Unit)? = null
//    ) {
//        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
//            ?: throw IllegalStateException("Gyroscope sensor not available on this device")
//
//        eventListener = object : SensorEventListener {
//            override fun onSensorChanged(event: SensorEvent) {
//                val data = GyroData(
//                    x = event.values[0],     // rad/s around x-axis
//                    y = event.values[1],     // rad/s around y-axis
//                    z = event.values[2],     // rad/s around z-axis
//                    timestampNs = event.timestamp
//                )
//
//                onData?.invoke(data)
//
//                if (autoLog) {
//                    Log.d("GyroscopeSDK",
//                        "X = %.4f | Y = %.4f | Z = %.4f rad/s".format(data.x, data.y, data.z)
//                    )
//                }
//            }
//
//            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
//                // You can log accuracy changes here if needed (rarely happens)
//            }
//        }
//
//        val success = sensorManager.registerListener(eventListener, sensor, samplingRate)
//        if (!success) {
//            throw IllegalStateException("Could not register gyroscope listener")
//        }
//    }
//
//    /**
//     * Stop listening and clean up
//     */
//    fun stop() {
//        eventListener?.let {
//            sensorManager.unregisterListener(it)
//            eventListener = null
//        }
//    }
//
//    data class GyroData(
//        val x: Float,
//        val y: Float,
//        val z: Float,
//        val timestampNs: Long
//    )
//
//    companion object {
//        // Optional: helper to check if device has gyroscope
//        fun hasGyroscope(context: Context): Boolean {
//            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
//            return sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
//        }
//    }
//}

package com.earnscape.gyroscopesdk

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

/**
 * GyroscopeSDK
 * Sirf gyroscope sensor se data lena — kuch aur nahi
 */
class GyroscopeSDK(private val context: Context) {

    companion object {
        private const val TAG = "GyroscopeSDK"
        const val IDLE_THRESHOLD = 0.05f

        fun hasGyroscope(context: Context): Boolean {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            return sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
        }
    }

    private val sensorManager: SensorManager by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    private var eventListener: SensorEventListener? = null
    private var isRunning = false

    /**
     * Start gyroscope sensor
     *
     * @param samplingRate  SensorManager.SENSOR_DELAY_GAME etc
     * @param autoLog       Logcat mein print karo
     * @param onData        Har reading pe callback
     */
    fun start(
        samplingRate: Int = SensorManager.SENSOR_DELAY_GAME,
        autoLog: Boolean = false,
        onData: ((GyroData) -> Unit)? = null
    ) {
        if (isRunning) stop()

        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
            ?: throw IllegalStateException("Gyroscope sensor not available on this device")

        eventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val data = GyroData(
                    x = event.values[0],
                    y = event.values[1],
                    z = event.values[2],
                    timestampNs = event.timestamp
                )
                onData?.invoke(data)
                if (autoLog) {
                    Log.d(TAG, "X=%.4f | Y=%.4f | Z=%.4f rad/s | idle=${data.isIdle}".format(data.x, data.y, data.z))
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        val success = sensorManager.registerListener(eventListener, sensor, samplingRate)
        if (!success) throw IllegalStateException("Could not register gyroscope listener")

        isRunning = true
        Log.d(TAG, "Gyroscope started")
    }

    /** Stop gyroscope sensor */
    fun stop() {
        eventListener?.let {
            sensorManager.unregisterListener(it)
            eventListener = null
        }
        isRunning = false
        Log.d(TAG, "Gyroscope stopped")
    }

    fun isActive() = isRunning

    // ── Data Model ─────────────────────────────────────────────────────────────

    data class GyroData(
        val x: Float,
        val y: Float,
        val z: Float,
        val timestampNs: Long
    ) {
        val magnitude: Float get() = Math.abs(x) + Math.abs(y) + Math.abs(z)
        val isIdle: Boolean get() = magnitude < IDLE_THRESHOLD
    }
}