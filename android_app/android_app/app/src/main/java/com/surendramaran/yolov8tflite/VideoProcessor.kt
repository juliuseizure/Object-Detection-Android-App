package com.surendramaran.yolov8tflite

import android.content.Context
import android.graphics.*
import android.media.*
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.view.Surface
import java.io.File
import java.util.concurrent.CountDownLatch

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

                val outputFile = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                    getOutputFileName(inputUri)
                )
                val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

                val format = MediaFormat.createVideoFormat("video/avc", width, height).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                    setInteger(MediaFormat.KEY_BIT_RATE, 2_000_000)
                    setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                }

                val encoder = MediaCodec.createEncoderByType("video/avc")
                encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                val inputSurface = encoder.createInputSurface()
                encoder.start()

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

                    feedFrameToEncoder(mutableBitmap, inputSurface)

                    processedFrames++
                    val progress = (processedFrames * 100) / totalFrames
                    progressCallback(progress.coerceIn(0, 100))

                    currentTimeMs += frameIntervalMs
                }

                encoder.signalEndOfInputStream()

                var outputTrackIndex = -1
                var muxerStarted = false

                while (true) {
                    val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
                    if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        break
                    } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val newFormat = encoder.outputFormat
                        outputTrackIndex = muxer.addTrack(newFormat)
                        muxer.start()
                        muxerStarted = true
                    } else if (outputBufferIndex >= 0) {
                        if (!muxerStarted) {
                            throw IllegalStateException("Muxer hasn't started")
                        }
                        val encodedData = encoder.getOutputBuffer(outputBufferIndex) ?: continue
                        muxer.writeSampleData(outputTrackIndex, encodedData, bufferInfo)
                        encoder.releaseOutputBuffer(outputBufferIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            break
                        }
                    }
                }

                encoder.stop()
                encoder.release()
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
        val latch = CountDownLatch(1)
        var detectedBoxes: List<BoundingBox> = emptyList()

        val tempDetector = Detector(context, Constants.MODEL_PATH, Constants.LABELS_PATH, object : Detector.DetectorListener {
            override fun onEmptyDetect() {
                detectedBoxes = emptyList()
                latch.countDown()
            }

            override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
                detectedBoxes = boundingBoxes
                latch.countDown()
            }
        })

        tempDetector.setup()
        tempDetector.detect(bitmap)
        latch.await()
        tempDetector.clear()

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

    private fun feedFrameToEncoder(bitmap: Bitmap, surface: Surface) {
        val surfaceCanvas = surface.lockCanvas(null)
        surfaceCanvas.drawBitmap(bitmap, 0f, 0f, null)
        surface.unlockCanvasAndPost(surfaceCanvas)
    }

    private fun getOutputFileName(inputUri: Uri): String {
        val originalName = File(inputUri.path ?: "output").nameWithoutExtension
        return "${originalName}_inf.mp4"
    }

    companion object {
        private const val TAG = "VideoProcessor"
    }
}
