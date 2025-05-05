package com.example.planthealthmonitorapplication

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageCapture
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.planthealthmonitorapplication.databinding.CameraLayoutBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.widget.Toast
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.Preview
import androidx.camera.core.CameraSelector
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.camera.core.Camera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.*
import kotlin.concurrent.thread

typealias LumaListener = (luma: Double) -> Unit

class CameraActivity : AppCompatActivity(){
    private lateinit var viewBinding: CameraLayoutBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private var camera: Camera? = null
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var username: String = ""
    
    // Location components
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null
    private var locationPermissionGranted = false
    private val cancellationTokenSource = CancellationTokenSource()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = CameraLayoutBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        username = intent.getStringExtra("username") ?: ""
        viewBinding.backButton.setOnClickListener { finish() }

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        // Check location permission
        locationPermissionGranted = (
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == 
            PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == 
            PackageManager.PERMISSION_GRANTED
        )
        
        // Request all required permissions
        if (allPermissionsGranted()) {
            startCamera()
            if (locationPermissionGranted) {
                getDeviceLocation()
            }
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        // Set up the listeners for take photo and video capture buttons
        viewBinding.imageCaptureButton.setOnClickListener { takePhoto() }
        cameraExecutor = Executors.newSingleThreadExecutor()

        val pinchListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val currentZoomRatio = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f
                val delta = detector.scaleFactor
                val newZoom = currentZoomRatio * delta
                camera?.cameraControl?.setZoomRatio(newZoom)
                Log.d(TAG, "Pinch-to-zoom: scaleFactor=$delta, currentZoom=$currentZoomRatio, newZoom=$newZoom")
                return true
            }
        }
        scaleGestureDetector = ScaleGestureDetector(this, pinchListener)

        viewBinding.viewFinder.setOnTouchListener { view, event ->
            scaleGestureDetector.onTouchEvent(event)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val factory = viewBinding.viewFinder.meteringPointFactory
                    val point = factory.createPoint(event.x, event.y)
                    val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF).build()
                    camera?.cameraControl?.startFocusAndMetering(action)
                    view.performClick()
                    true
                }
                else -> false
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun getDeviceLocation() {
        try {
            if (locationPermissionGranted) {
                // Get the last known location first as a fallback
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        currentLocation = location
                        Log.d(TAG, "Last known location: ${location.latitude}, ${location.longitude}")
                    }
                }
                
                // Then request current location (more accurate)
                val currentLocationTask = fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cancellationTokenSource.token
                )
                
                currentLocationTask.addOnSuccessListener { location ->
                    if (location != null) {
                        currentLocation = location
                        Log.d(TAG, "Current location: ${location.latitude}, ${location.longitude}")
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Exception: ${e.message}", e)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
                
                // Check if location permission specifically was granted
                locationPermissionGranted = (
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == 
                    PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == 
                    PackageManager.PERMISSION_GRANTED
                )
                
                if (locationPermissionGranted) {
                    getDeviceLocation()
                }
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time stamped name and MediaStore entry.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Edenscope-Plant")
            }
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()

        // Set up image capture listener, which is triggered after photo has been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults){
                    val savedUri = output.savedUri
                    val msg = "Photo capture succeeded"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)

                    // Create a unique name for the image to use on AWS.
                    val imageName = username + "_" + UUID.randomUUID().toString() + ".jpg"
                    
                    // Process image in a background thread to keep UI responsive
                    thread {
                        val originalBitmap: Bitmap? = savedUri?.let { uri ->
                            contentResolver.openInputStream(uri)?.use { inputStream ->
                                BitmapFactory.decodeStream(inputStream)
                            }
                        }
    
                        // Adjust the bitmap orientation
                        val finalBitmap = if (originalBitmap != null && savedUri != null) {
                            adjustBitmapOrientation(this@CameraActivity, originalBitmap, savedUri)
                        } else {
                            originalBitmap
                        }
    
                        val byteArrayOutputStream = ByteArrayOutputStream()
                        finalBitmap?.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream)
                        val imageData = byteArrayOutputStream.toByteArray()
    
                        // Create a description based on current time
                        val baseDescription = "Captured at ${SimpleDateFormat("hh:mm a", Locale.US).format(Date())}"
                        
                        // Prepare description with location if available
                        val descriptionWithLocation = if (currentLocation != null) {
                            "$baseDescription at [${String.format("%.5f", currentLocation!!.latitude)}, " +
                            "${String.format("%.5f", currentLocation!!.longitude)}]"
                        } else {
                            baseDescription
                        }
                        
                        // Create a note with all data included in constructor
                        val note = UserData.Note(
                            id = UUID.randomUUID().toString(),
                            name = name,
                            description = descriptionWithLocation,
                            imageName = imageName,
                            healthStatus = UserData.HealthStatus.HEALTHY,
                            latitude = currentLocation?.latitude,
                            longitude = currentLocation?.longitude
                        )
                        
                        // Log location if available
                        if (currentLocation != null) {
                            Log.d(TAG, "Adding location to image: ${currentLocation!!.latitude}, ${currentLocation!!.longitude}")
                        }
                        
                        // Set the image
                        note.image = finalBitmap
                        
                        // Upload to AWS
                        Backend.storeImage(imageName, imageData)
    
                        // Create the note in your backend and update your local data
                        Backend.createNote(note)
                        
                        // Update UI on main thread
                        runOnUiThread {
                            UserData.addNote(note)
                            Toast.makeText(
                                this@CameraActivity,
                                if (note.hasLocation) "Image with location saved" else "Image saved",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        )
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.surfaceProvider = viewBinding.viewFinder.surfaceProvider
                }

            imageCapture = ImageCapture.Builder()
                .build()

            val imageAnalyzer = ImageAnalysis.Builder()
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, LuminosityAnalyzer { luma ->
                        Log.d(TAG, "Average luminosity: $luma")
                    })
                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    fun adjustBitmapOrientation(context: Context, bitmap: Bitmap, imageUri: Uri): Bitmap {
        var rotatedBitmap = bitmap
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(imageUri)
            inputStream?.use { stream ->
                val exif = ExifInterface(stream)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                val rotationDegrees = when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
                if (rotationDegrees != 0) {
                    val matrix = Matrix()
                    matrix.postRotate(rotationDegrees.toFloat())
                    rotatedBitmap = Bitmap.createBitmap(
                        bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                    )
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return rotatedBitmap
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private class LuminosityAnalyzer(private val listener: LumaListener) : ImageAnalysis.Analyzer {

        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()    // Rewind the buffer to zero
            val data = ByteArray(remaining())
            get(data)   // Copy the buffer into a byte array
            return data // Return the byte array
        }

        override fun analyze(image: ImageProxy) {

            val buffer = image.planes[0].buffer
            val data = buffer.toByteArray()
            val pixels = data.map { it.toInt() and 0xFF }
            val luma = pixels.average()

            listener(luma)

            image.close()
        }
    }

    override fun onPause() {
        super.onPause()
        cancellationTokenSource.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        
        // Set flag to refresh photos when returning to MainActivity
        MainActivity.shouldRefreshPhotos = true
        Log.d(TAG, "CameraActivity finishing, set shouldRefreshPhotos=true")
    }

    companion object {
        private const val TAG = "CameraActivity"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }
}