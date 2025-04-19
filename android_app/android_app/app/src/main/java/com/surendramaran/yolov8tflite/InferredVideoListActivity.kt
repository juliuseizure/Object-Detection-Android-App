package com.surendramaran.yolov8tflite

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.surendramaran.yolov8tflite.databinding.ActivityVideoListBinding
import java.io.File

class InferredVideoListActivity : AppCompatActivity(), VideoListAdapter.OnItemClickListener {

    private lateinit var binding: ActivityVideoListBinding
    private lateinit var videoListAdapter: VideoListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val videoFiles = getInferredVideos()

        if (videoFiles.isEmpty()) {
            Toast.makeText(this, "No inferred videos found.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        videoListAdapter = VideoListAdapter(videoFiles, this)

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@InferredVideoListActivity)
            adapter = videoListAdapter
        }
    }

    private fun getInferredVideos(): List<File> {
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "proj_vids/inferred"
        )
        if (!directory.exists()) {
            return emptyList()
        }
        return directory.listFiles { file -> file.extension.equals("mp4", ignoreCase = true) }?.toList() ?: emptyList()
    }

    override fun onItemClick(file: File) {
        val intent = Intent(this, VideoPlayerActivity::class.java)
        intent.putExtra("videoUri", Uri.fromFile(file))
        startActivity(intent)
    }
}
