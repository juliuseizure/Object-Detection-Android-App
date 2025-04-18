package com.surendramaran.yolov8tflite

import android.net.Uri
import android.os.Bundle
import android.widget.MediaController
import androidx.appcompat.app.AppCompatActivity
import com.surendramaran.yolov8tflite.databinding.ActivityVideoPlayerBinding

class VideoPlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVideoPlayerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val videoUri = intent.getParcelableExtra<Uri>("videoUri") ?: return

        val mediaController = MediaController(this)
        mediaController.setAnchorView(binding.videoView)

        binding.videoView.apply {
            setVideoURI(videoUri)
            setMediaController(mediaController)
            setOnPreparedListener {
                start()
            }
        }
    }
}
