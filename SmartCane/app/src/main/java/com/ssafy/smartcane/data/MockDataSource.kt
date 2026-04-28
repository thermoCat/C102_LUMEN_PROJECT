package com.ssafy.smartcane.data

import com.ssafy.smartcane.data.model.NavigationState
import kotlin.math.cos
import kotlin.math.sin

object MockDataSource {

    private const val HORIZON_Y = 0.50f

    private val PATH = listOf(
        listOf(0.50f, 0.96f), listOf(0.50f, 0.86f), listOf(0.50f, 0.75f),
        listOf(0.50f, 0.67f), listOf(0.50f, 0.60f), listOf(0.50f, 0.55f)
    )
    private val MASK = listOf(
        listOf(0.12f, 1.00f), listOf(0.88f, 1.00f),
        listOf(0.53f, HORIZON_Y + 0.01f), listOf(0.47f, HORIZON_Y + 0.01f)
    )

    fun getMockNavigationState() = NavigationState("CENTER", PATH, MASK)

    fun getAnimatedState(): NavigationState {
        val t      = System.currentTimeMillis() / 1000.0
        val sway   = (sin(t * 1.9)  * 0.030).toFloat()
        val jitter = (sin(t * 12.1) * 0.007 + cos(t * 7.6) * 0.005).toFloat()

        val path = listOf(
            listOf(0.50f + sway          + jitter, 0.96f),
            listOf(0.50f + sway * 0.78f,           0.86f),
            listOf(0.50f + sway * 0.55f,           0.75f),
            listOf(0.50f + sway * 0.35f,           0.67f),
            listOf(0.50f + sway * 0.18f + jitter * 0.3f, 0.60f),
            listOf(0.50f + sway * 0.07f,           0.55f)
        )
        val mask = listOf(
            listOf(0.50f - 0.38f + sway,         1.00f),
            listOf(0.50f + 0.38f + sway,         1.00f),
            listOf(0.50f + 0.03f + sway * 0.07f, HORIZON_Y + 0.01f),
            listOf(0.50f - 0.03f + sway * 0.07f, HORIZON_Y + 0.01f)
        )
        val state = when {
            sway >  0.022f -> "RIGHT"
            sway < -0.022f -> "LEFT"
            else           -> "CENTER"
        }
        return NavigationState(state, path, mask)
    }
}
