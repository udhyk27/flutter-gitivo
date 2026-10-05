package com.ydh.salvio.util

import retrofit2.HttpException
import retrofit2.Response
import org.junit.Test

class NetworkStateTest {

    @Test
    fun `isAuthExpired returns true for 401 status code`() {
        val exception = HttpException(Response.error<Unit>(401, mockk()))
        assert(exception.isAuthExpired())
    }

    @Test
    fun `isAuthExpired returns false for non-401 status codes`() {
        val exception = HttpException(Response.error<Unit>(403, mockk()))
        assert(!exception.isAuthExpired())
    }

    @Test
    fun `isAuthExpired returns false for non-HttpException`() {
        val exception = Exception("Some error")
        assert(!exception.isAuthExpired())
    }

    @Test
    fun `toUserMessage returns correct message for UnknownHostException`() {
        val exception = java.net.UnknownHostException("No network")
        val message = exception.toUserMessage()
        assert(message.contains("인터넷"))
    }

    @Test
    fun `toUserMessage returns correct message for SocketTimeoutException`() {
        val exception = java.net.SocketTimeoutException("Timeout")
        val message = exception.toUserMessage()
        assert(message.contains("시간"))
    }

    @Test
    fun `toUserMessage returns 401 message for authentication error`() {
        val exception = HttpException(Response.error<Unit>(401, mockk()))
        val message = exception.toUserMessage()
        assert(message.contains("인증"))
    }

    @Test
    fun `isRateLimited returns true when rate limit exceeded`() {
        val headers = okhttp3.Headers.Builder()
            .add("X-RateLimit-Remaining", "0")
            .build()
        val response = Response.error<Unit>(429, okhttp3.ResponseBody.create(null, ""))
        val exception = HttpException(response)
        assert(exception.isRateLimited())
    }
}

private inline fun <reified T> mockk() = io.mockk.mockk<T>()
