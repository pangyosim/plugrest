package ai.pleos.plugrest.ui

import ai.pleos.plugrest.BuildConfig
import ai.pleos.plugrest.session.ChargeRateMemory
import ai.pleos.plugrest.session.EndReason
import ai.pleos.plugrest.session.Phase
import ai.pleos.plugrest.vehicle.FakeVehicleGateway
import ai.pleos.plugrest.vehicle.Feature
import ai.pleos.plugrest.vehicle.Gear
import ai.pleos.plugrest.vehicle.SeatMode
import ai.pleos.plugrest.vehicle.VehicleState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ---------- 공통 위젯 ----------

@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
              bg: Color = C.primary, fg: Color = C.textPrimary, height: Dp = D.buttonL, radius: Dp = D.radiusL) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier
            .fillMaxWidth().height(height).clip(RoundedCornerShape(radius))
            .background(if (enabled) bg else C.disabledBg)
            .clickable(enabled = enabled) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(text, style = T.titleS, color = if (enabled) fg else C.disabledText) }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(D.buttonM).clip(RoundedCornerShape(D.radiusM)).background(C.surface)
            .then(if (selected) Modifier.border(4.dp, C.primary, RoundedCornerShape(D.radiusM)) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = T.titleS, color = C.textPrimary) }
}

@Composable
fun Screen(bg: Color, modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().background(bg).padding(D.screenPad), content = content)
}

@Composable
fun SocRing(soc: Int, target: Int, ring: Color, text: Color, modifier: Modifier = Modifier) {
    // 화면 전체 기준 min(폭×0.65, 높이×0.45), 최대 520dp (tokens.md)
    val cfg = LocalConfiguration.current
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // 남은 높이(Fake 패널 열림 등)보다 커지지 않게
        val d = min(min(min(cfg.screenWidthDp * 0.65f, cfg.screenHeightDp * 0.45f), 520f), maxHeight.value).dp
        Box(Modifier.size(d), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = D.ringStroke.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(C.track, -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                drawArc(ring, -90f, 360f * soc / 100f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                // 목표 눈금
                val a = Math.toRadians((-90 + 360.0 * target / 100)).toFloat()
                val r = size.minDimension / 2
                val c = Offset(size.width / 2, size.height / 2)
                drawLine(text, c + Offset(cos(a), sin(a)) * (r - stroke), c + Offset(cos(a), sin(a)) * r, strokeWidth = 6.dp.toPx())
            }
            // 160sp @ 520dp 링 = 0.31. 링이 작아지면(패널 열림 등) 같이 줄인다.
            Text("$soc%", style = T.displayXL.copy(fontSize = (d.value * 0.31f).sp, lineHeight = (d.value * 0.33f).sp), color = text, maxLines = 1, softWrap = false)
        }
    }
}

/** 1초마다 재구성 트리거 (남은 시간·카운트다운). 애니메이션 아님. */
@Composable
fun tick(): Long {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1_000); now = System.currentTimeMillis() } }
    return now
}

// ---------- 화면 ----------

