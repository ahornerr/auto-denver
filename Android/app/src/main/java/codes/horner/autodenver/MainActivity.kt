package codes.horner.autodenver

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.SeekBar
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    val DEBOUNCE_MS = 50
    var lastSent = 0L

    val socketClient = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

    val socketRequest = Request.Builder()
            .url("ws://192.168.4.1")
            .build()

    lateinit var socket: WebSocket

    fun setStatus(text: String) {
        runOnUiThread {
            tv_status.text = text
        }
    }

    val socketListener = object : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket?, response: Response?) {
            setStatus("Socket opened")
//            speed.isEnabled = true
//            steering.isEnabled = true
        }

        override fun onFailure(webSocket: WebSocket?, t: Throwable?, response: Response?) {
            setStatus("Socket failed: $t")
        }

        override fun onClosing(webSocket: WebSocket?, code: Int, reason: String?) {
            setStatus("Socket closing: $code $reason")
        }

        override fun onMessage(webSocket: WebSocket?, text: String?) {
            setStatus("Socket string message: $text")
        }

        override fun onMessage(webSocket: WebSocket?, bytes: ByteString?) {
            setStatus("Socket byte message: $bytes")
        }

        override fun onClosed(webSocket: WebSocket?, code: Int, reason: String?) {
            setStatus("Socket closed: $code $reason")
        }
    }

    fun canSend(): Boolean {
        return System.currentTimeMillis() > lastSent + DEBOUNCE_MS
    }

    fun setSpeed(speed: Int) {
        speed_val.text = speed.toString()
        if (canSend()) {
            socket.send("RB$speed")
            lastSent = System.currentTimeMillis()
        }
    }

    fun setSteering(angle: Int) {
        steering_val.text = angle.toString()
        if (canSend()) {
            socket.send("RA$angle")
            lastSent = System.currentTimeMillis()
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
                setSpeed(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
            }
        })

        steering.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
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
