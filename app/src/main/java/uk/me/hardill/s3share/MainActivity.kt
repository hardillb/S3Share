package uk.me.hardill.s3share

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.provider.OpenableColumns
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.app.ActivityCompat
import androidx.preference.PreferenceManager
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.S3ClientOptions
import uk.me.hardill.s3share.databinding.MainactivityBinding


class MainActivity : AppCompatActivity() {

    private lateinit var binding: MainactivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MainactivityBinding.inflate(layoutInflater)
        val view = binding.root
        setTheme(androidx.appcompat.R.style.Theme_AppCompat)

        val preferences = PreferenceManager.getDefaultSharedPreferences(this).all

        when {
            intent?.action == Intent.ACTION_SEND -> {

                setContentView(binding.root)
                findViewById<AppCompatImageButton>(R.id.shareButton)
                    .setOnClickListener {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip: ClipData = ClipData.newPlainText("item link", binding.link.text)
                        clipboard.setPrimaryClip(clip)
                    }

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
                s3Client.endpoint = getString(R.string.endpoint, https, endpoint)//preferences.getValue("endpoint") as String

                if (intent.type?.startsWith("image/") == true) {
                    val uri = intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as Uri
                    Log.d("URI", uri.toString())
                    binding.preview.setImageURI(uri)
                    val cursor:Cursor? = contentResolver.query(uri, null, null, null, null)
                    cursor?.moveToFirst()
                    val nameColumn:Int? = cursor?.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val name:String? = cursor?.getString(nameColumn!!)
                    cursor?.close()
                    binding.fileName.text = name

                    var backgroundIntent = Intent(this, BackgroundService::class.java)
                    backgroundIntent.type = intent.type
                    backgroundIntent.putExtra(Intent.EXTRA_STREAM, uri)
                    backgroundIntent.setAction("uk.me.hardill.s3share.action.UPLOAD")
                    startService(backgroundIntent)
//                    handleSendImage(intent = intent, s3Client = s3Client)
                } else if (intent.type?.startsWith("video/") == true) {
                    val uri = intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as Uri
                    Log.d("URI", uri.toString())
                    val thumb: Bitmap? = createVideoThumb(this, uri!!)
                    thumb?.let {
                        binding.preview.setImageBitmap(thumb)
                    }
                    var backgroundIntent = Intent(this, BackgroundService::class.java)
                    backgroundIntent.type = intent.type
                    backgroundIntent.putExtra(Intent.EXTRA_STREAM, uri)
                    backgroundIntent.setAction("uk.me.hardill.s3share.action.UPLOAD")
                    startService(backgroundIntent)
//                    handleSendVideo(intent = intent, s3Client = s3Client)
                }
                finish()
            }

            else -> {
                setContentView(R.layout.setttings)

                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    val perms = arrayOf(Manifest.permission.POST_NOTIFICATIONS)
                    ActivityCompat.requestPermissions(this, perms, 1)
                    return
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

//    private fun handleSendImage(intent: Intent, s3Client: AmazonS3Client) {
//        (intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let {
//            // Update UI to reflect image being shared
//            val uri:Uri? = intent.getParcelableExtra(Intent.EXTRA_STREAM)
//            binding.preview.setImageURI(uri)
//            sendFile(uri!!, s3Client)
//        }
//    }

//    private fun handleSendVideo(intent: Intent, s3Client: AmazonS3Client) {
//        (intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let {
//            // Update UI to reflect image being shared
//            val uri:Uri? = intent.getParcelableExtra(Intent.EXTRA_STREAM)
//            val thumb: Bitmap? = createVideoThumb(this, uri!!)
//            thumb?.let {
//                binding.preview.setImageBitmap(thumb)
//            }
//            sendFile(uri, s3Client)
//        }
//    }

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

//    private fun sendFile(uri: Uri, s3Client: AmazonS3Client) {
//        val cursor:Cursor? = contentResolver.query(uri, null, null, null, null)
//        cursor?.moveToFirst()
//        val nameColumn:Int? = cursor?.getColumnIndex(OpenableColumns.DISPLAY_NAME)
//        val name:String? = cursor?.getString(nameColumn!!)
//        cursor?.close()
//        binding.fileName.text = name
//        val contentResolver:ContentResolver = this.contentResolver
//        val inputStream:InputStream? =  contentResolver.openInputStream(uri)
//        val preferences = PreferenceManager.getDefaultSharedPreferences(this).all
//        val trans = TransferUtility.builder().context(applicationContext)
//            .s3Client(s3Client)
//            .defaultBucket(preferences.getValue("bucket") as String)
//            .build()
//        val observer: TransferObserver = trans.upload(name, inputStream)
//        observer.setTransferListener(object: TransferListener {
//            override fun onStateChanged(id: Int, state: TransferState?) {
//                if (state == TransferState.COMPLETED) {
//                    Log.d("msg","sucessfully sent")
//                    var https = "s"
//                    if (!(preferences.getValue("https") as Boolean)) {
//                        https = ""
//                    }
//                    val endpoint = preferences.getValue("endpoint") as String
//                    val bucket = preferences.getValue("bucket") as String
//                    if ((preferences.getValue("pathBased") as Boolean)) {
//                        binding.link.text = getString(R.string.url_path, https, endpoint, bucket, name )
//                    } else {
//                        binding.link.text = getString(R.string.url_host, https, bucket, endpoint, name )
//                    }
//                } else if (state == TransferState.FAILED) {
//                    Log.d("msg","failed")
//                }
//            }
//
//            override fun onProgressChanged(id: Int, bytesCurrent: Long, bytesTotal: Long) {
//                binding.progressBar.max = 100
//                binding.progressBar.progress = (100 * (bytesCurrent/bytesTotal)).toInt()
//            }
//
//            override fun onError(id: Int, ex: Exception?) {
//                Log.d("error", ex.toString())
//            }
//        })
//    }
}