@Composable
fun IdleScreen(s: VehicleState, blockReason: String?, rates: ChargeRateMemory, onStart: (Int, SeatMode) -> Unit) {
    var target by remember { mutableStateOf(80) }
    var seat by remember { mutableStateOf(SeatMode.NONE) }
    val learned by rates.rates.collectAsState()
    val eta = s.soc?.let { rates.estimateMinutes(it, target) }?.takeIf { it > 0 }
    Screen(C.bg) {
        Text("충전쉼표", style = T.titleL, color = C.textPrimary)
        Spacer(Modifier.height(D.section))
        Text(s.soc?.let { "$it%" } ?: "--", style = T.displayL, color = C.textPrimary)
        Text(
            when {
                !s.connected -> "차량 연결 중…"
                blockReason == null -> "충전 중 · 기어 ${s.gear?.label() ?: "P"}"
                else -> "대기 중 · 기어 ${s.gear?.label() ?: "-"}"
            } + DEMO_TAG, style = T.bodyL, color = C.textSecondary,
        )
        Spacer(Modifier.height(D.section))
        Text("목표 충전량", style = T.labelM, color = C.textTertiary)
        Spacer(Modifier.height(D.gap / 2))
        Row(horizontalArrangement = Arrangement.spacedBy(D.gap)) {
            listOf(80, 90).forEach { t -> Chip("$t%", target == t, { target = t }, Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(D.gap))
        Text(if (eta != null) "지난 충전 ${learned.count}회 기준, $target%까지 보통 약 ${eta}분" else "시작 후 실제 충전 속도로 남은 시간을 계산해요", style = T.bodyS, color = C.textSecondary)
        // 디자인 ⑦: labelS·밑줄·터치 64dp·왼쪽 정렬, 텍스트 버튼이 여백 역할이라 아래 간격 48→24
        if (learned.count > 0) Box(Modifier.height(64.dp).clickable { rates.clear() }, contentAlignment = Alignment.CenterStart) {
            Text("학습 기록 지우기", style = T.labelS.copy(textDecoration = TextDecoration.Underline), color = C.textTertiary)
        }
        Spacer(Modifier.height(if (learned.count > 0) D.gap else D.section))
        Text("시트 통풍", style = T.labelM, color = C.textSecondary)
        Spacer(Modifier.height(D.gap))
        Row(horizontalArrangement = Arrangement.spacedBy(D.gap)) {
            Chip("끔", seat == SeatMode.NONE, { seat = SeatMode.NONE }, Modifier.weight(1f))
            if (Feature.SEAT_VENT !in s.unsupported) Chip("켬", seat == SeatMode.VENT, { seat = SeatMode.VENT }, Modifier.weight(1f))
        }
        Spacer(Modifier.weight(1f))
        BigButton("휴식 모드 시작", { onStart(target, seat) }, enabled = blockReason == null)
        Spacer(Modifier.height(D.gap / 2))
        Text(blockReason ?: "목표 $target%에 도달하면 깨워 드릴게요", style = T.bodyS, color = C.textTertiary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun RestingScreen(p: Phase.Resting, s: VehicleState, remaining: Int?, learned: Int?, onStop: () -> Unit, onTouch: () -> Unit) {
    Screen(C.bgRest, Modifier.pointerInput(Unit) { detectTapGestures { onTouch() } }) {
        Text("휴식 중 · 목표 ${p.target}%$DEMO_TAG", style = T.labelM, color = C.restTextDim)
        Spacer(Modifier.height(D.gap))
        SocRing(s.soc ?: 0, p.target, C.restCharge, C.restText, Modifier.weight(1f, fill = false))
        Spacer(Modifier.weight(0.01f))
        // 디자인 ⑧⑨: remaining은 실측 기울기가 있을 때만 값(고정 가정 없음). 계산 중엔 학습값 보조 줄, 실측이 생기면 보조 줄 대신 "실측" 태그 (학습값을 보여준 세션만)
        val learnedEta = learned?.takeIf { it > 0 }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text("남은 시간", style = T.labelM, color = C.restTextDim)
            if (remaining != null && learnedEta != null) Text("실측", style = T.labelS, color = C.restTextDim,
                modifier = Modifier.padding(start = 16.dp).border(2.dp, C.restTextDim, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 4.dp))
        }
        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            if (remaining == null) Text("계산 중…", style = T.titleL, color = C.restTextDim)
            else Text("약 ${remaining}분", style = T.displayL, color = C.restText)
        }
        if (remaining == null && learnedEta != null) Text("지난 충전 기준 약 ${learnedEta}분", style = T.bodyS, color = C.restTextDim, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(D.gap))
        if (p.halfRecline) Text("뒷좌석에 사람이 있어 등받이를 절반만 젖혔어요", style = T.bodyS, color = C.restTextDim, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        // 착좌 안내가 있으면 등받이 줄은 중복이라 생략(3줄 유지)
        val seatLine = when {
            p.halfRecline && p.seatMode == SeatMode.VENT -> "통풍 시트" to Feature.SEAT_VENT
            p.halfRecline -> null
            p.seatMode == SeatMode.VENT -> "등받이 리클라인 · 통풍" to Feature.SEAT_VENT
            else -> "등받이 리클라인" to Feature.SEAT_RECLINE
        }
        listOfNotNull(seatLine, "앰비언트 어둡게 · 실내등 끔" to Feature.AMBIENT, "공조 22°C · 풍량 1단" to Feature.HVAC).forEach { (label, f) ->
            Text(label + if (f in s.unsupported) " · 이 차량 미지원" else "", style = T.bodyS, color = C.restTextDim, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        if (s.seatMoving) Text("시트 이동 중 · 화면을 누르면 멈춥니다", style = T.bodyS, color = C.restTextDim, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(D.section))
        BigButton("종료", onStop, bg = C.surfaceRest, fg = C.restText)
    }
}

@Composable
fun RestoringScreen(p: Phase.Restoring, s: VehicleState, now: Long, onCancel: () -> Unit, onTouch: () -> Unit) {
    val secs = ((p.endsAt - now + 999) / 1000).coerceIn(0, 10)
    if (p.silent) { // 재정차 제안에서 "되돌리기" → 예고·취소 없음
        Screen(C.bgRest, Modifier.pointerInput(Unit) { detectTapGestures { onTouch() } }) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("되돌리는 중…", style = T.titleL, color = C.restText) }
            if (s.seatMoving) Text("시트 이동 중 · 화면을 누르면 멈춥니다", style = T.bodyS, color = C.restTextDim, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        return
    }
    Screen(C.bgRest, Modifier.pointerInput(Unit) { detectTapGestures { onTouch() } }) {
        Text(when (p.reason) { EndReason.TARGET_REACHED -> "목표 ${p.target}% 도달"; EndReason.CHARGING_STOPPED -> "충전 중단"; else -> "휴식 모드 종료" }, style = T.labelM, color = C.restTextDim)
        Spacer(Modifier.height(D.gap))
        if (p.reason == EndReason.TARGET_REACHED) Text("목표 ${p.target}%에 도달했습니다", style = T.bodyL, color = C.restText, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text("${secs}초 후 시트를 원래대로", style = T.bodyL, color = C.restText, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("$secs", style = T.displayXL, color = C.restText)
            Text("초", style = T.titleL, color = C.restTextDim)
        }
        if (s.seatMoving) Text("시트 이동 중 · 화면을 누르면 멈춥니다", style = T.bodyS, color = C.restTextDim, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text("취소하면 되돌리지 않고 지금 상태 그대로 끝냅니다", style = T.bodyS, color = C.restTextDim, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(D.gap))
        BigButton("취소", onCancel, bg = C.surfaceRest, fg = C.restText)
    }
}

@Composable
fun DoneScreen(p: Phase.Done, s: VehicleState, onConfirm: () -> Unit, onRestore: () -> Unit, onKeep: () -> Unit) {
    Screen(C.bg) {
        Text(if (p.reason == EndReason.TARGET_REACHED) "목표 ${p.target}% 도달" else "휴식 모드 종료", style = T.headlineL, color = C.textPrimary)
        Spacer(Modifier.height(D.gap))
        Text(if (p.restored) "시트·조명·공조를 원래대로 되돌렸어요." else "시트·조명·공조는 지금 상태 그대로예요.", style = T.bodyL, color = C.textSecondary)
        Spacer(Modifier.height(D.section))
        val warnings = buildList {
            if (s.rearSeatOccupied == true) add("뒷좌석에 사람이 있어요")
            s.openWindows.forEach { add("$it 창문이 열려 있어요") }
            if (s.cabinLightOn) add("실내등이 켜져 있어요")
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(D.cardRadius)).background(C.surface).padding(D.cardPad), verticalArrangement = Arrangement.spacedBy(D.gap)) {
            Text("내리기 전 확인", style = T.titleS, color = C.textPrimary)
            if (warnings.isEmpty()) Text("확인할 것 없음", style = T.bodyL, color = C.textSecondary)
            else warnings.forEach {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(D.gap / 2)) {
                    Icon(Icons.Filled.Warning, contentDescription = "주의", tint = C.warn, modifier = Modifier.size(40.dp))
                    Text(it, style = T.bodyL, color = C.textPrimary)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (p.offerRestore) {
            Text("시트를 원래대로 되돌릴까요?", style = T.bodyL, color = C.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(D.gap))
            Row(horizontalArrangement = Arrangement.spacedBy(D.gap)) {
                BigButton("되돌리기", onRestore, Modifier.weight(1f))
                BigButton("그대로", onKeep, Modifier.weight(1f), bg = C.surface)
            }
        } else BigButton("확인", onConfirm)
    }
}

/** Fake 빌드 전용: 하단 접이식 조작 패널 + set 호출 로그. */
@Composable
fun FakePanel(fake: FakeVehicleGateway) {
    var open by remember { mutableStateOf(false) }
    val s by fake.state.collectAsState()
    val log by fake.log.collectAsState()
    Column(Modifier.fillMaxWidth().background(C.surface).padding(horizontal = D.gap)) {
        // 접힌 상태는 24dp 핸들만 (텍스트 없음)
        Box(Modifier.fillMaxWidth().height(24.dp).clickable { open = !open }, contentAlignment = Alignment.Center) {
            Box(Modifier.size(width = 64.dp, height = 6.dp).clip(RoundedCornerShape(3.dp)).background(C.textTertiary))
        }
        if (!open) return@Column
        Text("Fake 차량 패널", style = T.labelM, color = C.textSecondary, modifier = Modifier.padding(bottom = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SOC ${s.soc}%", style = T.labelM, color = C.textPrimary, modifier = Modifier.weight(0.3f))
            Slider(value = (s.soc ?: 0).toFloat(), onValueChange = { fake.setSoc(it.toInt()) }, valueRange = 0f..100f, modifier = Modifier.weight(0.7f))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Toggle("충전 포트", s.chargePortConnected == true, Modifier.weight(1f)) { fake.setPortConnected(it) }
            Toggle("기어 D", s.gear == Gear.DRIVE, Modifier.weight(1f)) { fake.setGear(if (it) Gear.DRIVE else Gear.PARK) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Toggle("뒷좌석 착좌", s.rearSeatOccupied == true, Modifier.weight(1f)) { fake.setRearOccupied(it) }
            Toggle("브레이크", s.brakePressed, Modifier.weight(1f)) { fake.setBrake(it) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Toggle("조수석 창문 열림", "조수석" in s.openWindows, Modifier.weight(1f)) { fake.setPassengerWindowOpen(it) }
            Toggle("실내등 켜짐", s.cabinLightOn, Modifier.weight(1f)) { fake.setCabinLight(it) }
        }
        Column(Modifier.height(120.dp).verticalScroll(rememberScrollState())) {
            log.forEach { Text(it, style = TextStyle(fontSize = androidx.compose.ui.unit.TextUnit(18f, androidx.compose.ui.unit.TextUnitType.Sp)), color = C.textSecondary) }
        }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = checked, onCheckedChange = onChange)
        Text(label, style = T.labelM, color = C.textPrimary)
    }
}

/** BuildConfig.DEMO: 시간이 단축된 빌드임을 심사위원에게 표시 */
private val DEMO_TAG = if (BuildConfig.DEMO) " · 시간 단축 데모" else ""

private fun Gear.label() = when (this) { Gear.PARK -> "P"; Gear.DRIVE -> "D"; Gear.REVERSE -> "R"; Gear.NEUTRAL -> "N"; Gear.UNKNOWN -> "-" }
