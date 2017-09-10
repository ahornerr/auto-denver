package codes.horner.autodenver

import android.os.Bundle
import android.os.Handler
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.SeekBar
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private val TAG = "CAR"

    var lastSent = 0L

    val frequencyHz = 50
    val debounceMs = 1000 / frequencyHz

    val socketClient = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

    val socketRequest = Request.Builder()
            .url("ws://192.168.4.1")
            .build()

    var socket: WebSocket? = null

    val ports = arrayOf(
            // Port A - Steering
            Port("A", Pwm(frequencyHz = 50, maxAngle = 90, minPulse = 1000, maxPulse = 2000)),
            // Port B - Throttle
            Port("B", Pwm(frequencyHz = 50, maxAngle = 180, minPulse = 615, maxPulse = 2390))
    )

    data class Port(val which: String, val pwm: Pwm)

    data class Pwm(val frequencyHz: Int, val maxAngle: Int, val minPulse: Int, val maxPulse: Int)

    fun setStatus(text: String) {
        runOnUiThread {
            tv_status.text = text
        }
    }

    fun initPwm(webSocket: WebSocket?) {
        runOnUiThread {
            tv_status.text = ""
        }
        for ((which, pwm) in ports) {
            val msg = "Initializing port $which with PWM values: $pwm"
            Log.d(TAG, msg)
            runOnUiThread {
                tv_status.text = "${tv_status.text}$msg\n\n"
            }
            webSocket?.send("C${which}F${pwm.frequencyHz}")
            webSocket?.send("C${which}D${pwm.maxAngle}")
            webSocket?.send("C${which}P${pwm.minPulse}")
            webSocket?.send("C${which}Q${pwm.maxPulse}")
        }
    }

    val socketListener = object : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response?) {
            setStatus("Socket opened")
            initPwm(webSocket)
            setStatus("PWM initialized")
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable?, response: Response?) {
            setStatus("Socket failed: $t")
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String?) {
            setStatus("Socket closing: $code $reason")
        }

        override fun onMessage(webSocket: WebSocket, text: String?) {
            setStatus("Socket string message: $text")
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString?) {
            setStatus("Socket byte message: $bytes")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String?) {
            setStatus("Socket closed: $code $reason")
        }
    }

    fun canSend(): Boolean {
        if (socket == null) {
            runOnUiThread {
                tv_status.text = "Socket not initialized!"
            }
        }
        return System.currentTimeMillis() > lastSent + debounceMs
    }

    fun setSpeed(speed: Int) {
        speed_val.text = speed.toString()
        if (canSend()) {
            Log.d(TAG, "Sending speed value of $speed")
            socket?.send("RB$speed")
            lastSent = System.currentTimeMillis()
        } else {
            Log.d(TAG, "Not sending speed value of $speed because debounce")
        }
    }

    fun setSteering(angle: Int) {
        steering_val.text = angle.toString()
        if (canSend()) {
            Log.d(TAG, "Sending steering value of $angle")
            socket?.send("RA$angle")
            lastSent = System.currentTimeMillis()
        } else {
            Log.d(TAG, "Not sending steering value of $angle")
        }
    }

    fun connect() {
        socket = socketClient.newWebSocket(socketRequest, socketListener)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btn_connect.setOnClickListener {
            connect()
        }

        speed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) setSpeed(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                val midPoint = ports[1].pwm.maxAngle / 2
                seekBar.progress = midPoint
                Handler().postDelayed({
                    setSpeed(midPoint)
                }, debounceMs.toLong())
            }
        })

        steering.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                setSteering(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
            }
        })

        // Trigger shutdown of the dispatcher's executor so this process can exit cleanly.
//        socketClient.dispatcher().executorService().shutdown()
    }
}
