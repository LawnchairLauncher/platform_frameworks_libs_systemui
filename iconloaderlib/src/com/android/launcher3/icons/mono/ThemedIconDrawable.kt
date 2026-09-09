/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.launcher3.icons.mono

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.FastBitmapDrawable
import com.android.launcher3.icons.R

import app.lawnchair.icons.shouldTransparentBGIcons

/** Class to handle monochrome themed app icons */
class ThemedIconDrawable(constantState: ThemedConstantState) :
    FastBitmapDrawable(constantState.bitmapInfo) {
    private val colorFg = constantState.colorFg
    private val colorBg = constantState.colorBg

    // The foreground/monochrome icon for the app
    private val monoIcon = constantState.mono
    private val monoFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(colorFg, BlendModeCompat.SRC_IN)
    private val monoPaint =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { colorFilter = monoFilter }

    private val bgBitmap = constantState.whiteShadowLayer
    private val bgFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(colorBg, BlendModeCompat.SRC_IN)
    private val mBgPaint =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { colorFilter = bgFilter }

    override fun drawInternal(canvas: Canvas, bounds: Rect) {
        canvas.drawBitmap(bgBitmap, null, bounds, mBgPaint)
        canvas.drawBitmap(monoIcon, null, bounds, monoPaint)
    }

    override fun updateFilter() {
        super.updateFilter()
        val alpha = if (isDisabled) (disabledAlpha * FULLY_OPAQUE).toInt() else FULLY_OPAQUE
        mBgPaint.alpha = alpha
        mBgPaint.setColorFilter(
            if (isDisabled) BlendModeColorFilterCompat.createBlendModeColorFilterCompat(getDisabledColor(colorBg), BlendModeCompat.SRC_IN) else bgFilter,
        )

        monoPaint.alpha = alpha
        monoPaint.setColorFilter(
            if (isDisabled) BlendModeColorFilterCompat.createBlendModeColorFilterCompat(getDisabledColor(colorFg), BlendModeCompat.SRC_IN) else monoFilter,
        )
    }

    override fun isThemed() = true

    override fun newConstantState() =
        ThemedConstantState(bitmapInfo, monoIcon, bgBitmap, colorBg, colorFg)

    override fun getIconColor() = colorFg

    class ThemedConstantState(
        bitmapInfo: BitmapInfo,
        val mono: Bitmap,
        val whiteShadowLayer: Bitmap,
        val colorBg: Int,
        val colorFg: Int,
    ) : FastBitmapConstantState(bitmapInfo) {

        public override fun createDrawable() = ThemedIconDrawable(this)
    }

    companion object {
        const val TAG: String = "ThemedIconDrawable"

        /** Get an int array representing background and foreground colors for themed icons */
        @JvmStatic
        fun getColors(context: Context): IntArray {
            // Always resolve through COLORS_LOADER so the host app's theme (which may follow an
            // in-app preference rather than the system night mode) decides the colors. Transparent
            // backgrounds only drop the background color; the foreground still comes from the host.
            val colors = COLORS_LOADER(context)
            return if (context.shouldTransparentBGIcons()) {
                intArrayOf(Color.TRANSPARENT, colors[1])
            } else {
                colors
            }
        }

        @JvmStatic
        var COLORS_LOADER: (Context) -> IntArray = { context ->
            val res = context.resources
            intArrayOf(
                res.getColor(R.color.themed_icon_background_color),
                res.getColor(R.color.themed_icon_color),
            )
        }
    }
}
