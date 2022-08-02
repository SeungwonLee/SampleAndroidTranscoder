package com.example.newandroidtranscoder

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.Future
import net.ypresto.androidtranscoder.MediaTranscoder
import net.ypresto.androidtranscoder.format.MediaFormatStrategyPresets

class MainActivity : Activity() {
    private var mFuture: Future<Void>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transcoder)
        findViewById<View>(R.id.select_video_button).setOnClickListener {
            startActivityForResult(
                Intent(Intent.ACTION_GET_CONTENT)
                    .setType("video/*"),
                REQUEST_CODE_PICK
            )
        }
        findViewById<View>(R.id.cancel_button).setOnClickListener {
            mFuture!!.cancel(
                true
            )
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        when (requestCode) {
            REQUEST_CODE_PICK -> {
                val file: File
                if (resultCode == RESULT_OK) {
                    file = try {
                        val outputDir = File(getExternalFilesDir(null), "outputs")
                        outputDir.mkdir()
                        File.createTempFile("transcode_test", ".mp4", outputDir)
                    } catch (e: IOException) {
                        Log.e(TAG, "Failed to create temporary file.", e)
                        Toast.makeText(this, "Failed to create temporary file.", Toast.LENGTH_LONG)
                            .show()
                        return
                    }
                    val resolver = contentResolver
                    val parcelFileDescriptor: ParcelFileDescriptor? = try {
                        resolver.openFileDescriptor(data.data!!, "r")
                    } catch (e: FileNotFoundException) {
                        Log.w("Could not open '" + data.dataString + "'", e)
                        Toast.makeText(
                            this@MainActivity,
                            "File not found.",
                            Toast.LENGTH_LONG
                        ).show()
                        return
                    }
                    val fileDescriptor = parcelFileDescriptor!!.fileDescriptor
                    val progressBar = findViewById<View>(R.id.progress_bar) as ProgressBar
                    progressBar.max = PROGRESS_BAR_MAX
                    val startTime = SystemClock.uptimeMillis()
                    val listener: MediaTranscoder.Listener = object : MediaTranscoder.Listener {
                        override fun onTranscodeProgress(progress: Double) {
                            if (progress < 0) {
                                progressBar.isIndeterminate = true
                            } else {
                                progressBar.isIndeterminate = false
                                progressBar.progress =
                                    Math.round(progress * PROGRESS_BAR_MAX).toInt()
                            }
                        }

                        override fun onTranscodeCompleted() {
                            Log.d(
                                TAG,
                                "transcoding took " + (SystemClock.uptimeMillis() - startTime) + "ms"
                            )
                            onTranscodeFinished(
                                true,
                                "transcoded file placed on $file", parcelFileDescriptor
                            )
                        }

                        override fun onTranscodeCanceled() {
                            onTranscodeFinished(false, "Transcoder canceled.", parcelFileDescriptor)
                        }

                        override fun onTranscodeFailed(exception: Exception) {
                            onTranscodeFinished(
                                false,
                                "Transcoder error occurred.",
                                parcelFileDescriptor
                            )
                        }
                    }
                    Log.d(TAG, "transcoding into $file")
                    mFuture = MediaTranscoder.getInstance().transcodeVideo(
                        fileDescriptor, file.absolutePath,
                        MediaFormatStrategyPresets.createAndroid720pStrategy(
                            8000 * 1000,
                            128 * 1000,
                            1
                        ), listener
                    )
                    switchButtonEnabled(true)
                }
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.transcoder, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        val id = item.itemId
        return if (id == R.id.action_settings) {
            true
        } else super.onOptionsItemSelected(item)
    }

    private fun onTranscodeFinished(
        isSuccess: Boolean,
        toastMessage: String,
        parcelFileDescriptor: ParcelFileDescriptor?
    ) {
        val progressBar = findViewById<View>(R.id.progress_bar) as ProgressBar
        progressBar.isIndeterminate = false
        progressBar.progress = if (isSuccess) PROGRESS_BAR_MAX else 0
        switchButtonEnabled(false)
        Toast.makeText(this@MainActivity, toastMessage, Toast.LENGTH_LONG).show()
        try {
            parcelFileDescriptor!!.close()
        } catch (e: IOException) {
            Log.w("Error while closing", e)
        }
    }

    private fun switchButtonEnabled(isProgress: Boolean) {
        findViewById<View>(R.id.select_video_button).isEnabled = !isProgress
        findViewById<View>(R.id.cancel_button).isEnabled = isProgress
    }

    companion object {
        private const val TAG = "TranscoderActivity"
        private const val FILE_PROVIDER_AUTHORITY =
            "net.ypresto.androidtranscoder.example.fileprovider"
        private const val REQUEST_CODE_PICK = 1
        private const val PROGRESS_BAR_MAX = 1000
    }
}
