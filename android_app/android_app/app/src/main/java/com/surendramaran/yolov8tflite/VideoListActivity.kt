package com.surendramaran.yolov8tflite

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.surendramaran.yolov8tflite.databinding.ActivityVideoListBinding
import java.io.File

class VideoListActivity : AppCompatActivity(), VideoListAdapter.OnItemClickListener {

    private lateinit var binding: ActivityVideoListBinding
    private lateinit var videoListAdapter: VideoListAdapter
    private lateinit var detector: Detector
    private var loadingDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val videoFiles = getDemoVideos()

        if (videoFiles.isEmpty()) {
            Toast.makeText(this, "No demo videos found.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        videoListAdapter = VideoListAdapter(videoFiles, this)

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@VideoListActivity)
            adapter = videoListAdapter
        }

        // Initialize Detector
        detector = Detector(baseContext, Constants.MODEL_PATH, Constants.LABELS_PATH, object : Detector.DetectorListener {
            override fun onEmptyDetect() {}
            override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {}
        })
        detector.setup()
    }

    private fun getDemoVideos(): List<File> {

        //  THIS IS A HARDCODED PATH FOR DEMO VIDEOS

        val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "proj_vids/mid")
        if (!directory.exists()) {
            return emptyList()
        }
        return directory.listFiles { file -> file.extension.equals("mp4", ignoreCase = true) }?.toList() ?: emptyList()
    }


    override fun onItemClick(file: File) {
        val options = arrayOf("Play Video", "Run Inference")

        AlertDialog.Builder(this)
            .setTitle("Choose an action")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> playVideo(file)
                    1 -> runInference(file)
                }
            }
            .show()
    }

    private fun playVideo(file: File) {
        val intent = Intent(this, VideoPlayerActivity::class.java)
        intent.putExtra("videoUri", Uri.fromFile(file))
        startActivity(intent)
    }

    private fun runInference(file: File) {
        showLoadingSpinner()

        val processor = VideoProcessor(this, detector)
        processor.processVideo(Uri.fromFile(file)) { outputPath ->
            runOnUiThread {
                hideLoadingSpinner()
                Toast.makeText(this, "✅ Saved to app's Movies folder!", Toast.LENGTH_SHORT).show()

                // Attempt to open Movies folder
                val moviesFolder = getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                val intentShowFolder = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.fromFile(moviesFolder), "resource/folder")
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                try {
                    startActivity(intentShowFolder)
                } catch (e: Exception) {
                    Log.e("VideoListActivity", "Could not open folder: ${e.localizedMessage}")
                }

                // Then open the processed video
                val intent = Intent(this, VideoPlayerActivity::class.java)
                intent.putExtra("videoUri", Uri.fromFile(File(outputPath)))
                startActivity(intent)
            }
        }
    }

    private fun showLoadingSpinner() {
        if (loadingDialog == null) {
            loadingDialog = AlertDialog.Builder(this)
                .setCancelable(false)
                .setView(R.layout.dialog_loading_spinner)
                .create()
        }
        loadingDialog?.show()
    }

    private fun hideLoadingSpinner() {
        loadingDialog?.dismiss()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::detector.isInitialized) {
            detector.clear()
        }
    }
}
