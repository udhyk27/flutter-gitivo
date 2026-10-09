package com.ydh.salvio.util

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response as OkResponse
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ApiErrorTest {

    /** 지정한 상태 코드/헤더를 갖는 HttpException 을 만든다. */
    private fun httpException(code: Int, headers: Map<String, String> = emptyMap()): HttpException {
        val errorBody = "".toResponseBody("application/json".toMediaTypeOrNull())
        val rawBuilder = OkResponse.Builder()
            .request(Request.Builder().url("https://api.github.com/").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("error")
        headers.forEach { (k, v) -> rawBuilder.addHeader(k, v) }
        return HttpException(Response.error<Any>(errorBody, rawBuilder.build()))
    }

    // ---- isAuthExpired ----

    @Test
    fun isAuthExpired_true_for401() {
        assertTrue(httpException(401).isAuthExpired())
    }

    @Test
    fun isAuthExpired_false_forOtherCodesAndNonHttp() {
        assertFalse(httpException(403).isAuthExpired())
        assertFalse(IOException("boom").isAuthExpired())
    }

    // ---- httpCode ----

    @Test
    fun httpCode_returnsCode_forHttpException() {
        assertEquals(404, httpException(404).httpCode())
    }

    @Test
    fun httpCode_returnsNull_forNonHttpException() {
        assertNull(IOException("boom").httpCode())
        assertNull(RuntimeException().httpCode())
    }

    // ---- isRateLimited ----

    @Test
    fun isRateLimited_true_when403AndRemainingZero() {
        val e = httpException(403, mapOf("X-RateLimit-Remaining" to "0"))
        assertTrue(e.isRateLimited())
    }

    @Test
    fun isRateLimited_true_when429AndRemainingZero() {
        val e = httpException(429, mapOf("X-RateLimit-Remaining" to "0"))
        assertTrue(e.isRateLimited())
    }

    @Test
    fun isRateLimited_false_when403ButRemainingPositive() {
        val e = httpException(403, mapOf("X-RateLimit-Remaining" to "42"))
        assertFalse(e.isRateLimited())
    }

    @Test
    fun isRateLimited_false_when403WithoutHeader() {
        assertFalse(httpException(403).isRateLimited())
    }

    @Test
    fun isRateLimited_false_forNonHttpException() {
        assertFalse(IOException().isRateLimited())
    }

    // ---- toUserMessage: 네트워크 예외 ----

    @Test
    fun toUserMessage_unknownHost() {
        assertEquals(
            "인터넷에 연결되어 있지 않습니다. 연결 상태를 확인하세요.",
            UnknownHostException().toUserMessage()
        )
    }

    @Test
    fun toUserMessage_timeout() {
        assertEquals(
            "연결 시간이 초과되었습니다. 네트워크 상태를 확인하세요.",
            SocketTimeoutException().toUserMessage()
        )
    }

    @Test
    fun toUserMessage_genericIo() {
        assertEquals(
            "네트워크 오류가 발생했습니다. 잠시 후 다시 시도하세요.",
            IOException().toUserMessage()
        )
    }

    // ---- toUserMessage: HTTP 상태 코드 ----

    @Test
    fun toUserMessage_401() {
        assertEquals("인증이 만료되었습니다. 다시 로그인하세요.", httpException(401).toUserMessage())
    }

    @Test
    fun toUserMessage_403_permissionDenied() {
        val e = httpException(403, mapOf("X-RateLimit-Remaining" to "10"))
        assertEquals("접근 권한이 없습니다.", e.toUserMessage())
    }

    @Test
    fun toUserMessage_403_rateLimited_withResetTime() {
        val resetSec = System.currentTimeMillis() / 1000 + 3600 // 약 60분 뒤
        val e = httpException(
            403,
            mapOf("X-RateLimit-Remaining" to "0", "X-RateLimit-Reset" to resetSec.toString())
        )
        val msg = e.toUserMessage()
        assertTrue("실제 메시지: $msg", msg.startsWith("API 요청 한도를 초과했습니다."))
        assertTrue("실제 메시지: $msg", msg.contains("분 후"))
    }

    @Test
    fun toUserMessage_403_rateLimited_withoutResetTime() {
        val e = httpException(403, mapOf("X-RateLimit-Remaining" to "0"))
        assertEquals("API 요청 한도를 초과했습니다. 잠시 후 다시 시도하세요.", e.toUserMessage())
    }

    @Test
    fun toUserMessage_404() {
        assertEquals("찾을 수 없습니다.", httpException(404).toUserMessage())
    }

    @Test
    fun toUserMessage_422() {
        assertEquals("요청을 처리할 수 없습니다. 입력을 확인하세요.", httpException(422).toUserMessage())
    }

    @Test
    fun toUserMessage_500_serverError() {
        assertEquals("GitHub 서버 오류입니다. 잠시 후 다시 시도하세요.", httpException(503).toUserMessage())
    }

    @Test
    fun toUserMessage_unknownCode_usesFallback() {
        assertEquals("커스텀 실패", httpException(418).toUserMessage("커스텀 실패"))
    }

    @Test
    fun toUserMessage_nonHttpThrowable_usesFallback() {
        assertEquals("커스텀 실패", RuntimeException().toUserMessage("커스텀 실패"))
    }

    @Test
    fun toUserMessage_defaultFallback() {
        assertEquals("요청에 실패했습니다.", RuntimeException().toUserMessage())
    }
}
