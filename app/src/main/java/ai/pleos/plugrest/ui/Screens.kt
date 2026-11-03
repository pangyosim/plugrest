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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.text.font.FontWeight
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

/** 세그먼트 컨트롤 (v0.7): 높이 64, 트랙 r16 안쪽 4, 선택 surface r12 */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(16.dp)).background(C.segTrack).padding(4.dp)) {
        options.forEachIndexed { i, label ->
            Box(
                Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(12.dp))
                    .background(if (i == selected) C.surface else Color.Transparent).clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { Text(label, style = T.segment, color = if (i == selected) C.textPrimary else C.textSecondary) }
        }
    }
}

// ponytail: Pleos 앱 영역은 landscape 2-window(약 1900dp)까지 넓어짐. 넓으면 hero(상태)|content(조작) 두 열, 좁으면 한 열.
// 세로 고정(screenOrientation=portrait)은 Pleos 멀티윈도우에서 검은 레터박스를 만들어 제거함(기획 가이드 "app-structure-screen-model").
@Composable
fun Screen(
    bg: Color, modifier: Modifier = Modifier,
    hero: (@Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize().background(bg)) {
        if (hero != null && maxWidth >= WIDE_MIN) Row(Modifier.fillMaxSize().padding(D.screenPad)) {
            Column(Modifier.weight(0.42f).fillMaxHeight(), verticalArrangement = Arrangement.Center, content = hero)
            Spacer(Modifier.width(D.section))
            Column(Modifier.weight(0.58f).fillMaxHeight(), content = content)
        } else Column(Modifier.fillMaxSize().padding(D.screenPad)) { hero?.invoke(this); content() }
    }
}
private val WIDE_MIN = 1100.dp

