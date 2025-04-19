package com.surendramaran.yolov8tflite

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.surendramaran.yolov8tflite.databinding.ItemVideoBinding
import java.io.File

class VideoListAdapter(
    private val videoFiles: List<File>,
    private val listener: OnItemClickListener
) : RecyclerView.Adapter<VideoListAdapter.VideoViewHolder>() {

    interface OnItemClickListener {
        fun onItemClick(file: File)
    }

    inner class VideoViewHolder(val binding: ItemVideoBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                listener.onItemClick(videoFiles[adapterPosition])
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = ItemVideoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VideoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val file = videoFiles[position]
        holder.binding.videoName.text = file.name
    }

    override fun getItemCount(): Int = videoFiles.size
}