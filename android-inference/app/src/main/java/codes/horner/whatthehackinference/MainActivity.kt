package codes.horner.whatthehackinference

import android.content.*
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import codes.horner.whatthehackinference.models.CommandControlMessage
import codes.horner.whatthehackinference.tasks.ClassifyImageTask
import codes.horner.whatthehackinference.tasks.LoadModelsTask
import codes.horner.whatthehackinference.utils.ImageUtils
import com.google.gson.Gson
import com.qualcomm.qti.snpe.NeuralNetwork
import com.qualcomm.qti.snpe.NeuralNetwork.Runtime.*
import com.qualcomm.qti.snpe.SNPE
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.subjects.ReplaySubject
import kotlinx.android.synthetic.main.activity_camera.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.camera_connection_fragment.*


class MainActivity : CameraActivity() {

    private val inferenceResult = ReplaySubject.create<InferenceResult>(1)
    private val processingTime = ReplaySubject.create<Long>(1)

    val gson = Gson()
    private var usbService: UsbService? = null
    private val DESIRED_PREVIEW_SIZE = Size(640, 480)
    private var timestamp: Long = 0
    private var sensorOrientation: Int? = null
    private lateinit var trackingOverlay: OverlayView
    private var computingDetection = false


    private var lastProcessingTimeMs: Long = 0
    private lateinit var rgbFrameBitmap: Bitmap
    private lateinit var croppedBitmap: Bitmap
    private lateinit var cropCopyBitmap: Bitmap

    private var luminanceCopy: ByteArray? = null

    private lateinit var frameToCropTransform: Matrix
    private lateinit var cropToFrameTransform: Matrix

    private val SAVE_PREVIEW_BITMAP = false
    private val TEXT_SIZE_DIP = 10f

    //    private var detector: Classifier? = null
    private var neuralNetwork: NeuralNetwork? = null
    private var model: Model? = null

    // Minimum detection confidence to track a detection.
    private val MINIMUM_CONFIDENCE_TF_OD_API = 0.6f
    private val MINIMUM_CONFIDENCE_MULTIBOX = 0.1f
    private val MINIMUM_CONFIDENCE_YOLO = 0.25f

    // Configuration values for the prepackaged multibox model.
    private val MB_INPUT_SIZE = 224
    private val MB_IMAGE_MEAN = 128
    private val MB_IMAGE_STD = 128f
    private val MB_INPUT_NAME = "ResizeBilinear"
    private val MB_OUTPUT_LOCATIONS_NAME = "output_locations/Reshape"
    private val MB_OUTPUT_SCORES_NAME = "output_scores/Reshape"
    private val MB_MODEL_FILE = "file:///android_asset/multibox_model.pb"
    private val MB_LOCATION_FILE = "file:///android_asset/multibox_location_priors.txt"

    private val TF_OD_API_INPUT_SIZE = 227
    private val TF_OD_API_MODEL_FILE = "file:///android_asset/ssd_mobilenet_v1_android_export.pb"
    private val TF_OD_API_LABELS_FILE = "file:///android_asset/coco_labels_list.txt"

    // Configuration values for tiny-yolo-voc. Note that the graph is not included with TensorFlow and
    // must be manually placed in the assets/ directory by the user.
    // Graphs and models downloaded from http://pjreddie.com/darknet/yolo/ may be converted e.g. via
    // DarkFlow (https://github.com/thtrieu/darkflow). Sample command:
    // ./flow --model cfg/tiny-yolo-voc.cfg --load bin/tiny-yolo-voc.weights --savepb --verbalise
    private val YOLO_MODEL_FILE = "file:///android_asset/graph-tiny-yolo-voc.pb"
    private val YOLO_INPUT_SIZE = 416
    private val YOLO_INPUT_NAME = "input"
    private val YOLO_OUTPUT_NAMES = "output"
    private val YOLO_BLOCK_SIZE = 32

    private val MODE = DetectorMode.TF_OD_API
    private val MAINTAIN_ASPECT = MODE == DetectorMode.YOLO

    private enum class DetectorMode {
        TF_OD_API, MULTIBOX, YOLO
    }

