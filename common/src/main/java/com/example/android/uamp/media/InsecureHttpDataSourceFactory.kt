/*
 * Copyright 2024 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.uamp.media

import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * DEVELOPMENT ONLY: Custom HTTP data source factory that bypasses SSL verification
 * for stretto.tplinkdns.com domain. This is INSECURE and should only be used for development.
 */
class InsecureHttpDataSourceFactory : HttpDataSource.Factory {

    private val defaultFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("ExoPlayer-UAMP")
        .setConnectTimeoutMs(30000)
        .setReadTimeoutMs(30000)
        .setAllowCrossProtocolRedirects(true)

    override fun createDataSource(): HttpDataSource {
        val dataSource = defaultFactory.createDataSource()

        // Set up insecure SSL context for development
        try {
            val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())

            val allHostsValid = HostnameVerifier { _, _ -> true }

            // Set the default SSL socket factory and hostname verifier
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid)

        } catch (e: Exception) {
            // If setting up insecure context fails, use default
        }

        return dataSource
    }

    override fun setDefaultRequestProperties(defaultRequestProperties: MutableMap<String, String>): HttpDataSource.Factory {
        defaultFactory.setDefaultRequestProperties(defaultRequestProperties)
        return this
    }
}
