package com.surendramaran.yolov8tflite

import android.content.res.Configuration
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.MediaController
import androidx.appcompat.app.AppCompatActivity
import com.surendramaran.yolov8tflite.databinding.ActivityVideoPlayerBinding

class VideoPlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVideoPlayerBinding
    private lateinit var videoUri: Uri  // 🌟 Saved once!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 🌟 Save videoUri once
        videoUri = intent.getParcelableExtra<Uri>("videoUri") ?: return

        adjustVideoViewAspectRatio()

        val mediaController = MediaController(this)
        mediaController.setAnchorView(binding.videoView)
        binding.videoView.setMediaController(mediaController)
        binding.videoView.setVideoURI(videoUri)
        binding.videoView.start()
    }

    private fun adjustVideoViewAspectRatio() {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(this, videoUri)

            val videoWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt() ?: return
            val videoHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt() ?: return

            retriever.release()

            val layoutParams = binding.videoView.layoutParams as ViewGroup.LayoutParams

            val screenWidth = resources.displayMetrics.widthPixels
            val screenHeight = resources.displayMetrics.heightPixels

            val videoAspectRatio = videoWidth.toFloat() / videoHeight
            val screenAspectRatio = screenWidth.toFloat() / screenHeight

            if (videoAspectRatio > screenAspectRatio) {
                // Wider video
                layoutParams.width = screenWidth
                layoutParams.height = (screenWidth / videoAspectRatio).toInt()
            } else {
                // Taller video
                layoutParams.height = screenHeight
                layoutParams.width = (screenHeight * videoAspectRatio).toInt()
            }

            binding.videoView.layoutParams = layoutParams

        } catch (e: Exception) {
            Log.e("VideoPlayerActivity", "Error adjusting aspect ratio", e)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // 🌟 Adjust layout again after rotation
        adjustVideoViewAspectRatio()
    }
}
