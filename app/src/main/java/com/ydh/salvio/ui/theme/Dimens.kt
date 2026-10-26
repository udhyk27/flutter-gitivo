package com.ydh.salvio.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** 미니멀 UI의 간격 스케일. 화면 전반에서 이 값만 사용한다. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp

    /** 화면 좌우 기본 여백 */
    val screen = 16.dp

    /** 카드 내부 여백 */
    val card = 16.dp
}

/** 모서리 반경 스케일. 서정적 분위기를 위해 살짝 넉넉하게. */
object Radius {
    val card = RoundedCornerShape(14.dp)
    val chip = RoundedCornerShape(8.dp)
    val button = RoundedCornerShape(10.dp)
    val field = RoundedCornerShape(12.dp)
}