    override fun processImage() {
        ++timestamp
        val currTimestamp = timestamp
        val originalLuminance = luminance
//        tracker.onFrame(
//                previewWidth,
//                previewHeight,
//                luminanceStride,
//                sensorOrientation!!,
//                originalLuminance,
//                timestamp)
//        trackingOverlay.postInvalidate()
//
//        // No mutex needed as this method is not reentrant.
        if (computingDetection || neuralNetwork == null || model == null) {
            readyForNextImage()
            return
        }
        computingDetection = true
        LOGGER.i("Preparing image $currTimestamp for detection in bg thread.")
//
        rgbFrameBitmap.setPixels(rgbBytes, 0, previewWidth, 0, 0, previewWidth, previewHeight)
//
        if (luminanceCopy == null) {
            luminanceCopy = ByteArray(originalLuminance.size)
        }
        System.arraycopy(originalLuminance, 0, luminanceCopy, 0, originalLuminance.size)
        readyForNextImage()
//
        val canvas = Canvas(croppedBitmap)
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null)
//        // For examining the actual TF input.
        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(croppedBitmap)
        }

        runInBackground {
            LOGGER.i("Running detection on image " + currTimestamp)
            val startTime = SystemClock.uptimeMillis()

            val results = ClassifyImageTask.classify(neuralNetwork, croppedBitmap, model)

            inferenceResult.onNext(InferenceResult(results))

            lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime

            processingTime.onNext(lastProcessingTimeMs)

            Log.d("Results", results.joinToString(" | "))
//
//            cropCopyBitmap = Bitmap.createBitmap(croppedBitmap)
//            val canvas = Canvas(cropCopyBitmap)
//            val paint = Paint()
//            paint.color = Color.RED
//            paint.style = Paint.Style.STROKE
//            paint.strokeWidth = 2.0f
//
//            var minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API
//            when (MODE) {
//                DetectorMode.TF_OD_API -> minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API
//                DetectorMode.MULTIBOX -> minimumConfidence = MINIMUM_CONFIDENCE_MULTIBOX
//                DetectorMode.YOLO -> minimumConfidence = MINIMUM_CONFIDENCE_YOLO
//            }
//
//            val mappedRecognitions = LinkedList<Classifier.Recognition>()
//
//            for (result in results) {
//                val location = result.location
//                if (location != null && result.confidence >= minimumConfidence) {
//                    canvas.drawRect(location, paint)
//
//                    cropToFrameTransform.mapRect(location)
//                    result.location = location
//                    mappedRecognitions.add(result)
//                }
//            }
//
//            tracker.trackResults(mappedRecognitions, luminanceCopy, currTimestamp)
//            trackingOverlay.postInvalidate()
//
//            requestRender()
            computingDetection = false
        }
    }

    override fun onPreviewSizeChosen(size: Size, rotation: Int) {
        Log.d("onPreviewSizeChosen", size.toString() + " | " + rotation)
//        val textSizePx = TypedValue.applyDimension(
//                TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, resources.displayMetrics)
//        borderedText = BorderedText(textSizePx)
//        borderedText.setTypeface(Typeface.MONOSPACE)
//
//        tracker = MultiBoxTracker(this)
//
        var cropSize = TF_OD_API_INPUT_SIZE
//        if (MODE == DetectorMode.YOLO) {
//            detector = TensorFlowYoloDetector.create(
//                    assets,
//                    YOLO_MODEL_FILE,
//                    YOLO_INPUT_SIZE,
//                    YOLO_INPUT_NAME,
//                    YOLO_OUTPUT_NAMES,
//                    YOLO_BLOCK_SIZE)
//            cropSize = YOLO_INPUT_SIZE
//        } else if (MODE == DetectorMode.MULTIBOX) {
//            detector = TensorFlowMultiBoxDetector.create(
//                    assets,
//                    MB_MODEL_FILE,
//                    MB_LOCATION_FILE,
//                    MB_IMAGE_MEAN,
//                    MB_IMAGE_STD,
//                    MB_INPUT_NAME,
//                    MB_OUTPUT_LOCATIONS_NAME,
//                    MB_OUTPUT_SCORES_NAME)
//            cropSize = MB_INPUT_SIZE
//        } else {
        //TODO: Setup classifier/detector
//            try {
//                detector = TensorFlowObjectDetectionAPIModel.create(
//                        assets, TF_OD_API_MODEL_FILE, TF_OD_API_LABELS_FILE, TF_OD_API_INPUT_SIZE)
//                cropSize = TF_OD_API_INPUT_SIZE
//            } catch (e: IOException) {
//                LOGGER.e("Exception initializing classifier!", e)
//                val toast = Toast.makeText(
//                        applicationContext, "Classifier could not be initialized", Toast.LENGTH_SHORT)
//                toast.show()
//                finish()
//            }
//
//        }

        Log.d("Main", "Loading models")
        val models = LoadModelsTask.loadModels(this)
        model = models[0]

        Log.d("Main", "Building network")
        neuralNetwork = buildNeuralNetwork(model!!)

        previewWidth = size.width
        previewHeight = size.height
//
        val screenOrientation = windowManager.defaultDisplay.rotation
//
        LOGGER.i("Sensor orientation: %d, Screen orientation: %d", rotation, screenOrientation)
//
        sensorOrientation = rotation + screenOrientation
//
        LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight)
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888)
        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888)
//
        frameToCropTransform = ImageUtils.getTransformationMatrix(
                previewWidth, previewHeight,
                cropSize, cropSize,
                sensorOrientation!!, MAINTAIN_ASPECT)
//
        cropToFrameTransform = Matrix()
        frameToCropTransform.invert(cropToFrameTransform)
