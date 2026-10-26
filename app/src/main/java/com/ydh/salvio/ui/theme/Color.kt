package com.ydh.salvio.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 서정적(soft·warm·low-saturation) 미니멀 팔레트.
 *
 * 색은 상태를 전달할 때만 사용한다(open/merged/closed/CI 등).
 * 순수 흑백·고채도 대신 따뜻한 종이빛/차콜과 낮은 채도의 액센트로
 * 차분하고 서정적인 분위기를 만든다.
 *
 * 라이트/다크를 모두 지원하기 위해 색은 상수가 아니라
 * [SalvioColors] 토큰으로 정의하고 CompositionLocal로 주입한다.
 * 화면에서는 `SalvioTheme.colors.xxx`로 접근한다.
 */
@Immutable
data class SalvioColors(
    val canvas: Color, // 앱 배경
    val canvasInset: Color, // diff 등 더 깊은 면
    val surface: Color, // 카드 면
    val surfaceHover: Color, // 카드 hover/press
    val border: Color, // 구분선
    val borderMuted: Color, // 카드 기본 테두리(더 은은)
    val text: Color, // 기본 텍스트
    val textSecondary: Color, // 보조 텍스트
    val textTertiary: Color, // 더 옅은 텍스트
    val accent: Color, // 링크/기본 액션
    val success: Color, // open/통과
    val done: Color, // merged
    val attention: Color, // 경고
    val danger: Color, // closed/실패
    val isLight: Boolean
)

/** 다크: 따뜻한 차콜 + 낮은 채도 액센트 */
val DarkColors = SalvioColors(
    canvas = Color(0xFF191A1D),
    canvasInset = Color(0xFF121316),
    surface = Color(0xFF212327),
    surfaceHover = Color(0xFF292B30),
    border = Color(0xFF383A40),
    borderMuted = Color(0xFF2A2C31),
    text = Color(0xFFE7E4DE),
    textSecondary = Color(0xFF9A968D),
    textTertiary = Color(0xFF6C6960),
    accent = Color(0xFF7FA6DB),
    success = Color(0xFF7CB08A),
    done = Color(0xFFAB9BD6),
    attention = Color(0xFFD8B36B),
    danger = Color(0xFFE08C82),
    isLight = false
)

/** 라이트: 따뜻한 종이빛 + 부드럽게 눌러앉은 액센트 */
val LightColors = SalvioColors(
    canvas = Color(0xFFFBFAF7),
    canvasInset = Color(0xFFF1EFEA),
    surface = Color(0xFFFFFFFF),
    surfaceHover = Color(0xFFF4F2EE),
    border = Color(0xFFE2DFD8),
    borderMuted = Color(0xFFECEAE4),
    text = Color(0xFF33312C),
    textSecondary = Color(0xFF6E6A62),
    textTertiary = Color(0xFF9C978D),
    accent = Color(0xFF4E77AE),
    success = Color(0xFF4F9068),
    done = Color(0xFF8672B5),
    attention = Color(0xFFB5852F),
    danger = Color(0xFFC6584C),
    isLight = true
)

val LocalSalvioColors = staticCompositionLocalOf { DarkColors }
