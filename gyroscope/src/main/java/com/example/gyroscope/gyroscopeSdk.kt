////
////
////package com.earnscape.gyroscopesdk
////
////import android.content.Context
////import android.hardware.Sensor
////import android.hardware.SensorEvent
////import android.hardware.SensorEventListener
////import android.hardware.SensorManager
////import android.util.Log
////
/////**
//// * GyroscopeSDK
//// * Sirf gyroscope sensor se data lena — kuch aur nahi
//// */
////class GyroscopeSDK(private val context: Context) {
////
////    companion object {
////        private const val TAG = "GyroscopeSDK"
////        const val IDLE_THRESHOLD = 0.05f
////
////        fun hasGyroscope(context: Context): Boolean {
////            val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
////            return sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
////        }
////    }
////
////    private val sensorManager: SensorManager by lazy {
////        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
////    }
////
////    private var eventListener: SensorEventListener? = null
////    private var isRunning = false
////
////    /**
////     * Start gyroscope sensor
////     *
////     * @param samplingRate  SensorManager.SENSOR_DELAY_GAME etc
////     * @param autoLog       Logcat mein print karo
////     * @param onData        Har reading pe callback
////     */
////    fun start(
////        samplingRate: Int = SensorManager.SENSOR_DELAY_GAME,
////        autoLog: Boolean = false,
////        onData: ((GyroData) -> Unit)? = null
////    ) {
////        if (isRunning) stop()
////
////        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
////            ?: throw IllegalStateException("Gyroscope sensor not available on this device")
////
////        eventListener = object : SensorEventListener {
////            override fun onSensorChanged(event: SensorEvent) {
////                val data = GyroData(
////                    x = event.values[0],
////                    y = event.values[1],
////                    z = event.values[2],
////                    timestampNs = event.timestamp
////                )
////                onData?.invoke(data)
////                if (autoLog) {
////                    Log.d(TAG, "X=%.4f | Y=%.4f | Z=%.4f rad/s | idle=${data.isIdle}".format(data.x, data.y, data.z))
////                }
////            }
////            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
////        }
////
////        val success = sensorManager.registerListener(eventListener, sensor, samplingRate)
////        if (!success) throw IllegalStateException("Could not register gyroscope listener")
////
////        isRunning = true
////        Log.d(TAG, "Gyroscope started")
////    }
////
////    /** Stop gyroscope sensor */
////    fun stop() {
////        eventListener?.let {
////            sensorManager.unregisterListener(it)
////            eventListener = null
////        }
////        isRunning = false
////        Log.d(TAG, "Gyroscope stopped")
////    }
////
////    fun isActive() = isRunning
////
////    // ── Data Model ─────────────────────────────────────────────────────────────
////
////    data class GyroData(
////        val x: Float,
////        val y: Float,
////        val z: Float,
////        val timestampNs: Long
////    ) {
////        val magnitude: Float get() = Math.abs(x) + Math.abs(y) + Math.abs(z)
////        val isIdle: Boolean get() = magnitude < IDLE_THRESHOLD
////    }
////}
//
//package com.earnscape.gyroscopesdk
//
//import android.content.Context
//import android.hardware.Sensor
//import android.hardware.SensorEvent
//import android.hardware.SensorEventListener
//import android.hardware.SensorManager
//import android.util.Log
//
///**
// * GyroscopeSDK
// * Gyroscope + Accelerometer sensor data
// */
//class GyroscopeSDK(private val context: Context) {
//
//    companion object {
//        private const val TAG = "GyroscopeSDK"
//        const val IDLE_THRESHOLD = 0.05f
//
//        fun hasGyroscope(context: Context): Boolean {
//            val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
//            return sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
//        }
//    }
//
//    private val sensorManager: SensorManager by lazy {
//        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
//    }
//
//    private var gyroListener: SensorEventListener? = null
//    private var accelListener: SensorEventListener? = null
//    private var isRunning = false
//
//    // Latest accelerometer values — gyro callback reads these
//    @Volatile var accelX: Float = 0f; private set
//    @Volatile var accelY: Float = 0f; private set
//    @Volatile var accelZ: Float = 0f; private set
//
//    /**
//     * Start gyroscope + accelerometer sensors
//     */
//    fun start(
//        samplingRate: Int = SensorManager.SENSOR_DELAY_GAME,
//        autoLog: Boolean = false,
//        onData: ((GyroData) -> Unit)? = null
//    ) {
//        if (isRunning) stop()
//
//        // ── Accelerometer ──
//        val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
//        if (accelSensor != null) {
//            accelListener = object : SensorEventListener {
//                override fun onSensorChanged(event: SensorEvent) {
//                    accelX = event.values[0]
//                    accelY = event.values[1]
//                    accelZ = event.values[2]
//                }
//                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
//            }
//            sensorManager.registerListener(accelListener, accelSensor, samplingRate)
//            Log.d(TAG, "✅ Accelerometer started")
//        } else {
//            Log.w(TAG, "⚠️ No accelerometer sensor found")
//        }
//
//        // ── Gyroscope ──
//        val gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
//            ?: throw IllegalStateException("Gyroscope sensor not available on this device")
//
//        gyroListener = object : SensorEventListener {
//            override fun onSensorChanged(event: SensorEvent) {
//                val data = GyroData(
//                    x = event.values[0],
//                    y = event.values[1],
//                    z = event.values[2],
//                    timestampNs = event.timestamp,
//                    ax = accelX,
//                    ay = accelY,
//                    az = accelZ
//                )
//                onData?.invoke(data)
//                if (autoLog) {
//                    Log.d(TAG, "Gyro X=%.4f Y=%.4f Z=%.4f | Accel X=%.4f Y=%.4f Z=%.4f | idle=${data.isIdle}"
//                        .format(data.x, data.y, data.z, data.ax, data.ay, data.az))
//                }
//            }
//            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
//        }
//
//        val success = sensorManager.registerListener(gyroListener, gyroSensor, samplingRate)
//        if (!success) throw IllegalStateException("Could not register gyroscope listener")
//
//        isRunning = true
//        Log.d(TAG, "✅ Gyroscope + Accelerometer started")
//    }
//
//    fun stop() {
//        gyroListener?.let { sensorManager.unregisterListener(it); gyroListener = null }
//        accelListener?.let { sensorManager.unregisterListener(it); accelListener = null }
//        accelX = 0f; accelY = 0f; accelZ = 0f
//        isRunning = false
//        Log.d(TAG, "Gyroscope + Accelerometer stopped")
//    }
//
//    fun isActive() = isRunning
//
//    // ── Data Model ─────────────────────────────────────────────────────────
//
//    data class GyroData(
//        val x: Float,
//        val y: Float,
//        val z: Float,
//        val timestampNs: Long,
//        val ax: Float = 0f,
//        val ay: Float = 0f,
//        val az: Float = 0f
//    ) {
//        val magnitude: Float get() = Math.abs(x) + Math.abs(y) + Math.abs(z)
//        val isIdle: Boolean get() = magnitude < IDLE_THRESHOLD
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
 * Gyroscope + Accelerometer sensor data
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

    private var gyroListener: SensorEventListener? = null
    private var accelListener: SensorEventListener? = null
    private var isRunning = false
    private var hasGyro = false

    // Latest accelerometer values — gyro callback reads these
    @Volatile var accelX: Float = 0f; private set
    @Volatile var accelY: Float = 0f; private set
    @Volatile var accelZ: Float = 0f; private set

    /**
     * Start gyroscope + accelerometer sensors
     */
    fun start(
        samplingRate: Int = SensorManager.SENSOR_DELAY_GAME,
        autoLog: Boolean = false,
        onData: ((GyroData) -> Unit)? = null
    ) {
        if (isRunning) stop()

        // ── Accelerometer ──
        val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelSensor != null) {
            accelListener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    accelX = event.values[0]
                    accelY = event.values[1]
                    accelZ = event.values[2]

                    // If no gyroscope, use accel callback to send data
                    if (!hasGyro) {
                        val data = GyroData(
                            x = 0f, y = 0f, z = 0f,
                            timestampNs = event.timestamp,
                            ax = accelX, ay = accelY, az = accelZ
                        )
                        onData?.invoke(data)
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sensorManager.registerListener(accelListener, accelSensor, samplingRate)
            Log.d(TAG, "✅ Accelerometer started")
        } else {
            Log.w(TAG, "⚠️ No accelerometer sensor found")
        }

        // ── Gyroscope (optional) ──
        val gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        hasGyro = gyroSensor != null

        if (gyroSensor != null) {
            gyroListener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val data = GyroData(
                        x = event.values[0],
                        y = event.values[1],
                        z = event.values[2],
                        timestampNs = event.timestamp,
                        ax = accelX, ay = accelY, az = accelZ
                    )
                    onData?.invoke(data)
                    if (autoLog) {
                        Log.d(TAG, "Gyro X=%.4f Y=%.4f Z=%.4f | Accel X=%.4f Y=%.4f Z=%.4f | idle=${data.isIdle}"
                            .format(data.x, data.y, data.z, data.ax, data.ay, data.az))
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sensorManager.registerListener(gyroListener, gyroSensor, samplingRate)
            Log.d(TAG, "✅ Gyroscope started")
        } else {
            Log.w(TAG, "⚠️ No gyroscope — using accelerometer only")
        }

        // At least one sensor must be available
        if (accelSensor == null && gyroSensor == null) {
            throw IllegalStateException("No motion sensors available on this device")
        }

        isRunning = true
        Log.d(TAG, "✅ Sensors started (gyro=${hasGyro}, accel=${accelSensor != null})")
    }

    fun stop() {
        gyroListener?.let { sensorManager.unregisterListener(it); gyroListener = null }
        accelListener?.let { sensorManager.unregisterListener(it); accelListener = null }
        accelX = 0f; accelY = 0f; accelZ = 0f
        isRunning = false
        Log.d(TAG, "Gyroscope + Accelerometer stopped")
    }

    fun isActive() = isRunning

    // ── Data Model ─────────────────────────────────────────────────────────

    data class GyroData(
        val x: Float,
        val y: Float,
        val z: Float,
        val timestampNs: Long,
        val ax: Float = 0f,
        val ay: Float = 0f,
        val az: Float = 0f
    ) {
        val magnitude: Float get() = Math.abs(x) + Math.abs(y) + Math.abs(z)
        val isIdle: Boolean get() = magnitude < IDLE_THRESHOLD
    }
}