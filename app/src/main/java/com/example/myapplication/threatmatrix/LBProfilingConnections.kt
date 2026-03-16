/*
 * Copyright (c) 2022 ThreatMetrix All Rights Reserved.
 */
@file:Suppress("OverrideDeprecatedMigration")

package com.example.myapplication.threatmatrix

import android.util.Log
import com.lexisnexisrisk.threatmetrix.TMXProfilingConnectionsInterface
import com.lexisnexisrisk.threatmetrix.TMXProfilingConnectionsInterface.*
import java.io.*
import java.net.*
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Simple implementation of TMXProfilingConnectionsInterface.
 *
 * Note: This class is not used by default, to use it please change the value of
 * LoginActivity#USE_TMX_PROFILING_CONNECTIONS to false.
 */
class LBProfilingConnections : TMXProfilingConnectionsInterface {
    private val tasks = mutableListOf<Future<*>>()
    private val taskRunner = TaskRunner()
    private val socketProvider = LBSocketProvider()
    private val taskLock = ReentrantLock()

    override fun httpRequest(
        method: HttpMethod,
        url: String,
        headers: Map<String, String>,
        body: ByteArray,
        callback: TMXCallback?
    ) {
        taskRunner.executeAsync(method, url, headers, body, callback)
    }

    override fun resolveHostByName(hostName: String) {
        taskRunner.executeAsync {
            try {
                InetAddress.getByName(hostName)
            } catch (e: UnknownHostException) {
                Log.i(TAG, "Failed to resolve host: $hostName", e)
            }
        }
    }

    override fun cancelProfiling() {
        taskLock.withLock {
            tasks.forEach { it.cancel(true) }
            tasks.clear()
        }
    }

    override fun sendSocketRequest(
        server: String,
        port: Int,
        data: ByteArray,
        closeSocket: Boolean,
        callback: TMXCallback?
    ) {
        socketProvider.sendSocketRequest(server, port, data, closeSocket, callback)
    }

    override fun closeSocket(server: String, port: Int) {
        socketProvider.closeSocket()
    }

    private inner class TaskRunner {
        private val executor = Executors.newCachedThreadPool()

        fun executeAsync(runnable: Runnable) {
            val task = executor.submit(runnable)
            taskLock.withLock {
                tasks.add(task)
            }
        }

        fun executeAsync(
            method: HttpMethod,
            url: String,
            header: Map<String, String>,
            body: ByteArray,
            callback: TMXCallback?
        ) {
            executeAsync {
                var connection: HttpURLConnection? = null
                try {
                    connection = createConnection(method, url, header, body)
                    if (connection == null) {
                        callback?.onComplete(
                            TMXHttpResponseCode(TMXHttpResponseCode.HttpResponseConnectionError),
                            null
                        )
                        return@executeAsync
                    }

                    connection.connect()
                    val responseCode = connection.responseCode

                    if (responseCode != TMXHttpResponseCode.HttpResponseOK) {
                        callback?.onComplete(TMXHttpResponseCode(responseCode), null)
                        return@executeAsync
                    }

                    callback?.onComplete(TMXHttpResponseCode(responseCode), connection.inputStream)
                } catch (exception: IOException) {
                    Log.e(TAG, "HttpURLConnection exception: ${exception.message}")
                    callback?.onComplete(getStatusFromException(exception), null)
                } finally {
                    connection?.disconnect()
                }
            }
        }

        @Throws(IOException::class)
        private fun createConnection(
            method: HttpMethod,
            urlStr: String,
            header: Map<String, String>,
            body: ByteArray
        ): HttpURLConnection? {
            val url = URL(urlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = if (method == HttpMethod.POST) "POST" else "GET"
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.useCaches = false
            connection.doInput = true
            connection.doOutput = method == HttpMethod.POST

            header.forEach { (key, value) ->
                connection.setRequestProperty(key, value)
            }

            if (method == HttpMethod.POST) {
                try {
                    connection.outputStream.use { os ->
                        os.write(body)
                        os.flush()
                    }
                } catch (exception: IOException) {
                    Log.i(TAG, "Failed to write to output stream", exception)
                    return null
                }
            }
            return connection
        }

        private fun getStatusFromException(e: Exception): TMXHttpResponseCode {
            return when (e) {
                is UnknownHostException -> TMXHttpResponseCode(TMXHttpResponseCode.HttpResponseHostNotFoundError)
                is SocketTimeoutException -> TMXHttpResponseCode(TMXHttpResponseCode.HttpResponseNetworkTimeoutError)
                else -> TMXHttpResponseCode(TMXHttpResponseCode.HttpResponseConnectionError)
            }
        }
    }

    private class LBSocketProvider {
        private var socket: Socket? = null
        private val maxPortNum = 65535

        @Synchronized
        fun sendSocketRequest(
            server: String,
            port: Int,
            packetToSend: ByteArray,
            closeSocket: Boolean,
            callback: TMXCallback?
        ) {
            var inStream: InputStream? = null
            val status = TMXHttpResponseCode(TMXSocketResponseCode.ResponseOk)
            try {
                if (server.isEmpty() || port < 1 || port > maxPortNum) {
                    status.httpResponseCode = TMXSocketResponseCode.ResponseIllegalArgumentException
                } else {
                    updateSocket(server, port)
                    socket?.let { s ->
                        s.outputStream.write(packetToSend)
                        if (closeSocket) {
                            s.close()
                            socket = null
                        } else {
                            inStream = s.inputStream
                        }
                    }
                }
            } catch (_: IllegalArgumentException) {
                status.httpResponseCode = TMXSocketResponseCode.ResponseIllegalArgumentException
            } catch (_: UnknownHostException) {
                status.httpResponseCode = TMXSocketResponseCode.ResponseUnknownHost
            } catch (_: IOException) {
                status.httpResponseCode = TMXSocketResponseCode.ResponseIOException
            }
            callback?.onComplete(status, inStream)
        }

        @Synchronized
        fun closeSocket() {
            try {
                socket?.close()
            } catch (_: IOException) {
            } finally {
                socket = null
            }
        }

        private fun updateSocket(server: String, port: Int) {
            if (socket == null || socket?.isClosed == true) {
                socket = Socket(server, port)
            }
        }
    }

    companion object {
        private val TAG = LBProfilingConnections::class.java.simpleName
        private const val TIMEOUT_MILLIS = 10_000
        private val taskLock = ReentrantLock() // Corrected to use instance lock instead of companion if preferred, but keeping static logic if required.
    }
}
