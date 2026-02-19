package com.yourname.gyroscopesdk

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

class GyroscopeSDK(private val context: Context) {

    private val sensorManager: SensorManager by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    private var eventListener: SensorEventListener? = null

    /**
     * Start listening to gyroscope
     *
     * @param samplingRate Use: SensorManager.SENSOR_DELAY_FASTEST, GAME, UI, NORMAL
     *                     GAME (~50–100 Hz) is usually best balance for smoothness vs battery
     * @param autoLog      Automatically print values to Logcat (tag = "GyroscopeSDK")
     * @param onData       Optional callback with GyroData every time new values arrive
     */
    fun start(
        samplingRate: Int = SensorManager.SENSOR_DELAY_GAME,
        autoLog: Boolean = true,
        onData: ((GyroData) -> Unit)? = null
    ) {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
            ?: throw IllegalStateException("Gyroscope sensor not available on this device")

        eventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val data = GyroData(
                    x = event.values[0],     // rad/s around x-axis
                    y = event.values[1],     // rad/s around y-axis
                    z = event.values[2],     // rad/s around z-axis
                    timestampNs = event.timestamp
                )

                onData?.invoke(data)

                if (autoLog) {
                    Log.d("GyroscopeSDK",
                        "X = %.4f | Y = %.4f | Z = %.4f rad/s".format(data.x, data.y, data.z)
                    )
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // You can log accuracy changes here if needed (rarely happens)
            }
        }

        val success = sensorManager.registerListener(eventListener, sensor, samplingRate)
        if (!success) {
            throw IllegalStateException("Could not register gyroscope listener")
        }
    }

    /**
     * Stop listening and clean up
     */
    fun stop() {
        eventListener?.let {
            sensorManager.unregisterListener(it)
            eventListener = null
        }
    }

    data class GyroData(
        val x: Float,
        val y: Float,
        val z: Float,
        val timestampNs: Long
    )

    companion object {
        // Optional: helper to check if device has gyroscope
        fun hasGyroscope(context: Context): Boolean {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            return sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
        }
    }
}