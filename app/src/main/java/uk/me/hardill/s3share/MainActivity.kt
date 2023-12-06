package uk.me.hardill.s3share

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.provider.OpenableColumns
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.S3ClientOptions
import kotlinx.android.synthetic.main.mainactivity.*
import java.io.InputStream


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(androidx.appcompat.R.style.Theme_AppCompat)

        val preferences = PreferenceManager.getDefaultSharedPreferences(this).all
        preferences.forEach {
            Log.d("Preferences", "${it.key} -> ${it.value}")
        }

        when {
            intent?.action == Intent.ACTION_SEND -> {
                val credentials = BasicAWSCredentials(preferences.get("accessKey") as String, preferences.getValue("accessSecret") as String)
                val s3Client = AmazonS3Client(credentials)
                //if path based access
                s3Client.setS3ClientOptions(
                    S3ClientOptions.builder()
                        .setPathStyleAccess(preferences.getValue("pathBased") as Boolean).build()
                )
                val endpoint = preferences.getValue("endpoint") as String
                var https = "s"
                if (!(preferences.getValue("https") as Boolean)) {
                    https = ""
                }
                Log.d("endpoint", getString(R.string.endpoint, https, endpoint))
                s3Client.endpoint = getString(R.string.endpoint, https, endpoint)//preferences.getValue("endpoint") as String

                if (intent.type?.startsWith("image/") == true) {
                    setContentView(R.layout.mainactivity)
                    handleSendImage(intent = intent, s3Client = s3Client)
                } else if (intent.type?.startsWith("video/") == true) {
                    setContentView(R.layout.mainactivity)
                    handleSendVideo(intent = intent, s3Client = s3Client)
                }
            }

            else -> {
                setContentView(R.layout.setttings)
            }
        }
    }

    private fun handleSendImage(intent: Intent, s3Client: AmazonS3Client) {
        (intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let {
            // Update UI to reflect image being shared
            val uri:Uri? = intent.getParcelableExtra(Intent.EXTRA_STREAM)
            preview.setImageURI(uri)
            sendFile(uri!!, s3Client)
        }
    }

    private fun handleSendVideo(intent: Intent, s3Client: AmazonS3Client) {
        (intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let {
            // Update UI to reflect image being shared
            val uri:Uri? = intent.getParcelableExtra(Intent.EXTRA_STREAM)
            val thumb: Bitmap? = createVideoThumb(this, uri!!)
            thumb?.let {
                preview.setImageBitmap(thumb)
            }
            sendFile(uri, s3Client)
        }
    }

//    private fun handleSendMultipleImages(intent: Intent, s3Client: AmazonS3Client) {
//        intent.getParcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)?.let {
//            // Update UI to reflect multiple images being shared
//            var list:ArrayList<Parcelable>? =  intent.getParcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)
//            for (file in list!!) {
//                var uri:Uri = file as Uri
//                sendFile(uri!!, s3Client)
//            }
//        }
//    }

    private fun createVideoThumb(context: Context, uri: Uri): Bitmap? {
        try {
            val mediaMetadataRetriever = MediaMetadataRetriever()
            mediaMetadataRetriever.setDataSource(context, uri)
            return mediaMetadataRetriever.getFrameAtTime(0)
        } catch (ex: Exception) {
        }
        return null

    }

    private fun sendFile(uri: Uri, s3Client: AmazonS3Client) {
        val cursor:Cursor? = contentResolver.query(uri, null, null, null, null)
        cursor?.moveToFirst()
        val nameColumn:Int? = cursor?.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val name:String? = cursor?.getString(nameColumn!!)
        cursor?.close()
        fileName.text = name
        val contentResolver:ContentResolver = this.contentResolver
        val inputStream:InputStream? =  contentResolver.openInputStream(uri)
        val preferences = PreferenceManager.getDefaultSharedPreferences(this).all
        val trans = TransferUtility.builder().context(applicationContext)
            .s3Client(s3Client)
            .defaultBucket(preferences.getValue("bucket") as String)
            .build()
        val observer: TransferObserver = trans.upload(name, inputStream)
        observer.setTransferListener(object: TransferListener {
            override fun onStateChanged(id: Int, state: TransferState?) {
                if (state == TransferState.COMPLETED) {
                    Log.d("msg","success")
                    var https = "s"
                    if (!(preferences.getValue("https") as Boolean)) {
                        https = ""
                    }
                    val endpoint = preferences.getValue("endpoint") as String
                    val bucket = preferences.getValue("bucket") as String
                    if ((preferences.getValue("pathBased") as Boolean)) {
                        link.text = getString(R.string.url_path, https, endpoint, bucket, name )
                    } else {
                        link.text = getString(R.string.url_host, https, bucket, endpoint, name )
                    }
                } else if (state == TransferState.FAILED) {
                    Log.d("msg","failed")
                }
            }

            override fun onProgressChanged(id: Int, bytesCurrent: Long, bytesTotal: Long) {
                progressBar.max = 100
                progressBar.progress = (100 * (bytesCurrent/bytesTotal)).toInt()
            }

            override fun onError(id: Int, ex: Exception?) {
                Log.d("error", ex.toString())
            }
        })
    }
}
