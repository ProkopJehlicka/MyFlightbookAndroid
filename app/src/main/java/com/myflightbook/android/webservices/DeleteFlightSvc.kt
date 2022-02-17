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
package com.myflightbook.android.webservices

import android.content.Context
import org.ksoap2.serialization.SoapPrimitive

class DeleteFlightSvc : MFBSoap() {
    fun deleteFlightForUser(szAuthToken: String?, idFlight: Int, c: Context?) {
        val request = setMethod("DeleteLogbookEntry")
        request.addProperty("szAuthUserToken", szAuthToken)
        request.addProperty("idFlight", idFlight)
        val result = invoke(c) as SoapPrimitive?
        if (result == null) lastError = "Error deleting flight - $lastError"
    }
}