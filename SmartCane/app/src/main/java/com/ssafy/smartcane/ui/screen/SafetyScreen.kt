package com.ssafy.smartcane.ui.screen

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.smartcane.lumen2.SafetyWalkService
import com.ssafy.smartcane.ui.NavTab
import com.ssafy.smartcane.ui.component.BottomNav
import com.ssafy.smartcane.ui.theme.AppWhite
import com.ssafy.smartcane.ui.theme.NavBarBg
import com.ssafy.smartcane.ui.theme.NavGray
import android.content.Intent
import com.ssafy.smartcane.lumen2.SafetyWalkActivity

private val SafetyAccent = Color(0xFF005387)
private val SafetyBackground = Color(0xFF001B2B)

@Composable
fun SafetyScreen(
    onTabChange: (NavTab) -> Unit,
    onEnabledChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(SafetyWalkService.userEnabled) }
    val background = if (enabled) SafetyBackground else NavBarBg

    // 화면이 다시 보일 때 서비스 상태와 동기화
    LaunchedEffect(Unit) {
        enabled = SafetyWalkService.userEnabled
    }

    // 카메라 권한 요청 → 허용 시 서비스 시작, 거부 시 토글 해제
    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            SafetyWalkService.start(context)
        } else {
            enabled = false
        }
    }

    fun startService() {
        val hasPerm = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) SafetyWalkService.start(context)
        else cameraPermLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(enabled) {
        onEnabledChange(enabled)
        if (!enabled) SafetyWalkService.stop(context)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            SafetyCenteredContent(
                enabled = enabled,
                onButtonClick = {
                    if (!enabled) { enabled = true; startService() }
                    else { enabled = false }
                },
                onToggle = {
                    if (!enabled) { enabled = true; startService() }
                    else { enabled = false }
                },
                onVisualizeClick = {
                    val intent = Intent(context, SafetyWalkActivity::class.java)
                    context.startActivity(intent)
                }
            )
        }
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            BottomNav(
                active = NavTab.Safety,
                onTab = { tab ->
                    enabled = false
                    if (tab != NavTab.Safety) onTabChange(tab)
                },
                backgroundColor = background
            )
        }
    }
}

@Composable
private fun SafetyCenteredContent(
    enabled: Boolean,
    onButtonClick: () -> Unit,
    onToggle: () -> Unit,
    onVisualizeClick: () -> Unit
) {
    val gap = 32.dp
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            SafetyStatusText(enabled = enabled)
            SafetyActionButton(enabled = enabled, onClick = onButtonClick)
            VisualizationOption(enabled = enabled, onClick = onVisualizeClick)
            SafetySwitch(enabled = enabled, onToggle = onToggle)
        }
    ) { measurables, constraints ->
        val textPlaceable = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0))
        val buttonPlaceable = measurables[1].measure(constraints.copy(minWidth = 0, minHeight = 0))
        val vizPlaceable = measurables[2].measure(constraints.copy(minWidth = 0, minHeight = 0))
        val switchPlaceable = measurables[3].measure(constraints.copy(minWidth = 0, minHeight = 0))
        val gapPx = gap.roundToPx()

        layout(constraints.maxWidth, constraints.maxHeight) {
            val buttonX = (constraints.maxWidth - buttonPlaceable.width) / 2
            val buttonY = (constraints.maxHeight - buttonPlaceable.height) / 2
            
            val textX = (constraints.maxWidth - textPlaceable.width) / 2
            val textY = buttonY - gapPx - textPlaceable.height
            
            val vizX = (constraints.maxWidth - vizPlaceable.width) / 2
            val vizY = buttonY + buttonPlaceable.height + (gapPx / 2)

            val switchX = (constraints.maxWidth - switchPlaceable.width) / 2
            val switchY = vizY + vizPlaceable.height + (gapPx / 2)

            textPlaceable.placeRelative(textX, textY.coerceAtLeast(0))
            buttonPlaceable.placeRelative(buttonX, buttonY)
            vizPlaceable.placeRelative(vizX, vizY)
            switchPlaceable.placeRelative(switchX, switchY)
        }
    }
}

@Composable
private fun VisualizationOption(enabled: Boolean, onClick: () -> Unit) {
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "vizOptionAlpha"
    )

    if (alpha > 0f) {
        Box(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .background(SafetyAccent.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "화면으로 보기 >",
                color = AppWhite.copy(alpha = alpha),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    } else {
        // 공간 차지를 방지하기 위해 빈 Box
        Box(modifier = Modifier.size(0.dp))
    }
}

@Composable
private fun SafetyStatusText(enabled: Boolean) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (enabled) "안전 보행 모드가 가동 중입니다." else "안전 보행 모드가 비활성 상태입니다.",
            color = AppWhite,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            lineHeight = 30.sp,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (enabled) "버튼을 누르면 안전 보행이 종료됩니다." else "버튼을 눌러 안전 보행을 활성화하세요.",
            color = NavGray,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SafetyActionButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(162.dp),
        contentAlignment = Alignment.Center
    ) {
        listOf(
            Triple(0.dp, 0.dp, 0.10f) to Pair(72.dp, 160),
            Triple(12.dp, 12.dp, 0.20f) to Pair(62.dp, 80),
            Triple(24.dp, 24.dp, 0.30f) to Pair(52.dp, 0)
        ).forEach { (layer, style) ->
            val (horizontalInset, verticalInset, targetAlpha) = layer
            val (cornerRadius, delayMillis) = style
            val animatedAlpha by animateFloatAsState(
                targetValue = if (enabled) targetAlpha else 0f,
                animationSpec = tween(durationMillis = 220, delayMillis = delayMillis),
                label = "safetyHoverAlpha"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalInset, vertical = verticalInset)
                    .height(162.dp - (verticalInset * 2))
                    .background(SafetyAccent.copy(alpha = animatedAlpha), RoundedCornerShape(cornerRadius))
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp, vertical = 36.dp)
                .height(90.dp)
                .background(if (enabled) SafetyAccent else Color(0xFF222222), RoundedCornerShape(42.dp))
                .semantics {
                    role = Role.Button
                    contentDescription = if (enabled) "안전 보행 중지" else "안전 보행 시작"
                    stateDescription = if (enabled) "켜짐" else "꺼짐"
                }
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (enabled) "안전 보행 끄기" else "안전 보행 켜기",
                color = if (enabled) AppWhite else Color(0xFFA7A7A7),
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun SafetySwitch(enabled: Boolean, onToggle: () -> Unit) {
    val thumbOffsetX by animateDpAsState(
        targetValue = if (enabled) 34.dp else 0.dp,
        animationSpec = tween(durationMillis = 220),
        label = "safetySwitchThumb"
    )

    Box(
        modifier = Modifier
            .size(width = 68.dp, height = 34.dp)
            .background(if (enabled) SafetyAccent else Color(0xFF222222), RoundedCornerShape(17.dp))
            .semantics {
                role = Role.Switch
                contentDescription = "안전 보행"
                stateDescription = if (enabled) "켜짐" else "꺼짐"
            }
            .clickable(onClick = onToggle)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffsetX)
                .size(24.dp)
                .background(Color(0xFFD9E2EA), CircleShape)
        )
    }
}
