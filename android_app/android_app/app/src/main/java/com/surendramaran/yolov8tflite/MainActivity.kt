package com.surendramaran.yolov8tflite

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.surendramaran.yolov8tflite.Constants.LABELS_PATH
import com.surendramaran.yolov8tflite.Constants.MODEL_PATH
import com.surendramaran.yolov8tflite.databinding.ActivityMainBinding
import java.io.File

class MainActivity : AppCompatActivity(), Detector.DetectorListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var detector: Detector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (allPermissionsGranted()) {
            initializeDetector()
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }

        binding.pickVideoButton.setOnClickListener {
            pickExternalVideo()
        }

        binding.browseDemoVideosButton.setOnClickListener {
            val intent = Intent(this, VideoListActivity::class.java)
            startActivity(intent)
        }
    }

    private fun initializeDetector() {
        detector = Detector(baseContext, MODEL_PATH, LABELS_PATH, this)
        detector.setup()
    }

    private fun pickExternalVideo() {
        pickExternalVideoLauncher.launch("video/*")
    }

    private val pickExternalVideoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            runVideoInference(it)
        }
    }

    private fun runVideoInference(uri: Uri) {
        val processor = VideoProcessor(this, detector)
        processor.processVideo(
            inputUri = uri,
            onComplete = { outputPath ->
                runOnUiThread {
                    val intent = Intent(this, VideoPlayerActivity::class.java)
                    intent.putExtra("videoUri", Uri.fromFile(File(outputPath)))
                    startActivity(intent)
                }
            }
        )
    }

    private fun allPermissionsGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            baseContext,
            Manifest.permission.READ_MEDIA_VIDEO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    baseContext,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            initializeDetector()
        } else {
            Toast.makeText(this, "Permissions not granted. Please allow media access.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::detector.isInitialized) {
            detector.clear()
        }
    }

    override fun onEmptyDetect() {
        // No live detection overlay needed.
    }

    override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
        // No live detection overlay needed.
    }

    companion object {
        private const val TAG = "MainActivity"
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.READ_MEDIA_VIDEO
        )
    }
}