@Composable
fun SocRing(soc: Int, target: Int?, ring: Color, text: Color, modifier: Modifier = Modifier, center: (@Composable (Dp) -> Unit)? = null) {
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
                // 목표 눈금 (출발 모드는 없음)
                if (target != null) {
                    val a = Math.toRadians((-90 + 360.0 * target / 100)).toFloat()
                    val r = size.minDimension / 2
                    val c = Offset(size.width / 2, size.height / 2)
                    drawLine(text, c + Offset(cos(a), sin(a)) * (r - stroke), c + Offset(cos(a), sin(a)) * r, strokeWidth = 6.dp.toPx())
                }
            }
            // 160sp @ 520dp 링 = 0.31. 링이 작아지면(패널 열림 등) 같이 줄인다.
            if (center != null) center(d)
            else Text("$soc%", style = T.displayXL.copy(fontSize = (d.value * 0.31f).sp, lineHeight = (d.value * 0.33f).sp), color = text, maxLines = 1, softWrap = false)
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
fun IdleScreen(s: VehicleState, blockReason: String?, rates: ChargeRateMemory, onStart: (target: Int, seat: SeatMode, departMin: Int) -> Unit) {
    var target by remember { mutableStateOf(80) }
    var seat by remember { mutableStateOf(SeatMode.NONE) }
    var departure by remember { mutableStateOf(false) } // 세그먼트: 배터리 목표 | 출발 시각 (PRD §4.7)
    var departMin by remember { mutableStateOf(30) }
    val learned by rates.rates.collectAsState()
    val eta = s.soc?.let { rates.estimateMinutes(it, target) }?.takeIf { it > 0 }
    val departSoc = s.soc?.let { rates.predictSoc(it, departMin) } // 학습값만, 가정 금지
    Screen(C.bg, hero = {
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
    }) {
        Text("언제 깨워 드릴까요", style = T.labelM, color = C.textTertiary)
        Spacer(Modifier.height(D.gap / 2))
        Segmented(listOf("배터리 목표", "출발 시각"), if (departure) 1 else 0, { departure = it == 1 })
        Spacer(Modifier.height(D.gap))
        Row(horizontalArrangement = Arrangement.spacedBy(D.gap)) {
            if (departure) DEPART_MINUTES.forEach { m -> Chip("${m}분 후", departMin == m, { departMin = m }, Modifier.weight(1f)) }
            else listOf(80, 90).forEach { t -> Chip("$t%", target == t, { target = t }, Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(D.gap))
        if (departure) Text(
            if (departSoc != null) "지난 충전 기준, 출발 시 약 $departSoc%" else "출발 시 배터리는 충전 속도를 확인한 뒤 알려 드려요",
            style = T.bodyS, color = if (departSoc != null) C.textSecondary else C.textTertiary,
        ) else Text(if (eta != null) "지난 충전 ${learned.count}회 기준, $target%까지 보통 약 ${eta}분" else "시작 후 실제 충전 속도로 남은 시간을 계산해요", style = T.bodyS, color = C.textSecondary)
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
        BigButton("휴식 모드 시작", { if (departure) onStart(100, seat, departMin) else onStart(target, seat, 0) }, enabled = blockReason == null)
        Spacer(Modifier.height(D.gap / 2))
        Text(blockReason ?: if (departure) "${departMin}분 후에 깨워 드릴게요" else "목표 $target%에 도달하면 깨워 드릴게요", style = T.bodyS, color = C.textTertiary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun RestingScreen(p: Phase.Resting, s: VehicleState, remaining: Int?, learned: Int?, departMinutes: Int?, predictSoc: (Int) -> Int?, onStop: () -> Unit, onTouch: () -> Unit) {
    // 디자인 ⑫: 선라이즈는 배경 1회 교체 + 글자 전부 restText(restTextDim 대비 부족)
    val dim = if (p.sunrise) C.restText else C.restTextDim
    Screen(if (p.sunrise) C.bgSunrise else C.bgRest, Modifier.pointerInput(Unit) { detectTapGestures { onTouch() } }, hero = {
        Text((if (departMinutes != null) "휴식 중 · 출발 시각 모드" else "휴식 중 · 목표 ${p.target}%") + DEMO_TAG, style = T.labelM, color = dim)
        Spacer(Modifier.height(D.gap))
        if (departMinutes != null) { // 디자인 ⑪: 출발 모드 — 링 안 "출발까지 N분", 아래 배터리·출발 시 예상
            SocRing(s.soc ?: 0, null, C.restCharge, C.restText, Modifier.weight(1f, fill = false)) { d ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("출발까지", style = T.labelM, color = dim)
                    Text("${departMinutes}분", style = T.displayXL.copy(fontSize = (d.value * 0.31f).sp, lineHeight = (d.value * 0.33f).sp), color = C.restText, maxLines = 1, softWrap = false)
                }
            }
            Spacer(Modifier.weight(0.01f))
            Text("배터리 ${s.soc ?: "--"}%", style = T.labelM, color = dim, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            val predicted = predictSoc(departMinutes)
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                if (predicted != null) Text("출발 시 약 $predicted%", style = T.titleL, color = C.restText)
                else Text("출발 시 배터리 · 계산 중…", style = T.titleL, color = dim)
            }
        } else {
            SocRing(s.soc ?: 0, p.target, C.restCharge, C.restText, Modifier.weight(1f, fill = false))
            Spacer(Modifier.weight(0.01f))
            // 디자인 ⑧⑨: remaining은 실측 기울기가 있을 때만 값(고정 가정 없음). 계산 중엔 학습값 보조 줄, 실측이 생기면 보조 줄 대신 "실측" 태그 (학습값을 보여준 세션만)
            val learnedEta = learned?.takeIf { it > 0 }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("남은 시간", style = T.labelM, color = dim)
                if (remaining != null && learnedEta != null) Text("실측", style = T.labelS, color = dim,
                    modifier = Modifier.padding(start = 16.dp).border(2.dp, dim, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 4.dp))
            }
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                if (remaining == null) Text("계산 중…", style = T.titleL, color = dim)
                else Text("약 ${remaining}분", style = T.displayL, color = C.restText)
            }
            if (remaining == null && learnedEta != null) Text("지난 충전 기준 약 ${learnedEta}분", style = T.bodyS, color = dim, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(D.gap))
    }) {
        Spacer(Modifier.weight(1f))
        // 디자인 ⑫ M1: 선라이즈 문구는 하단 상태줄 첫 줄(헤더의 데모 고지는 유지)
        if (p.sunrise) Text(if (Feature.AMBIENT in s.unsupported) "곧 깨워 드릴게요" else "곧 깨워 드릴게요 · 조명을 천천히 밝히는 중", style = T.bodyS.copy(fontWeight = FontWeight.Bold), color = C.restText, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        if (p.halfRecline) Text("뒷좌석에 사람이 있어 등받이를 절반만 젖혔어요", style = T.bodyS, color = dim, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        // 착좌 안내가 있으면 등받이 줄은 중복이라 생략(3줄 유지)
        val seatLine = when {
            p.halfRecline && p.seatMode == SeatMode.VENT -> "통풍 시트" to Feature.SEAT_VENT
            p.halfRecline -> null
            p.seatMode == SeatMode.VENT -> "등받이 리클라인 · 통풍" to Feature.SEAT_VENT
            else -> "등받이 리클라인" to Feature.SEAT_RECLINE
        }
        listOfNotNull(seatLine, (if (p.sunrise) "앰비언트 밝히는 중 · 실내등 끔" else "앰비언트 어둡게 · 실내등 끔") to Feature.AMBIENT, "공조 22°C · 풍량 ${if (p.sunrise) 2 else 1}단" to Feature.HVAC).forEach { (label, f) ->
            Text(label + if (f in s.unsupported) " · 이 차량 미지원" else "", style = T.bodyS, color = dim, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        if (s.seatMoving) Text("시트 이동 중 · 화면을 누르면 멈춥니다", style = T.bodyS, color = dim, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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
        Text(if (p.reason == EndReason.DEPARTURE) "출발 시각" else p.reason.title(p.target), style = T.labelM, color = C.restTextDim) // Restoring 라벨은 "출발 시각", Done 제목만 "출발 시각이에요"
        Spacer(Modifier.height(D.gap))
        if (p.reason == EndReason.TARGET_REACHED) Text("목표 ${p.target}%에 도달했습니다", style = T.bodyL, color = C.restText, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        if (p.reason == EndReason.DEPARTURE) Text("출발 시각입니다 · 배터리 ${s.soc ?: "--"}%", style = T.bodyL, color = C.restText, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        if (p.reason == EndReason.CHARGING_DONE) Text("충전이 끝났어요 · 배터리 ${s.soc ?: "--"}%", style = T.bodyL, color = C.restText, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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
        Text(p.reason.title(p.target), style = T.headlineL, color = C.textPrimary)
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
    val ambient by fake.ambient.collectAsState()
    Column(Modifier.fillMaxWidth().background(C.surface).padding(horizontal = D.gap)) {
        // 접힌 상태는 24dp 핸들만 (텍스트 없음)
        Box(Modifier.fillMaxWidth().height(24.dp).clickable { open = !open }, contentAlignment = Alignment.Center) {
            Box(Modifier.size(width = 64.dp, height = 6.dp).clip(RoundedCornerShape(3.dp)).background(C.textTertiary))
        }
        if (!open) return@Column
        Text("Fake 차량 패널 · 앰비언트 밝기 $ambient%", style = T.labelM, color = C.textSecondary, modifier = Modifier.padding(bottom = 8.dp))
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

/** 출발 모드 칩. DEMO 빌드에서만 "2분 후" 시연용 칩 노출 (PRD §4.7) */
private val DEPART_MINUTES = listOf(15, 30, 45) + if (BuildConfig.DEMO) listOf(2) else emptyList()

private fun EndReason.title(target: Int) = when (this) {
    EndReason.TARGET_REACHED -> "목표 $target% 도달"; EndReason.CHARGING_STOPPED -> "충전 중단"
    EndReason.DEPARTURE -> "출발 시각이에요"; EndReason.CHARGING_DONE -> "충전이 끝났어요"; else -> "휴식 모드 종료"
}

private fun Gear.label() = when (this) { Gear.PARK -> "P"; Gear.DRIVE -> "D"; Gear.REVERSE -> "R"; Gear.NEUTRAL -> "N"; Gear.UNKNOWN -> "-" }
