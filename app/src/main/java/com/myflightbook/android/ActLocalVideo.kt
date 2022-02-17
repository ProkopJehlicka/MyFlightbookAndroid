/*
	MyFlightbook for Android - provides native access to MyFlightbook
	pilot's logbook
    Copyright (C) 2017-2022 MyFlightbook, LLC

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.myflightbook.android

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import model.MFBConstants
import android.widget.VideoView
import android.net.Uri
import android.util.Log
import java.io.File

class ActLocalVideo : AppCompatActivity() {
    private var szTempFile: String? = ""
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.localvideo)
        val szURL = this.intent.getStringExtra(MFBConstants.intentViewURL)
        szTempFile = this.intent.getStringExtra(MFBConstants.intentViewTempFile)
        val video = findViewById<VideoView>(R.id.video)
        // Load and start the movie
        video.setVideoURI(Uri.parse(szURL))
        video.start()
    }

    public override fun onPause() {
        super.onPause()
        if (szTempFile != null && szTempFile!!.isNotEmpty()) {
            if (!File(szTempFile!!).delete()) Log.v(MFBConstants.LOG_TAG, "Local video delete failed")
        }
    }
}