package com.ydh.salvio.util

import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 예외로부터 HTTP 상태 코드를 추출한다. HTTP 오류가 아니면 null.
 * (`.message?.contains("403")` 같은 문자열 검사를 대체)
 */
fun Throwable.httpCode(): Int? = (this as? HttpException)?.code()

/** GitHub API rate limit(요청 한도) 초과 여부 */
fun Throwable.isRateLimited(): Boolean {
    val e = this as? HttpException ?: return false
    if (e.code() != 403 && e.code() != 429) return false
    return e.response()?.headers()?.get("X-RateLimit-Remaining")?.toIntOrNull() == 0
}

/** 인증 만료 여부 (401 Unauthorized) */
fun Throwable.isAuthExpired(): Boolean = (this as? HttpException)?.code() == 401

/**
 * 어떤 예외든 사용자에게 보여줄 한국어 메시지로 변환한다.
 * ViewModel 전반에 흩어져 있던 raw `e.message` 노출과 임시 문자열 검사를 대체한다.
 *
 * @param fallback 매핑되지 않는 경우 기본 메시지
 */
fun Throwable.toUserMessage(fallback: String = "요청에 실패했습니다."): String = when (this) {
    is UnknownHostException -> "인터넷에 연결되어 있지 않습니다. 연결 상태를 확인하세요."
    is SocketTimeoutException -> "연결 시간이 초과되었습니다. 네트워크 상태를 확인하세요."
    is HttpException -> httpMessage(this, fallback)
    is IOException -> "네트워크 오류가 발생했습니다. 잠시 후 다시 시도하세요."
    else -> fallback
}

private fun httpMessage(e: HttpException, fallback: String): String = when (e.code()) {
    401 -> "인증이 만료되었습니다. 다시 로그인하세요."
    403 -> if (e.isRateLimited()) rateLimitMessage(e) else "접근 권한이 없습니다."
    404 -> "찾을 수 없습니다."
    422 -> "요청을 처리할 수 없습니다. 입력을 확인하세요."
    429 -> rateLimitMessage(e)
    in 500..599 -> "GitHub 서버 오류입니다. 잠시 후 다시 시도하세요."
    else -> fallback
}

private fun rateLimitMessage(e: HttpException): String {
    val resetSec = e.response()?.headers()?.get("X-RateLimit-Reset")?.toLongOrNull()
    val mins = resetSec?.let {
        ((it * 1000 - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0)
    }
    return if (mins != null && mins > 0) {
        "API 요청 한도를 초과했습니다. 약 ${mins}분 후 다시 시도하세요."
    } else {
        "API 요청 한도를 초과했습니다. 잠시 후 다시 시도하세요."
    }
}
