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

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.ContextMenu.ContextMenuInfo
import android.widget.TextView
import androidx.activity.result.ActivityResult
import com.myflightbook.android.ActMFBForm.GallerySource
import com.myflightbook.android.DlgDatePicker.DateTimeUpdate
import com.myflightbook.android.webservices.AircraftSvc
import com.myflightbook.android.webservices.AuthToken
import com.myflightbook.android.webservices.MFBSoap
import com.myflightbook.android.webservices.MFBSoap.Companion.isOnline
import com.myflightbook.android.webservices.MFBSoap.MFBSoapProgressUpdate
import com.myflightbook.android.webservices.UTCDate.isNullDate
import model.*
import model.Aircraft.PilotRole
import model.MFBImageInfo.PictureDestination
import java.util.*

class ActEditAircraft : ActMFBForm(), View.OnClickListener, DateTimeUpdate, GallerySource {
    private var mAc: Aircraft? = null

    private class SubmitTask(c: Context?, aea: ActEditAircraft) :
        AsyncTask<Void?, String?, Boolean>(), MFBSoapProgressUpdate {
        private var mPd: ProgressDialog? = null
        private var mAcs: AircraftSvc? = null
        private val mCtxt: AsyncWeakContext<ActEditAircraft> = AsyncWeakContext(c, aea)
        override fun doInBackground(vararg params: Void?): Boolean {
            val c = mCtxt.context
            val aea = mCtxt.callingActivity
            if (c == null || aea == null) return false
            mAcs = AircraftSvc()
            mAcs!!.mProgress = this
            mAcs!!.updateMaintenanceForAircraft(AuthToken.m_szAuthToken, aea.mAc, c)
            return mAcs!!.lastError.isEmpty()
        }

        override fun onPreExecute() {
            mPd = mCtxt.callingActivity?.let {
                MFBUtil.showProgress(
                    it,
                    mCtxt.context!!.getString(R.string.prgUpdatingAircraft)
                )
            }
        }

        override fun onPostExecute(b: Boolean) {
            try {
                if (mPd != null) mPd!!.dismiss()
            } catch (e: Exception) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e))
            }
            val aea = mCtxt.callingActivity
            if (aea == null || !aea.isAdded || aea.activity == null) return
            if (b) {
                // force a refresh.
                mAcs!!.flushCache()
                MFBMain.invalidateCachedTotals() // could have updated maintenance, leading currency to be invalid.
                val i = Intent()
                aea.requireActivity().setResult(Activity.RESULT_OK, i)
                aea.finish()
            } else {
                MFBUtil.alert(aea, aea.getString(R.string.txtError), mAcs!!.lastError)
            }
        }

        override fun onProgressUpdate(vararg msg: String?) {
            mPd!!.setMessage(msg[0])
        }

        override fun notifyProgress(percentageComplete: Int, szMsg: String?) {
            publishProgress(szMsg)
        }

    }

    private class DeleteTask(c: Context?, aea: ActEditAircraft) :
        AsyncTask<Void?, String?, MFBSoap?>(), MFBSoapProgressUpdate {
        private var mPd: ProgressDialog? = null
        private val mCtxt: AsyncWeakContext<ActEditAircraft> = AsyncWeakContext(c, aea)
        override fun doInBackground(vararg params: Void?): MFBSoap {
            val mAcs = AircraftSvc()
            mAcs.mProgress = this
            mAcs.deleteAircraftForUser(
                AuthToken.m_szAuthToken,
                mCtxt.callingActivity!!.mAc!!.aircraftID,
                mCtxt.context
            )
            return mAcs
        }

        override fun onPreExecute() {
            mPd = MFBUtil.showProgress(
                mCtxt.callingActivity!!,
                mCtxt.context!!.getString(R.string.prgDeletingAircraft)
            )
        }

        override fun onPostExecute(acs: MFBSoap?) {
            try {
                if (mPd != null) mPd!!.dismiss()
            } catch (e: Exception) {
                Log.e(MFBConstants.LOG_TAG, Log.getStackTraceString(e))
            }
            val c = mCtxt.context
            val aea = mCtxt.callingActivity
            if (c == null || aea == null || acs == null || aea.activity == null || !aea.isAdded) return
            if (acs.lastError.isEmpty()) {
                val i = Intent()
                aea.requireActivity().setResult(Activity.RESULT_OK, i)
                aea.finish()
            } else MFBUtil.alert(aea, c.getString(R.string.txtError), acs.lastError)
        }

        override fun onProgressUpdate(vararg msg: String?) {
            mPd!!.setMessage(msg[0])
        }

        override fun notifyProgress(percentageComplete: Int, szMsg: String?) {
            publishProgress(szMsg)
        }

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        setHasOptionsMenu(true)
        return inflater.inflate(R.layout.editaircraft, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        addListener(R.id.btnVORCheck)
        addListener(R.id.btnTransponder)
        addListener(R.id.btnPitotStatic)
        addListener(R.id.btnAltimeter)
        addListener(R.id.btnELT)
        addListener(R.id.btnAnnual)
        addListener(R.id.btnRegistration)
        addListener(R.id.ckHideAircraftFromSelection)
        addListener(R.id.rbRoleNone)
        addListener(R.id.rbRoleCFI)
        addListener(R.id.rbRolePIC)
        addListener(R.id.rbRoleSIC)

        // Expand/collapse
        addListener(R.id.acPrefsHeader)
        addListener(R.id.acNotesHeader)
        addListener(R.id.txtACMaintenance)
        addListener(R.id.txtImageHeader)
        val i = requireActivity().intent
        val idAircraft = i.getIntExtra(AIRCRAFTID, 0)
        if (idAircraft > 0) {
            val acs = AircraftSvc()
            val rgac = acs.cachedAircraft
            for (ac in rgac) {
                if (ac.aircraftID == idAircraft) {
                    mAc = ac
                    break
                }
            }
        }
        toView()
        if (mAc != null) {
            setExpandedState(
                findViewById(R.id.acPrefsHeader) as TextView,
                findViewById(R.id.rbgPilotRole)!!,
                mAc!!.roleForPilot != PilotRole.None,
                false
            )
            setExpandedState(
                findViewById(R.id.acNotesHeader) as TextView,
                findViewById(R.id.sectACNotes)!!,
                mAc!!.privateNotes.length + mAc!!.publicNotes.length > 0,
                false
            )
            val fHideMaintenance = isNullDate(mAc!!.lastVOR) &&
                    isNullDate(mAc!!.lastTransponder) &&
                    isNullDate(mAc!!.lastStatic) &&
                    isNullDate(mAc!!.lastAltimeter) &&
                    isNullDate(mAc!!.lastELT) &&
                    isNullDate(mAc!!.lastAnnual) &&
                    isNullDate(mAc!!.registrationDue) && mAc!!.last100 == 0.0 && mAc!!.lastOil == 0.0 && mAc!!.lastEngine == 0.0
            setExpandedState(
                findViewById(R.id.txtACMaintenance) as TextView,
                findViewById(R.id.sectACMaintenance)!!,
                !fHideMaintenance,
                false
            )
        }
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        val inflater = requireActivity().menuInflater
        inflater.inflate(R.menu.imagemenu, menu)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        return if (id == R.id.menuAddComment || id == R.id.menuDeleteImage || id == R.id.menuViewImage) onImageContextItemSelected(
            item,
            this
        ) else true
    }

    private fun toView() {
        if (mAc == null) {
            finish()
            return
        }
        (findViewById(R.id.txtTailNumber) as TextView).text = mAc!!.tailNumber
        (findViewById(R.id.txtAircraftType) as TextView).text =
            getString(Aircraft.rgidInstanceTypes[if (mAc!!.instanceTypeID > 0) mAc!!.instanceTypeID - 1 else 0])
        (findViewById(R.id.txtAircraftMakeModel) as TextView).text = String.format("%s%s",
            mAc!!.modelDescription,
            if (mAc!!.modelCommonName.isNotEmpty()) String.format(
                " (%s)",
                mAc!!.modelCommonName.trim { it <= ' ' }) else ""
        )
        setLocalDateForField(R.id.btnVORCheck, MFBUtil.localDateFromUTCDate(mAc!!.lastVOR))
        setLocalDateForField(R.id.btnAltimeter, MFBUtil.localDateFromUTCDate(mAc!!.lastAltimeter))
        setLocalDateForField(R.id.btnAnnual, MFBUtil.localDateFromUTCDate(mAc!!.lastAnnual))
        setLocalDateForField(
            R.id.btnTransponder,
            MFBUtil.localDateFromUTCDate(mAc!!.lastTransponder)
        )
        setLocalDateForField(R.id.btnELT, MFBUtil.localDateFromUTCDate(mAc!!.lastELT))
        setLocalDateForField(R.id.btnPitotStatic, MFBUtil.localDateFromUTCDate(mAc!!.lastStatic))
        setLocalDateForField(
            R.id.btnRegistration,
            MFBUtil.localDateFromUTCDate(mAc!!.registrationDue)
        )
        setStringForField(
            R.id.nextVOR,
            mAc!!.nextDueLabel(mAc!!.nextVOR(), getString(R.string.lblNextDue), context)
        )
        setStringForField(
            R.id.nextAltimeter,
            mAc!!.nextDueLabel(mAc!!.nextAltimeter(), getString(R.string.lblNextDue), context)
        )
        setStringForField(
            R.id.nextAnnual,
            mAc!!.nextDueLabel(mAc!!.nextAnnual(), getString(R.string.lblNextDue), context)
        )
        setStringForField(
            R.id.nextTransponder,
            mAc!!.nextDueLabel(mAc!!.nextTransponder(), getString(R.string.lblNextDue), context)
        )
        setStringForField(
            R.id.nextELT,
            mAc!!.nextDueLabel(mAc!!.nextELT(), getString(R.string.lblNextDue), context)
        )
        setStringForField(
            R.id.nextPitotStatic,
            mAc!!.nextDueLabel(mAc!!.nextStatic(), getString(R.string.lblNextDue), context)
        )
        setDoubleForField(R.id.txt100hr, mAc!!.last100)
        setDoubleForField(R.id.txtOilChange, mAc!!.lastOil)
        setDoubleForField(R.id.txtNewEngine, mAc!!.lastEngine)
        setStringForField(R.id.txtPublicAircraftNotes, mAc!!.publicNotes)
        setStringForField(R.id.txtPrivateAircraftNotes, mAc!!.privateNotes)
        setStringForField(R.id.txtMaintNotes, mAc!!.maintenanceNote)
        setCheckState(R.id.ckHideAircraftFromSelection, !mAc!!.hideFromSelection)
        when (mAc!!.roleForPilot) {
            PilotRole.None -> setRadioButton(R.id.rbRoleNone)
            PilotRole.PIC -> setRadioButton(R.id.rbRolePIC)
            PilotRole.SIC -> setRadioButton(R.id.rbRoleSIC)
            PilotRole.CFI -> setRadioButton(R.id.rbRoleCFI)
        }
        setUpImageGallery(getGalleryID(), getImages(), getGalleryHeader())
        findViewById(R.id.sectMaintenance)!!.visibility =
            if (mAc!!.isReal() && !mAc!!.isAnonymous()) View.VISIBLE else View.GONE
    }

    private fun fromView() {
        // dates were changed synchronously, only need the decimal values.
        mAc!!.last100 = doubleFromField(R.id.txt100hr)
        mAc!!.lastOil = doubleFromField(R.id.txtOilChange)
        mAc!!.lastEngine = doubleFromField(R.id.txtNewEngine)
        mAc!!.publicNotes = stringFromField(R.id.txtPublicAircraftNotes)
        mAc!!.privateNotes = stringFromField(R.id.txtPrivateAircraftNotes)
        mAc!!.maintenanceNote = stringFromField(R.id.txtMaintNotes)
    }

    override fun onClick(v: View) {
        fromView()
        val id = v.id
        if (id == R.id.btnVORCheck) {
            if (isNullDate(mAc!!.lastVOR)) mAc!!.lastVOR =
                MFBUtil.getUTCDateFromLocalDate(Date()) else setDateTime(
                id, MFBUtil.localDateFromUTCDate(
                    mAc!!.lastVOR
                ), this, DlgDatePicker.DatePickMode.LOCALDATENULLABLE
            )
        } else if (id == R.id.btnAltimeter) {
            if (isNullDate(mAc!!.lastAltimeter)) mAc!!.lastAltimeter =
                MFBUtil.getUTCDateFromLocalDate(
                    Date()
                ) else setDateTime(
                id, MFBUtil.localDateFromUTCDate(
                    mAc!!.lastAltimeter
                ), this, DlgDatePicker.DatePickMode.LOCALDATENULLABLE
            )
        } else if (id == R.id.btnAnnual) {
            if (isNullDate(mAc!!.lastAnnual)) mAc!!.lastAnnual =
                MFBUtil.getUTCDateFromLocalDate(Date()) else setDateTime(
                id, MFBUtil.localDateFromUTCDate(
                    mAc!!.lastAnnual
                ), this, DlgDatePicker.DatePickMode.LOCALDATENULLABLE
            )
        } else if (id == R.id.btnTransponder) {
            if (isNullDate(mAc!!.lastTransponder)) mAc!!.lastTransponder =
                MFBUtil.getUTCDateFromLocalDate(
                    Date()
                ) else setDateTime(
                id, MFBUtil.localDateFromUTCDate(
                    mAc!!.lastTransponder
                ), this, DlgDatePicker.DatePickMode.LOCALDATENULLABLE
            )
        } else if (id == R.id.btnELT) {
            if (isNullDate(mAc!!.lastELT)) mAc!!.lastELT =
                MFBUtil.getUTCDateFromLocalDate(Date()) else setDateTime(
                id, MFBUtil.localDateFromUTCDate(
                    mAc!!.lastELT
                ), this, DlgDatePicker.DatePickMode.LOCALDATENULLABLE
            )
        } else if (id == R.id.btnPitotStatic) {
            if (isNullDate(mAc!!.lastStatic)) mAc!!.lastStatic =
                MFBUtil.getUTCDateFromLocalDate(Date()) else setDateTime(
                id, MFBUtil.localDateFromUTCDate(
                    mAc!!.lastStatic
                ), this, DlgDatePicker.DatePickMode.LOCALDATENULLABLE
            )
        } else if (id == R.id.btnRegistration) {
            if (isNullDate(mAc!!.registrationDue)) mAc!!.registrationDue =
                MFBUtil.getUTCDateFromLocalDate(
                    Date()
                ) else setDateTime(
                id, MFBUtil.localDateFromUTCDate(
                    mAc!!.registrationDue
                ), this, DlgDatePicker.DatePickMode.LOCALDATENULLABLE
            )
        } else if (id == R.id.ckHideAircraftFromSelection) mAc!!.hideFromSelection =
            !checkState(id) else if (id == R.id.rbRoleNone) mAc!!.roleForPilot =
            PilotRole.None else if (id == R.id.rbRolePIC) mAc!!.roleForPilot =
            PilotRole.PIC else if (id == R.id.rbRoleSIC) mAc!!.roleForPilot =
            PilotRole.SIC else if (id == R.id.rbRoleCFI) mAc!!.roleForPilot =
            PilotRole.CFI else if (id == R.id.acNotesHeader) {
            val target = findViewById(R.id.sectACNotes)!!
            setExpandedState(v as TextView, target, target.visibility != View.VISIBLE)
        } else if (id == R.id.acPrefsHeader) {
            val target = findViewById(R.id.rbgPilotRole)!!
            setExpandedState(v as TextView, target, target.visibility != View.VISIBLE)
        } else if (id == R.id.txtACMaintenance) {
            val target = findViewById(R.id.sectACMaintenance)!!
            setExpandedState(v as TextView, target, target.visibility != View.VISIBLE)
        } else if (id == R.id.txtImageHeader) {
            val target = findViewById(R.id.tblImageTable)!!
            setExpandedState(v as TextView, target, target.visibility != View.VISIBLE)
        }
        toView()
    }

    //region Image support
    override fun chooseImageCompleted(result: ActivityResult?) {
        addGalleryImage(Objects.requireNonNull(result!!.data!!))
    }

    override fun takePictureCompleted(result: ActivityResult?) {
        addCameraImage(mTempfilepath, false)
    }

    //endregion
    override fun updateDate(id: Int, dt: Date?) {
        var dtLoc: Date = dt!!
        fromView()
        dtLoc = MFBUtil.getUTCDateFromLocalDate(dtLoc)
        when (id) {
            R.id.btnVORCheck -> {
                mAc!!.lastVOR = dtLoc
                setLocalDateForField(R.id.btnVORCheck, mAc!!.lastVOR)
            }
            R.id.btnAltimeter -> {
                mAc!!.lastAltimeter = dtLoc
                setLocalDateForField(R.id.btnAltimeter, mAc!!.lastAltimeter)
            }
            R.id.btnAnnual -> {
                mAc!!.lastAnnual = dtLoc
                setLocalDateForField(R.id.btnAnnual, mAc!!.lastAnnual)
            }
            R.id.btnTransponder -> {
                mAc!!.lastTransponder = dtLoc
                setLocalDateForField(R.id.btnTransponder, mAc!!.lastTransponder)
            }
            R.id.btnELT -> {
                mAc!!.lastELT = dtLoc
                setLocalDateForField(R.id.btnELT, mAc!!.lastELT)
            }
            R.id.btnPitotStatic -> {
                mAc!!.lastStatic = dtLoc
                setLocalDateForField(R.id.btnPitotStatic, mAc!!.lastStatic)
            }
            R.id.btnRegistration -> {
                mAc!!.registrationDue = dtLoc
                setLocalDateForField(R.id.btnRegistration, mAc!!.registrationDue)
            }
        }
        toView()
    }

    private fun updateAircraft() {
        fromView()
        val st = SubmitTask(context, this)
        st.execute()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.editaircraftmenu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.menuChoosePicture) choosePicture() else if (id == R.id.menuTakePicture) takePicture() else if (id == R.id.menuUpdateAircraft) {
            if (isOnline(context)) updateAircraft() else MFBUtil.alert(
                context,
                getString(R.string.txtError),
                getString(R.string.errNoInternet)
            )
        } else if (id == R.id.menuDeleteAircraft) DeleteTask(
            context, this
        ).execute() else if (id == R.id.menuViewSchedule) {
            if (isOnline(context)) ActWebView.viewURL(
                requireActivity(), MFBConstants.authRedirWithParams(
                    String.format(Locale.US, "d=aircraftschedule&ac=%d", mAc!!.aircraftID), context
                )
            ) else MFBUtil.alert(
                context, getString(R.string.txtError), getString(R.string.errNoInternet)
            )
        } else if (id == R.id.findFlights) {
            if (isOnline(context)) {
                val fq = FlightQuery()
                fq.init()
                fq.aircraftList = if (mAc == null) arrayOf() else arrayOf(mAc!!)
                val i = Intent(activity, RecentFlightsActivity::class.java)
                val b = Bundle()
                b.putSerializable(ActFlightQuery.QUERY_TO_EDIT, fq)
                i.putExtras(b)
                startActivity(i)
            } else MFBUtil.alert(
                context,
                getString(R.string.txtError),
                getString(R.string.errNoInternet)
            )
        } else return super.onOptionsItemSelected(item)
        return true
    }

    /*
     * GallerySource methods
     * (non-Javadoc)
     * @see com.myflightbook.android.ActMFBForm.GallerySource#getGalleryID()
     */
    override fun getGalleryID(): Int {
        return R.id.tblImageTable
    }

    override fun getGalleryHeader(): View {
        return findViewById(R.id.txtImageHeader)!!
    }

    override fun getImages(): Array<MFBImageInfo> {
        return if (mAc == null || mAc!!.aircraftImages == null) arrayOf() else mAc!!.aircraftImages!!
    }

    override fun setImages(rgmfbii: Array<MFBImageInfo>?) {
        mAc!!.aircraftImages = rgmfbii
    }

    override fun newImage(mfbii: MFBImageInfo?) {
        mfbii!!.setPictureDestination(PictureDestination.AircraftImage)
        mfbii.targetID = mAc!!.aircraftID.toLong()
        mfbii.toDB()
        mAc!!.aircraftImages = MFBImageInfo.getLocalImagesForId(
            mAc!!.aircraftID.toLong(),
            PictureDestination.AircraftImage
        )
    }

    override fun refreshGallery() {
        setUpImageGallery(getGalleryID(), getImages(), getGalleryHeader())
    }

    override fun getPictureDestination(): PictureDestination {
        return PictureDestination.AircraftImage
    }

    companion object {
        const val AIRCRAFTID = "com.myflightbook.android.aircraftID"
    }
}