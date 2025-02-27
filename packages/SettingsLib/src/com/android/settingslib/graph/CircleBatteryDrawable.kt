/*
 * Copyright (C) 2017 The Android Open Source Project
 * Copyright (C) 2019 The LineageOS Project
 * Copyright (C) 2020 Paranoid Android
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.settingslib.graph

import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.TypedValue
import com.android.settingslib.R
import com.android.settingslib.Utils
import kotlin.math.max
import kotlin.math.min

class CircleBatteryDrawable(private val context: Context, frameColor: Int) : Drawable() {
    private val backPaint: Paint
    private val frontPaint: Paint
    private val chargingPaint: Paint
    private val plusPaint: Paint
    private val powerSavePaint: Paint
    private val colors: IntArray
    private val padding = Rect()
    private val frame = RectF()

    private var chargeColor: Int
    private var iconTint = Color.WHITE
    private var intrinsicWidth: Int
    private var intrinsicHeight: Int
    private var height = 0
    private var width = 0

    // Dual tone implies that battery level is a clipped overlay over top of the whole shape
    private var dualTone = false

    override fun getIntrinsicHeight() = intrinsicHeight

    override fun getIntrinsicWidth() = intrinsicWidth

    var charging = false
        set(value) {
            field = value
            postInvalidate()
        }

    var powerSaveEnabled = false
        set(value) {
            field = value
            postInvalidate()
        }

    var showPercent = false
        set(value) {
            field = value
            postInvalidate()
        }

    var batteryLevel = -1
        set(value) {
            field = value
            postInvalidate()
        }

    // an approximation of View.postInvalidate()
    private fun postInvalidate() {
        unscheduleSelf { invalidateSelf() }
        scheduleSelf({ invalidateSelf() }, 0)
    }

    override fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
        super.setBounds(left, top, right, bottom)
        updateSize()
    }

    private fun updateSize() {
        val res = context.resources
        height = bounds.bottom - padding.bottom - (bounds.top + padding.top)
        width = bounds.right - padding.right - (bounds.left + padding.left)
        intrinsicHeight = res.getDimensionPixelSize(R.dimen.battery_height)
        intrinsicWidth = res.getDimensionPixelSize(R.dimen.battery_height)
    }

    override fun getPadding(padding: Rect): Boolean {
        if (this.padding.left == 0 &&
            this.padding.top == 0 &&
            this.padding.right == 0 &&
            this.padding.bottom == 0
        ) {
            return super.getPadding(padding)
        }
        padding.set(this.padding)
        return true
    }

    private fun getColorForLevel(percent: Int): Int {
        var thresh: Int
        var color = 0
        var i = 0
        while (i < colors.size) {
            thresh = colors[i]
            color = colors[i + 1]
            if (percent <= thresh) {
                // Respect tinting for "normal" level
                return if (i == colors.size - 2) {
                    iconTint
                } else {
                    color
                }
            }
            i += 2
        }
        return color
    }

    private fun batteryColorForLevel(level: Int) =
        if (charging || powerSaveEnabled)
            chargeColor
        else
            getColorForLevel(level)

    fun setColors(fgColor: Int, bgColor: Int, singleToneColor: Int) {
        val fillColor = if (dualTone) fgColor else singleToneColor
        iconTint = fillColor
        backPaint.color = bgColor
        chargeColor = fillColor
        invalidateSelf()
    }

    override fun draw(c: Canvas) {
        if (batteryLevel == -1) return
        val circleSize = min(width, height)
        val strokeWidth = circleSize / 6.5f
        backPaint.strokeWidth = strokeWidth
        backPaint.style = Paint.Style.STROKE
        frontPaint.strokeWidth = strokeWidth
        frontPaint.style = Paint.Style.STROKE
        powerSavePaint.strokeWidth = strokeWidth
        frame[
                strokeWidth / 2.0f + padding.left, strokeWidth / 2.0f,
                circleSize - strokeWidth / 2.0f + padding.left
        ] = circleSize - strokeWidth / 2.0f
        frontPaint.color = batteryColorForLevel(batteryLevel)
        chargingPaint.color = batteryColorForLevel(batteryLevel)
        if (charging) {
            val cf = (frame.right - frame.left) / 4.0f
            c.drawCircle(frame.centerX(), frame.centerY(), cf, chargingPaint)
        }
        c.drawArc(frame, 270f, 360f, false, backPaint)
        if (batteryLevel > 0) {
            if (!charging && powerSaveEnabled) {
                c.drawArc(frame, 270f, 3.6f * batteryLevel, false, powerSavePaint)
            } else {
                c.drawArc(frame, 270f, 3.6f * batteryLevel, false, frontPaint)
            }
        }
    }

    // Some stuff required by Drawable.
    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {
        backPaint.colorFilter = colorFilter
        frontPaint.colorFilter = colorFilter
        chargingPaint.colorFilter = colorFilter
        plusPaint.colorFilter = colorFilter
    }

    override fun getOpacity() = PixelFormat.UNKNOWN

    companion object {
        private fun loadPoints(
            res: Resources,
            pointArrayRes: Int
        ): FloatArray {
            val pts = res.getIntArray(pointArrayRes)
            var maxX = 0
            var maxY = 0
            run {
                var i = 0
                while (i < pts.size) {
                    maxX = max(maxX, pts[i])
                    maxY = max(maxY, pts[i + 1])
                    i += 2
                }
            }
            val ptsF = FloatArray(pts.size)
            var i = 0
            while (i < pts.size) {
                ptsF[i] = pts[i].toFloat() / maxX
                ptsF[i + 1] = pts[i + 1].toFloat() / maxY
                i += 2
            }
            return ptsF
        }
    }

    init {
        val res = context.resources
        val color_levels = res.obtainTypedArray(R.array.batterymeter_color_levels)
        val color_values = res.obtainTypedArray(R.array.batterymeter_color_values)
        colors = IntArray(2 * color_levels.length())
        for (i in 0 until color_levels.length()) {
            colors[2 * i] = color_levels.getInt(i, 0)
            if (color_values.getType(i) == TypedValue.TYPE_ATTRIBUTE) {
                colors[2 * i + 1] = Utils.getColorAttrDefaultColor(
                    context,
                    color_values.getThemeAttributeId(i, 0)
                )
            } else {
                colors[2 * i + 1] = color_values.getColor(i, 0)
            }
        }
        color_levels.recycle()
        color_values.recycle()
        backPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        backPaint.color = frameColor
        backPaint.isDither = true
        frontPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        frontPaint.isDither = true
        chargingPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        chargingPaint.style = Paint.Style.FILL
        chargeColor = Utils.getColorStateListDefaultColor(context, R.color.meter_consumed_color)
        plusPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        plusPaint.color = Utils.getColorStateListDefaultColor(
            context,
            R.color.batterymeter_plus_color
        )
        powerSavePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        powerSavePaint.color = plusPaint.color
        powerSavePaint.style = Paint.Style.STROKE
        intrinsicWidth = res.getDimensionPixelSize(R.dimen.battery_width)
        intrinsicHeight = res.getDimensionPixelSize(R.dimen.battery_height)

        dualTone = res.getBoolean(com.android.internal.R.bool.config_batterymeterDualTone)
    }
}
