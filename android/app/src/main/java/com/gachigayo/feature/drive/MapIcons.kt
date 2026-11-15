package com.gachigayo.feature.drive

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface

/**
 * 지도 위에 올리는 그림 (웹 데모와 같은 모양).
 * - 차: 둥근 사각형 차체 + 앞유리 + 전조등 (오른쪽을 바라보는 그림, 진행 방향으로 회전)
 * - 이름표: 흰 알약(다른 차) / 색 알약(내 차)
 * - 질문 말풍선: 노란 알약
 */
object MapIcons {
    private const val S = 1.25f // 차량 화면(160dpi)에서 웹 데모와 비슷한 크기

    fun car(color: Int, me: Boolean): Bitmap {
        val w = (46 * S).toInt()
        val h = (30 * S).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val body = RectF(2 * S, 2 * S, 44 * S, 28 * S)
        p.color = color
        c.drawRoundRect(body, 10 * S, 10 * S, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2.5f * S
        p.color = Color.WHITE
        c.drawRoundRect(body, 10 * S, 10 * S, p)
        p.style = Paint.Style.FILL
        p.color = Color.argb(90, 255, 255, 255)
        c.drawRoundRect(RectF(9 * S, 8 * S, 24 * S, 22 * S), 5 * S, 5 * S, p)
        p.color = if (me) Color.WHITE else Color.parseColor("#1B2430")
        c.drawCircle(34 * S, 11 * S, 2.1f * S, p)
        c.drawCircle(34 * S, 19 * S, 2.1f * S, p)
        return bmp
    }

    fun tag(text: String, color: Int, me: Boolean): Bitmap =
        pill(
            text = text,
            bg = if (me) color else Color.WHITE,
            fg = if (me) Color.WHITE else Color.parseColor("#1B2430"),
            border = if (me) color else Color.parseColor("#E2E6EA"),
            sizeSp = 13f,
            bold = true,
        )

    fun bubble(text: String): Bitmap =
        pill(
            text = text,
            bg = Color.parseColor("#FFC845"),
            fg = Color.parseColor("#1B2430"),
            border = Color.parseColor("#FFC845"),
            sizeSp = 15f,
            bold = true,
            padH = 14f,
            padV = 8f,
        )

    private fun pill(
        text: String,
        bg: Int,
        fg: Int,
        border: Int,
        sizeSp: Float,
        bold: Boolean,
        padH: Float = 10f,
        padV: Float = 5f,
    ): Bitmap {
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sizeSp * S
            color = fg
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        val tw = tp.measureText(text)
        val fm = tp.fontMetrics
        val th = fm.descent - fm.ascent
        val w = (tw + padH * 2 * S).toInt() + 4
        val h = (th + padV * 2 * S).toInt() + 4
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = RectF(2f, 2f, w - 2f, h - 2f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = bg
        c.drawRoundRect(r, h / 2f, h / 2f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f
        p.color = border
        c.drawRoundRect(r, h / 2f, h / 2f, p)
        c.drawText(text, (w - tw) / 2f, h / 2f - (fm.ascent + fm.descent) / 2f, tp)
        return bmp
    }
}
