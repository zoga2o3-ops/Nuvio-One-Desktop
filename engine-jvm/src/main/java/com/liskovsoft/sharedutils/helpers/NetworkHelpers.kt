package com.liskovsoft.sharedutils.helpers

import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object NetworkHelpers {
    @JvmStatic
    fun getHttpsURLConnection(url: URL): HttpURLConnection = getBestHttpsURLConnection(url)

    @JvmStatic
    fun getDohURLConnection(url: URL): HttpURLConnection = getBestHttpsURLConnection(url)

    private fun getBestHttpsURLConnection(url: URL): HttpsURLConnection =
        url.openConnection() as HttpsURLConnection
}
