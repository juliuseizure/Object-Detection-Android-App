package com.surendramaran.yolov8tflite

import android.content.Context
import android.graphics.*
import android.media.*
import android.net.Uri
import android.os.Environment
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

class VideoProcessor(private val context: Context, private val detector: Detector) {

    fun processVideo(
        inputUri: Uri,
        onComplete: (outputPath: String) -> Unit,
        progressCallback: (progress: Int) -> Unit = {}
    ) {
        Thread {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, inputUri)

                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt() ?: 0
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt() ?: 0
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
                val frameRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloat()?.toInt()
                    ?: 30

                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val inferredDir = File(downloadsDir, "proj_vids/inferred")
                if (!inferredDir.exists()) {
                    inferredDir.mkdirs() // create folder if missing
                }
                val outputFile = File(inferredDir, getOutputFileName(inputUri))

                val codec = MediaCodec.createEncoderByType("video/avc")
                val format = MediaFormat.createVideoFormat("video/avc", width, height)
                format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                format.setInteger(MediaFormat.KEY_BIT_RATE, 2_000_000)
                format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

                codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                codec.start()

                val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                var outputTrackIndex = -1
                var muxerStarted = false

                val bufferInfo = MediaCodec.BufferInfo()

                var currentTimeMs = 0L
                val frameIntervalMs = (1000 / frameRate)
                var processedFrames = 0
                val totalFrames = (durationMs / frameIntervalMs).toInt().coerceAtLeast(1)

                while (currentTimeMs < durationMs) {
                    val bitmap = retriever.getFrameAtTime(currentTimeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)

                    if (bitmap == null) {
                        currentTimeMs += frameIntervalMs
                        continue
                    }

                    val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                    val detectedBoxes = detectBoundingBoxesBlocking(mutableBitmap)
                    drawBoundingBoxes(mutableBitmap, detectedBoxes)

                    val yuvBuffer = encodeBitmapToNV21(mutableBitmap)

                    val inputBufferIndex = codec.dequeueInputBuffer(10000)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                        inputBuffer?.clear()
                        inputBuffer?.put(yuvBuffer)
                        val presentationTimeUs = (currentTimeMs * 1000)
                        codec.queueInputBuffer(inputBufferIndex, 0, yuvBuffer.size, presentationTimeUs, 0)
                    }

                    var outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                    while (outputBufferIndex >= 0) {
                        if (!muxerStarted) {
                            val newFormat = codec.outputFormat
                            outputTrackIndex = muxer.addTrack(newFormat)
                            muxer.start()
                            muxerStarted = true
                        }

                        val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                        outputBuffer?.let {
                            muxer.writeSampleData(outputTrackIndex, it, bufferInfo)
                        }
                        codec.releaseOutputBuffer(outputBufferIndex, false)
                        outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                    }

                    processedFrames++
                    val progress = (processedFrames * 100) / totalFrames
                    progressCallback(progress.coerceIn(0, 100))

                    currentTimeMs += frameIntervalMs
                }

                // Send end-of-stream signal
                val inputBufferIndex = codec.dequeueInputBuffer(10000)
                if (inputBufferIndex >= 0) {
                    codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                }

                var outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                while (outputBufferIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                    outputBuffer?.let {
                        muxer.writeSampleData(outputTrackIndex, it, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outputBufferIndex, false)
                    outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                }

                codec.stop()
                codec.release()
                muxer.stop()
                muxer.release()
                retriever.release()

                Log.d(TAG, "Video processing completed: ${outputFile.absolutePath}")
                onComplete(outputFile.absolutePath)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to process video: ${e.localizedMessage}", e)
                onComplete("") // Always call onComplete even on error
            }
        }.start()
    }

    private fun detectBoundingBoxesBlocking(bitmap: Bitmap): List<BoundingBox> {
        var detectedBoxes: List<BoundingBox> = emptyList()

        val originalListener = detector.detectorListener

        detector.detectorListener = object : Detector.DetectorListener {
            override fun onEmptyDetect() {
                detectedBoxes = emptyList()
            }

            override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
                detectedBoxes = boundingBoxes
            }
        }

        detector.detect(bitmap)

        detector.detectorListener = originalListener

        return detectedBoxes
    }

    private fun drawBoundingBoxes(bitmap: Bitmap, boxes: List<BoundingBox>) {
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 36f
            style = Paint.Style.FILL
        }

        val width = bitmap.width
        val height = bitmap.height

        for (box in boxes) {
            val left = box.x1 * width
            val top = box.y1 * height
            val right = box.x2 * width
            val bottom = box.y2 * height
            canvas.drawRect(left, top, right, bottom, paint)
            canvas.drawText(box.clsName, left, top - 10, textPaint)
        }
    }

    private fun encodeBitmapToNV21(bitmap: Bitmap): ByteArray {
        val argb = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(argb, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        val yuv = ByteArray(bitmap.width * bitmap.height * 3 / 2)
        encodeYUV420SP(yuv, argb, bitmap.width, bitmap.height)

        return yuv
    }

    private fun encodeYUV420SP(yuv420sp: ByteArray, argb: IntArray, width: Int, height: Int) {
        val frameSize = width * height

        var yIndex = 0
        var uvIndex = frameSize

        var a: Int
        var R: Int
        var G: Int
        var B: Int
        var Y: Int
        var U: Int
        var V: Int

        var index = 0
        for (j in 0 until height) {
            for (i in 0 until width) {

                a = argb[index] shr 24 and 0xff // unused
                R = argb[index] shr 16 and 0xff
                G = argb[index] shr 8 and 0xff
                B = argb[index] and 0xff

                // well known RGB to YUV algorithm
                Y = ((66 * R + 129 * G + 25 * B + 128) shr 8) + 16
                U = ((-38 * R - 74 * G + 112 * B + 128) shr 8) + 128
                V = ((112 * R - 94 * G - 18 * B + 128) shr 8) + 128

                yuv420sp[yIndex++] = Y.coerceIn(0, 255).toByte()

                if (j % 2 == 0 && i % 2 == 0) {
                    yuv420sp[uvIndex++] = V.coerceIn(0, 255).toByte()
                    yuv420sp[uvIndex++] = U.coerceIn(0, 255).toByte()
                }

                index++
            }
        }
    }

    private fun getOutputFileName(inputUri: Uri): String {
        val originalName = File(inputUri.path ?: "output").nameWithoutExtension
        return "${originalName}_inf.mp4"
    }

    companion object {
        private const val TAG = "VideoProcessor"
    }
}