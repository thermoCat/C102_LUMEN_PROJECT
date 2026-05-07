package com.ssafy.smartcane.lumen2.ble

import com.ssafy.smartcane.lumen2.assist.AssistCommand
import com.ssafy.smartcane.lumen2.assist.AssistDecision
import com.ssafy.smartcane.lumen2.assist.DepthAnomalyStatus

/**
 * Lumen2 AssistEngine 의 corridor zone 판정을 BLE 진동 명령으로 변환한다.
 *
 * 방향 매핑 (보행가능 바 안에 obstacle 위치 = 진동 방향):
 *  - 정면(양쪽)  → B (Both)
 *  - 오른쪽     → R
 *  - 왼쪽       → L
 *  - 보행가능 바에 obstacle 없음 → "O" (직전이 진동 중일 때 1회 OFF)
 *
 * 거리 레벨 (3m / 1.5m / 0.8m):
 *  - 0.8m 이내 (immediate) → 3
 *  - 1.5m 이내 (near)       → 2
 *  - 3.0m 이내 (plan / depth anomaly) → 1
 *
 * AssistCommand → BLE 매핑:
 *  - STOP                                                  → B3   (즉각 정면, 양쪽 강진동)
 *  - FRONT_LIMIT, BOTH_SIDE_SPACE                          → B2   (near 정면, 양쪽)
 *  - LEFT_SPACE  (왼쪽 비어있음 = 오른쪽이 obstacle)        → R2
 *  - RIGHT_SPACE (오른쪽 비어있음 = 왼쪽이 obstacle)        → L2
 *  - FRONT_CAUTION                                         → B1   (plan 정면)
 *  - DEPTH_CAUTION + LEFT/RIGHT/CENTER/MULTIPLE            → L1/R1/B1/B1
 *  - KEEP / CAMERA_ADJUST / SYSTEM_UNSTABLE                → 무신호 (필요시 OFF)
 *
 * "걸릴때마다 보내주기" 정책: 같은 명령이라도 프레임마다 발견되면 매번 동일 명령을 송신한다.
 */
class ProximityVibrationController(private val send: (String) -> Unit) {

    private var lastCommand: String? = null

    fun update(decision: AssistDecision) {
        val cmd = commandFor(decision.command, decision.awareness.depthAnomaly)
        when {
            cmd != null -> {
                send(cmd)
                lastCommand = cmd
            }
            lastCommand != null -> {
                send("O")
                lastCommand = null
            }
            else -> Unit
        }
    }

    fun reset() {
        if (lastCommand != null) {
            send("O")
        }
        lastCommand = null
    }

    private fun commandFor(cmd: AssistCommand, depth: DepthAnomalyStatus): String? {
        return when (cmd) {
            AssistCommand.STOP -> "B3"
            AssistCommand.FRONT_LIMIT,
            AssistCommand.BOTH_SIDE_SPACE -> "B2"
            AssistCommand.LEFT_SPACE -> "R2"   // 왼쪽 여유 → obstacle은 오른쪽
            AssistCommand.RIGHT_SPACE -> "L2"  // 오른쪽 여유 → obstacle은 왼쪽
            AssistCommand.FRONT_CAUTION -> "B1"
            AssistCommand.DEPTH_CAUTION -> when (depth) {
                DepthAnomalyStatus.LEFT -> "L1"
                DepthAnomalyStatus.RIGHT -> "R1"
                DepthAnomalyStatus.CENTER,
                DepthAnomalyStatus.MULTIPLE -> "B1"
                DepthAnomalyStatus.CLEAR,
                DepthAnomalyStatus.UNKNOWN -> null
            }
            AssistCommand.KEEP,
            AssistCommand.CAMERA_ADJUST,
            AssistCommand.SYSTEM_UNSTABLE -> null
        }
    }
}