//
//        trackingOverlay = findViewById(R.id.tracking_overlay)
//        trackingOverlay.addCallback(
//                { canvas ->
//                    tracker.draw(canvas)
//                    if (isDebug) {
//                        tracker.drawDebug(canvas)
//                    }
//                })
//
//        addCallback(
//                OverlayView.DrawCallback { canvas ->
//                    if (!isDebug) {
//                        return@DrawCallback
//                    }
//                    val copy = cropCopyBitmap ?: return@DrawCallback
//
//                    val backgroundColor = Color.argb(100, 0, 0, 0)
//                    canvas.drawColor(backgroundColor)
//
//                    val matrix = Matrix()
//                    val scaleFactor = 2f
//                    matrix.postScale(scaleFactor, scaleFactor)
//                    matrix.postTranslate(
//                            canvas.width - copy.width * scaleFactor,
//                            canvas.height - copy.height * scaleFactor)
//                    canvas.drawBitmap(copy, matrix, Paint())
//
//                    val lines = Vector<String>()
//                    if (detector != null) {
//                        val statString = detector.getStatString()
//                        val statLines = statString.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
//                        for (line in statLines) {
//                            lines.add(line)
//                        }
//                    }
//                    lines.add("")
//
//                    lines.add("Frame: " + previewWidth + "x" + previewHeight)
//                    lines.add("Crop: " + copy.width + "x" + copy.height)
//                    lines.add("View: " + canvas.width + "x" + canvas.height)
//                    lines.add("Rotation: " + sensorOrientation)
//                    lines.add("Inference time: " + lastProcessingTimeMs + "ms")
//
//                    borderedText.drawLines(canvas, 10f, canvas.height - 10f, lines)
//                })
    }

    private fun buildNeuralNetwork(model: Model): NeuralNetwork? {
        return SNPE.NeuralNetworkBuilder(application)
                .setDebugEnabled(false)
                .setPerformanceProfile(NeuralNetwork.PerformanceProfile.HIGH_PERFORMANCE)
                .setRuntimeOrder(DSP, GPU, CPU)
                .setModel(model.file)
                .build()
    }

    override fun getLayoutId(): Int {
        return R.layout.camera_connection_fragment_tracking
    }

    override fun getDesiredPreviewFrameSize(): Size {
        return DESIRED_PREVIEW_SIZE
    }

    /*
     * Notifications from UsbService will be received here.
     */
    private val mUsbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbService.ACTION_USB_PERMISSION_GRANTED // USB PERMISSION GRANTED
                -> Toast.makeText(context, "USB Ready", Toast.LENGTH_SHORT).show()
                UsbService.ACTION_USB_PERMISSION_NOT_GRANTED // USB PERMISSION NOT GRANTED
                -> Toast.makeText(context, "USB Permission not granted", Toast.LENGTH_SHORT).show()
                UsbService.ACTION_NO_USB // NO USB CONNECTED
                -> Toast.makeText(context, "No USB connected", Toast.LENGTH_SHORT).show()
                UsbService.ACTION_USB_DISCONNECTED // USB DISCONNECTED
                -> Toast.makeText(context, "USB disconnected", Toast.LENGTH_SHORT).show()
                UsbService.ACTION_USB_NOT_SUPPORTED // USB NOT SUPPORTED
                -> Toast.makeText(context, "USB device not supported", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val usbConnection = object : ServiceConnection {
        override fun onServiceConnected(arg0: ComponentName, arg1: IBinder) {
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            usbService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        setSupportActionBar(toolbar)

        // Example of a call to a native method
//        sample_text.text = stringFromJNI()
    }

    override fun onStart() {
        super.onStart()

        Serial.Device.perLineSubject.subscribe {
            Log.d("Main", "Serial message: $it")
        }

        Serial.Device.latestCar.subscribe {
            Log.d("Latest car", it.toString())

            val steer = CommandControlMessage("steering", it.steering)
            val throttle = CommandControlMessage("throttle", it.throttle)
            Serial.Device.sendQueue.onNext(gson.toJson(steer))
            Serial.Device.sendQueue.onNext(gson.toJson(throttle))
        }

        processingTime.observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    val fps = Math.round(it / 10.0) / 10.0
                    tv_fps.text = "$fps fps"
                }

        inferenceResult.observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    tv_pred.text = it.labels.joinToString(", ")
                }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        setFilters()  // Start listening notifications from UsbService
        startService(UsbService::class.java, usbConnection, null) // Start UsbService(if it was not started before) and Bind it
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(mUsbReceiver)
        unbindService(usbConnection)
    }

    private fun startService(service: Class<*>, serviceConnection: ServiceConnection, extras: Bundle?) {
        if (!UsbService.SERVICE_CONNECTED) {
            val startService = Intent(this, service)
            if (extras != null && !extras.isEmpty) {
                val keys = extras.keySet()
                for (key in keys) {
                    val extra = extras.getString(key)
                    startService.putExtra(key, extra)
                }
            }
            startService(startService)
        }
        val bindingIntent = Intent(this, service)
        bindService(bindingIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun setFilters() {
        val filter = IntentFilter()
        filter.addAction(UsbService.ACTION_USB_PERMISSION_GRANTED)
        filter.addAction(UsbService.ACTION_NO_USB)
        filter.addAction(UsbService.ACTION_USB_DISCONNECTED)
        filter.addAction(UsbService.ACTION_USB_NOT_SUPPORTED)
        filter.addAction(UsbService.ACTION_USB_PERMISSION_NOT_GRANTED)
        registerReceiver(mUsbReceiver, filter)
    }

}
