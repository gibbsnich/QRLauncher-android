package maw.qrlauncher

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.blikoon.qrcodescanner.QrCodeActivity
import com.devbrackets.android.exomedia.ui.widget.VideoView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDL.UpdateStatus
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers


class MainActivity : AppCompatActivity() {

    private val compositeDisposable = CompositeDisposable()
    private val REQUEST_CODE_QR_SCAN: Int = 101
    private var updating: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        try {
            YoutubeDL.getInstance().init(application)
            //YoutubeDL.getInstance().updateYoutubeDL(application)
            FFmpeg.getInstance().init(application)
        } catch (e: YoutubeDLException) {
            Log.e("MainActivity", "failed to initialize youtubedl-android", e)
        }

        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener { view ->

            val i = Intent(this@MainActivity, QrCodeActivity::class.java)
            startActivityForResult(i, REQUEST_CODE_QR_SCAN)

            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
        }

        updateYoutubeDL()
    }

    private fun updateYoutubeDL() {
        if (updating) {
            Toast.makeText(this@MainActivity, "update is already in progress", Toast.LENGTH_LONG)
                .show()
            return
        }
        updating = true
        //progressBar.setVisibility(View.VISIBLE)
        val disposable = Observable.fromCallable {
            YoutubeDL.getInstance().updateYoutubeDL(application)
        }
            .subscribeOn(Schedulers.newThread())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ status: UpdateStatus ->
                //progressBar.setVisibility(View.GONE)
                when (status) {
                    UpdateStatus.DONE -> Toast.makeText(
                        this@MainActivity,
                        "update successful",
                        Toast.LENGTH_LONG
                    ).show()
                    UpdateStatus.ALREADY_UP_TO_DATE -> Toast.makeText(
                        this@MainActivity,
                        "already up to date",
                        Toast.LENGTH_LONG
                    ).show()
                    else -> Toast.makeText(this@MainActivity, status.toString(), Toast.LENGTH_LONG)
                        .show()
                }
                updating = false
            }) { e: Throwable? ->
                if (BuildConfig.DEBUG) Log.e(
                    "MainActivity",
                    "failed to update",
                    e
                )
                //progressBar.setVisibility(View.GONE)
                Toast.makeText(this@MainActivity, "update failed", Toast.LENGTH_LONG).show()
                updating = false
            }
        compositeDisposable.add(disposable)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    @SuppressLint("MissingSuperCall")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        //super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != Activity.RESULT_OK) {

            val result: String? = data?.getStringExtra("com.blikoon.qrcodescanner.error_decoding_image")
            Toast.makeText(applicationContext, "Error decoding: $result", Toast.LENGTH_LONG).show()

        }

        if (requestCode == REQUEST_CODE_QR_SCAN) {
            val result: String? = data?.getStringExtra("com.blikoon.qrcodescanner.got_qr_scan_relult")
            if (result != null) {
                Toast.makeText(applicationContext, "Starting: $result", Toast.LENGTH_LONG).show()
                launchYoutubeDl(result)
            } else {
                Toast.makeText(applicationContext, "No url found!", Toast.LENGTH_LONG).show()
            }
        }

    }

    fun launchYoutubeDl(url: String) {
        val disposable: Disposable = Observable.fromCallable {
            val request = YoutubeDLRequest(url)
            // best stream containing video+audio
            request.addOption("-f", "best")
            YoutubeDL.getInstance().getInfo(request)
        }
            .subscribeOn(Schedulers.newThread())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ streamInfo ->
                val videoUrl: String = streamInfo.getUrl()
                if (TextUtils.isEmpty(videoUrl)) {
                    Toast.makeText(this@MainActivity, "failed to get stream url", Toast.LENGTH_LONG)
                        .show()
                } else {
                    setupVideoView(videoUrl)
                }
            }) { e ->
                if (BuildConfig.DEBUG) Log.e("MainActivity", "failed to get stream info", e)
                Toast.makeText(
                    this@MainActivity,
                    "streaming failed. failed to get stream info",
                    Toast.LENGTH_LONG
                ).show()
            }
        compositeDisposable.add(disposable)
    }

    fun setupVideoView(videoUrl: String) {
        val videoView = findViewById<VideoView>(R.id.video_view);
        videoView.setVideoURI(Uri.parse(videoUrl))
        videoView.start()
    }

}