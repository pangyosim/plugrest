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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
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
    content: @Composable androidx.compose.foundation.layout.ColumnScope.(wide: Boolean) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize().background(bg)) {
        val wide = hero != null && maxWidth >= WIDE_MIN
        if (wide) Row(Modifier.fillMaxSize().padding(horizontal = D.screenPad * 1.5f, vertical = D.screenPad)) {
            // 왼쪽: 상태(hero) 세로 중앙. 오른쪽: 조작 묶음 + 버튼을 세로 중앙에 모아 빈 공간 제거(가로 전용 리듬)
            Column(Modifier.weight(0.44f).fillMaxHeight(), verticalArrangement = Arrangement.Center, content = hero!!)
            Spacer(Modifier.width(D.screenPad))
            Box(Modifier.width(2.dp).fillMaxHeight(0.6f).align(Alignment.CenterVertically).background(C.surface))
            Spacer(Modifier.width(D.screenPad * 1.5f))
            Column(Modifier.weight(0.56f).fillMaxHeight(), verticalArrangement = Arrangement.Center) { content(true) }
        } else Column(Modifier.fillMaxSize().padding(D.screenPad)) { hero?.invoke(this); content(false) }
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
    val now = tick() // 1초 틱(기존 헬퍼) — 출발 시각 하한·분 계산용
    val targets = TARGET_OPTIONS.filter { it > (s.soc ?: 0) } // 현재 배터리보다 높은 목표만 라디오로 노출
    var target by remember { mutableStateOf(80) }
    if (targets.isNotEmpty() && target !in targets) target = targets.first()
    var seat by remember { mutableStateOf(SeatMode.NONE) }
    var departure by remember { mutableStateOf(false) } // 세그먼트: 배터리 목표 | 출발 시각 (PRD §4.7)
    var departAt by remember { mutableStateOf(roundUp5(System.currentTimeMillis() + 30 * 60_000L)) } // 절대 시각(현재 이후만)
    val minDepart = now + MIN_DEPART_MS
    val departMin = ((departAt - now + 59_999) / 60_000).toInt().coerceAtLeast(1)
    val learned by rates.rates.collectAsState()
    val eta = s.soc?.let { rates.estimateMinutes(it, target) }?.takeIf { it > 0 }
    val departSoc = s.soc?.let { rates.predictSoc(it, departMin) } // 학습값만, 가정 금지
    val batteryBlocked = !departure && targets.isEmpty()
    val departInvalid = departure && departAt < minDepart
    val statusBadges = buildList {
        add(when {
            !s.connected -> Badge(CabinIcon.BOLT, "차량 연결 중")
            blockReason == null -> Badge(CabinIcon.BOLT, "충전 중")
            else -> Badge(CabinIcon.BOLT, "대기 중")
        })
        add(Badge(CabinIcon.GEAR, "기어 ${s.gear?.label() ?: "-"}"))
        if (BuildConfig.DEMO) add(Badge(CabinIcon.CLOCK, "시간 단축 데모"))
    }
    Screen(C.bg, hero = {
        // 가로: 가운데 정렬 — 앱명 / 배터리 링(현재 SOC) / 상태 뱃지
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("충전쉼표", style = T.titleL, color = C.textPrimary)
            Spacer(Modifier.height(D.section))
            SocRing(s.soc ?: 0, null, if (blockReason == null) C.charge else C.surface, C.textPrimary, Modifier.fillMaxWidth(0.72f))
            Spacer(Modifier.height(D.section))
            BadgeRow(statusBadges, active = C.textSecondary, muted = C.textTertiary, bg = C.surface)
        }
    }) { wide ->
        if (!wide) { // 세로: 기존처럼 숫자 + 뱃지
            Text(s.soc?.let { "$it%" } ?: "--", style = T.displayL, color = C.textPrimary)
            Spacer(Modifier.height(D.gap / 2))
            BadgeRow(statusBadges, active = C.textSecondary, muted = C.textTertiary, bg = C.surface, start = true)
            Spacer(Modifier.height(D.section))
        }
        Text("언제 깨워 드릴까요", style = T.labelM, color = C.textTertiary)
        Spacer(Modifier.height(D.gap / 2))
        Segmented(listOf("목표 배터리", "출발 시각"), if (departure) 1 else 0, { departure = it == 1 })
        Spacer(Modifier.height(D.gap))
        if (departure) {
            // iOS식 휠: 시 / 분(5분 단위) 위아래 스크롤. 아래에 빠른 선택(10·30·60분 후). 현재+5분 이전이면 시작 불가
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = departAt }
            val hour = cal.get(java.util.Calendar.HOUR_OF_DAY); val minute5 = cal.get(java.util.Calendar.MINUTE) / 5
            fun setHm(h: Int, m5: Int) {
                val c = java.util.Calendar.getInstance().apply { timeInMillis = now; set(java.util.Calendar.HOUR_OF_DAY, h); set(java.util.Calendar.MINUTE, m5 * 5); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0) }
                departAt = c.timeInMillis
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Wheel((0..23).map { "%02d".format(it) }, hour, { setHm(it, minute5) }, Modifier.width(150.dp))
                Text(":", style = T.displayL, color = C.textPrimary, modifier = Modifier.padding(horizontal = 12.dp))
                Wheel((0..11).map { "%02d".format(it * 5) }, minute5, { setHm(hour, it) }, Modifier.width(150.dp))
                Spacer(Modifier.width(D.section))
                Column { Text("출발 시각", style = T.labelM, color = C.textTertiary); Text(hhmm(departAt), style = T.titleL, color = if (departAt >= minDepart) C.textPrimary else C.warn) }
            }
            Spacer(Modifier.height(D.gap))
            Row(horizontalArrangement = Arrangement.spacedBy(D.gap)) {
                DEPART_MINUTES.forEach { m ->
                    val at = if (m == 2) now + 2 * 60_000L else roundUp5(now + m * 60_000L) // 데모 "2분 후"만 정확히
                    Chip("${m}분 후", Math.abs(departAt - at) < 150_000L, { departAt = at }, Modifier.weight(1f))
                }
            }
        } else {
            if (targets.isEmpty()) Text("배터리가 이미 목표 이상이에요 · 출발 시각 모드를 써 보세요", style = T.bodyL, color = C.textSecondary)
            else RadioRow(targets.map { "$it%" }, targets.indexOf(target), { target = targets[it] })
        }
        Spacer(Modifier.height(D.gap))
        if (departure) Text(
            (if (departSoc != null) "지난 충전 기준, 출발 시 약 $departSoc%" else "출발 시 배터리는 충전 속도를 확인한 뒤 알려 드려요") + " · ${departMin}분 뒤",
            style = T.bodyS, color = if (departSoc != null) C.textSecondary else C.textTertiary,
        ) else if (targets.isNotEmpty()) Text(if (eta != null) "지난 충전 ${learned.count}회 기준, $target%까지 보통 약 ${eta}분" else "시작 후 실제 충전 속도로 남은 시간을 계산해요", style = T.bodyS, color = C.textSecondary)
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
        if (wide) Spacer(Modifier.height(D.section)) else Spacer(Modifier.weight(1f))
        BigButton("휴식 모드 시작", { if (departure) onStart(100, seat, departMin) else onStart(target, seat, 0) }, enabled = blockReason == null && !batteryBlocked && !departInvalid)
        Spacer(Modifier.height(D.gap / 2))
        Text(blockReason ?: if (departInvalid) "현재 시각 이후로 선택해 주세요" else if (departure) "${hhmm(departAt)}에 깨워 드릴게요" else if (batteryBlocked) "출발 시각 모드로 바꿔 주세요" else "목표 $target%에 도달하면 깨워 드릴게요", style = T.bodyS, color = C.textTertiary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun RestingScreen(p: Phase.Resting, s: VehicleState, remaining: Int?, learned: Int?, departMinutes: Int?, predictSoc: (Int) -> Int?, onStop: () -> Unit, onTouch: () -> Unit) {
    // 디자인 ⑫: 선라이즈는 배경 1회 교체 + 글자 전부 restText(restTextDim 대비 부족)
    val dim = if (p.sunrise) C.restText else C.restTextDim
    Screen(if (p.sunrise) C.bgSunrise else C.bgRest, Modifier.pointerInput(Unit) { detectTapGestures { onTouch() } }, hero = {
        BadgeRow(buildList {
            add(Badge(CabinIcon.MOON, "휴식 중"))
            add(Badge(CabinIcon.TARGET, if (departMinutes != null) "출발 시각 모드" else "목표 ${p.target}%"))
            if (BuildConfig.DEMO) add(Badge(CabinIcon.CLOCK, "시간 단축 데모"))
        }, active = dim, muted = dim, bg = if (p.sunrise) C.surfaceRest.copy(alpha = 0.6f) else C.surfaceRest, start = true)
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
        } else {
            SocRing(s.soc ?: 0, p.target, C.restCharge, C.restText, Modifier.weight(1f, fill = false))
            Spacer(Modifier.weight(0.01f))
        }
        Spacer(Modifier.height(D.gap))
    }) { wide ->
        if (departMinutes != null) { // 출발 모드: 오른쪽 주인공 = 출발 시 예상 배터리
            val predicted = predictSoc(departMinutes)
            Text("출발 시 배터리", style = T.labelM, color = dim, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Box(Modifier.fillMaxWidth().height(if (wide) 180.dp else 120.dp), contentAlignment = Alignment.Center) {
                if (predicted != null) Text("약 $predicted%", style = if (wide) T.displayXL else T.displayL, color = C.restText, maxLines = 1, softWrap = false)
                else CalculatingText("계산 중", style = if (wide) T.displayL else T.titleL, color = dim)
            }
            Spacer(Modifier.height(if (wide) D.section else D.gap))
        }
        if (departMinutes == null) { // 배터리 모드: 남은 시간 블록 (가로에선 오른쪽 열의 주인공)
            // 디자인 ⑧⑨: remaining은 실측 기울기가 있을 때만 값(고정 가정 없음). 계산 중엔 학습값 보조 줄, 실측이 생기면 보조 줄 대신 "실측" 태그 (학습값을 보여준 세션만)
            val learnedEta = learned?.takeIf { it > 0 }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("남은 시간", style = T.labelM, color = dim)
                if (remaining != null && learnedEta != null) Text("실측", style = T.labelS, color = dim,
                    modifier = Modifier.padding(start = 16.dp).border(2.dp, dim, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 4.dp))
            }
            Box(Modifier.fillMaxWidth().height(if (wide) 180.dp else 120.dp), contentAlignment = Alignment.Center) {
                if (remaining == null) CalculatingText("계산 중", style = if (wide) T.displayL else T.titleL, color = dim)
                else Text("약 ${remaining}분", style = if (wide) T.displayXL else T.displayL, color = C.restText, maxLines = 1, softWrap = false)
            }
            if (remaining == null && learnedEta != null) Text("지난 충전 기준 약 ${learnedEta}분", style = T.bodyS, color = dim, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(if (wide) D.section else D.gap))
        }
        if (!wide) Spacer(Modifier.weight(1f))
        // 디자인 ⑫ M1: 선라이즈 문구는 하단 상태줄 첫 줄(헤더의 데모 고지는 유지)
        if (p.sunrise) Text(if (Feature.AMBIENT in s.unsupported) "곧 깨워 드릴게요" else "곧 깨워 드릴게요 · 조명을 천천히 밝히는 중", style = T.bodyS.copy(fontWeight = FontWeight.Bold), color = C.restText, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        if (p.halfRecline) Text(if (s.rearSeatOccupied == true) "뒷좌석에 사람이 있어 등받이를 절반만 젖혔어요" else "뒷좌석 확인이 안 돼 등받이를 절반만 젖혔어요", style = T.bodyS, color = dim, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        // 상태는 아이콘+뱃지(사용자 요청). 미지원은 흐리게 + "미지원"
        // 1줄: 실내등·온도·풍량 / 2줄: 등받이·(통풍)·앰비언트 (사용자 지정 순서)
        val row1 = listOf(
            Badge(CabinIcon.LAMP, "실내등 끔", Feature.AMBIENT in s.unsupported),
            Badge(CabinIcon.TEMP, "22°C", Feature.HVAC in s.unsupported),
            Badge(CabinIcon.FAN, "풍량 ${if (p.sunrise) 2 else 1}단", Feature.HVAC in s.unsupported),
        )
        val row2 = buildList {
            add(Badge(CabinIcon.SEAT, if (p.halfRecline) "등받이 절반" else "등받이 젖힘", Feature.SEAT_RECLINE in s.unsupported))
            if (p.seatMode == SeatMode.VENT) add(Badge(CabinIcon.VENT, "통풍 시트", Feature.SEAT_VENT in s.unsupported))
            add(Badge(CabinIcon.AMBIENT, if (p.sunrise) "조명 밝히는 중" else "앰비언트 어둡게", Feature.AMBIENT in s.unsupported))
        }
        BadgeRow(row1, active = C.restText, muted = dim, bg = C.surfaceRest, large = wide)
        Spacer(Modifier.height(12.dp))
        BadgeRow(row2, active = C.restText, muted = dim, bg = C.surfaceRest, large = wide)
        if (s.seatMoving) Text("시트 이동 중 · 화면을 누르면 멈춥니다", style = T.bodyS, color = dim, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(if (wide) D.section * 1.5f else D.section))
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
    val warnings = buildList {
        if (s.rearSeatOccupied == true) add("뒷좌석에 사람이 있어요")
        s.openWindows.forEach { add("$it 창문이 열려 있어요") }
        if (s.cabinLightOn) add("실내등이 켜져 있어요")
    }
    val resultBadges = if (p.restored) listOf(
        Badge(CabinIcon.SEAT, "등받이 원위치"), Badge(CabinIcon.AMBIENT, "조명 복원"), Badge(CabinIcon.TEMP, "공조 복원"),
    ) else listOf(Badge(CabinIcon.SEAT, "시트 그대로"), Badge(CabinIcon.AMBIENT, "조명 그대로"), Badge(CabinIcon.TEMP, "공조 그대로"))
    Screen(C.bg, hero = {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            BadgeRow(listOf(Badge(CabinIcon.MOON, "휴식 모드 종료")) + (if (BuildConfig.DEMO) listOf(Badge(CabinIcon.CLOCK, "시간 단축 데모")) else emptyList()), active = C.textSecondary, muted = C.textTertiary, bg = C.surface)
            Spacer(Modifier.height(D.section))
            Text(p.reason.title(p.target), style = T.headlineL, color = C.textPrimary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(D.gap))
            Text(if (p.restored) "시트·조명·공조를 원래대로 되돌렸어요" else "시트·조명·공조는 지금 상태 그대로예요", style = T.bodyL, color = C.textSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(D.section))
            BadgeRow(resultBadges, active = C.textPrimary, muted = C.textTertiary, bg = C.surface)
        }
    }) { wide ->
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(D.cardRadius)).background(C.surface).padding(D.cardPad), verticalArrangement = Arrangement.spacedBy(D.gap)) {
            Text("내리기 전 확인", style = T.titleS, color = C.textPrimary)
            if (warnings.isEmpty()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(D.gap / 2)) {
                Icon(Icons.Filled.Check, contentDescription = "이상 없음", tint = C.charge, modifier = Modifier.size(40.dp))
                Text("확인할 것 없음", style = T.bodyL, color = C.textSecondary)
            } else warnings.forEach {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(D.gap / 2)) {
                    Icon(Icons.Filled.Warning, contentDescription = "주의", tint = C.warn, modifier = Modifier.size(40.dp))
                    Text(it, style = T.bodyL, color = C.textPrimary)
                }
            }
        }
        if (wide) Spacer(Modifier.height(D.section)) else Spacer(Modifier.weight(1f))
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

/** 출발 모드 칩. DEMO 빌드에서만 "2분 후" 시연용 칩 노출 (PRD §4.7) */
private val DEPART_MINUTES = listOf(10, 30, 60) + if (BuildConfig.DEMO) listOf(2) else emptyList()
private val TARGET_OPTIONS = listOf(60, 70, 80, 90)
private val MIN_DEPART_MS = if (BuildConfig.DEMO) 60_000L else 5 * 60_000L
private fun roundUp5(ms: Long): Long { val step = 5 * 60_000L; return (ms + step - 1) / step * step } // 휠(5분 단위)과 일치
private fun hhmm(ms: Long): String = java.text.SimpleDateFormat("HH:mm", java.util.Locale.KOREA).format(java.util.Date(ms))

private fun EndReason.title(target: Int) = when (this) {
    EndReason.TARGET_REACHED -> "목표 $target% 도달"; EndReason.CHARGING_STOPPED -> "충전 중단"
    EndReason.DEPARTURE -> "출발 시각이에요"; EndReason.CHARGING_DONE -> "충전이 끝났어요"; else -> "휴식 모드 종료"
}

private fun Gear.label() = when (this) { Gear.PARK -> "P"; Gear.DRIVE -> "D"; Gear.REVERSE -> "R"; Gear.NEUTRAL -> "N"; Gear.UNKNOWN -> "-" }


// ---- 상태 뱃지 (아이콘은 의존성 없이 Canvas로 그림) ----
enum class CabinIcon { SEAT, VENT, AMBIENT, LAMP, TEMP, FAN, MOON, TARGET, CLOCK, BOLT, GEAR }
data class Badge(val icon: CabinIcon, val label: String, val unsupported: Boolean = false)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun BadgeRow(badges: List<Badge>, active: Color, muted: Color, bg: Color, large: Boolean = false, start: Boolean = false) {
    androidx.compose.foundation.layout.FlowRow(
        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, if (start) Alignment.Start else Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        badges.forEach { b ->
            val tint = if (b.unsupported) muted.copy(alpha = 0.5f) else active
            Row(
                Modifier.clip(RoundedCornerShape(if (large) 20.dp else 16.dp)).background(bg).padding(horizontal = if (large) 22.dp else 16.dp, vertical = if (large) 14.dp else 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CabinGlyph(b.icon, tint, Modifier.size(if (large) 36.dp else 28.dp))
                Text(b.label + if (b.unsupported) " · 미지원" else "", style = if (large) T.bodyL else T.bodyS, color = tint, maxLines = 1)
            }
        }
    }
}

@Composable
fun CabinGlyph(icon: CabinIcon, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height; val sw = w * 0.1f
        val st = Stroke(width = sw, cap = StrokeCap.Round)
        when (icon) {
            CabinIcon.SEAT -> { // 등받이(기울임) + 방석
                drawLine(tint, Offset(w * 0.32f, h * 0.12f), Offset(w * 0.22f, h * 0.62f), sw, StrokeCap.Round)
                drawRoundRect(tint, Offset(w * 0.18f, h * 0.58f), Size(w * 0.56f, h * 0.2f), androidx.compose.ui.geometry.CornerRadius(sw))
                drawLine(tint, Offset(w * 0.28f, h * 0.78f), Offset(w * 0.28f, h * 0.92f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.66f, h * 0.78f), Offset(w * 0.66f, h * 0.92f), sw, StrokeCap.Round)
            }
            CabinIcon.VENT -> { // 바람 3줄
                for ((i, f) in listOf(0.3f, 0.5f, 0.7f).withIndex()) {
                    val len = if (i == 1) 0.7f else 0.5f
                    drawLine(tint, Offset(w * 0.15f, h * f), Offset(w * (0.15f + len), h * f), sw, StrokeCap.Round)
                }
            }
            CabinIcon.AMBIENT -> { // 해(원 + 광선)
                drawCircle(tint, radius = w * 0.18f, center = Offset(w / 2, h / 2), style = st)
                for (k in 0 until 8) {
                    val a = Math.toRadians(k * 45.0); val c = Math.cos(a).toFloat(); val sn = Math.sin(a).toFloat()
                    drawLine(tint, Offset(w / 2 + c * w * 0.3f, h / 2 + sn * h * 0.3f), Offset(w / 2 + c * w * 0.42f, h / 2 + sn * h * 0.42f), sw, StrokeCap.Round)
                }
            }
            CabinIcon.LAMP -> { // 전구
                drawCircle(tint, radius = w * 0.24f, center = Offset(w / 2, h * 0.4f), style = st)
                drawLine(tint, Offset(w * 0.4f, h * 0.72f), Offset(w * 0.6f, h * 0.72f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.43f, h * 0.86f), Offset(w * 0.57f, h * 0.86f), sw, StrokeCap.Round)
            }
            CabinIcon.TEMP -> { // 온도계
                drawRoundRect(tint, Offset(w * 0.4f, h * 0.08f), Size(w * 0.2f, h * 0.55f), androidx.compose.ui.geometry.CornerRadius(w * 0.1f), style = st)
                drawCircle(tint, radius = w * 0.17f, center = Offset(w / 2, h * 0.75f))
            }
            CabinIcon.MOON -> { // 초승달: 큰 원에서 작은 원을 배경색으로 덮는 대신 두 호
                drawCircle(tint, radius = w * 0.34f, center = Offset(w * 0.5f, h * 0.5f), style = st)
                drawArc(tint, startAngle = -60f, sweepAngle = 300f, useCenter = false, topLeft = Offset(w * 0.3f, h * 0.22f), size = Size(w * 0.56f, h * 0.56f), style = Stroke(sw * 0.6f, cap = StrokeCap.Round))
            }
            CabinIcon.TARGET -> { // 과녁
                drawCircle(tint, radius = w * 0.38f, center = Offset(w / 2, h / 2), style = st)
                drawCircle(tint, radius = w * 0.2f, center = Offset(w / 2, h / 2), style = st)
                drawCircle(tint, radius = w * 0.06f, center = Offset(w / 2, h / 2))
            }
            CabinIcon.CLOCK -> { // 시계
                drawCircle(tint, radius = w * 0.38f, center = Offset(w / 2, h / 2), style = st)
                drawLine(tint, Offset(w / 2, h / 2), Offset(w / 2, h * 0.26f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w / 2, h / 2), Offset(w * 0.68f, h * 0.6f), sw, StrokeCap.Round)
            }
            CabinIcon.BOLT -> { // 번개
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.58f, h * 0.06f); lineTo(w * 0.26f, h * 0.56f); lineTo(w * 0.5f, h * 0.56f)
                    lineTo(w * 0.42f, h * 0.94f); lineTo(w * 0.76f, h * 0.42f); lineTo(w * 0.52f, h * 0.42f); close()
                }
                drawPath(path, tint)
            }
            CabinIcon.GEAR -> { // 기어 노브: 원 + 스틱
                drawCircle(tint, radius = w * 0.2f, center = Offset(w / 2, h * 0.3f), style = st)
                drawLine(tint, Offset(w / 2, h * 0.5f), Offset(w / 2, h * 0.9f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.3f, h * 0.9f), Offset(w * 0.7f, h * 0.9f), sw, StrokeCap.Round)
            }
            CabinIcon.FAN -> { // 팬 날개 3개
                drawCircle(tint, radius = w * 0.08f, center = Offset(w / 2, h / 2))
                for (k in 0 until 3) {
                    val a = Math.toRadians(k * 120.0 - 90); val c = Math.cos(a).toFloat(); val sn = Math.sin(a).toFloat()
                    drawCircle(tint, radius = w * 0.15f, center = Offset(w / 2 + c * w * 0.25f, h / 2 + sn * h * 0.25f), style = st)
                }
            }
        }
    }
}


/** "계산 중" 뒤 점 1→2→3 순환(400ms). 정차 전용 화면이라 허용되는 최소 모션. 글자 폭 고정을 위해 점 3개 자리를 항상 확보. */
@Composable
fun CalculatingText(prefix: String, style: androidx.compose.ui.text.TextStyle, color: Color) {
    var n by remember { mutableStateOf(1) }
    androidx.compose.runtime.LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(400); n = n % 3 + 1 } }
    Row(verticalAlignment = Alignment.Bottom) {
        Text(prefix, style = style, color = color, maxLines = 1)
        Box { // 보이지 않는 "…"로 폭 고정 + 실제 점
            Text("…", style = style, color = Color.Transparent)
            Text(".".repeat(n), style = style, color = color)
        }
    }
}


// ---- 라디오 행 (목표 배터리) ----
@Composable
fun RadioRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(D.gap)) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Row(
                Modifier.weight(1f).height(D.buttonM).clip(RoundedCornerShape(D.radiusM)).background(C.surface)
                    .border(if (on) 3.dp else 0.dp, if (on) C.primary else Color.Transparent, RoundedCornerShape(D.radiusM))
                    .clickable { onSelect(i) }.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(28.dp).border(3.dp, if (on) C.primary else C.textTertiary, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                    if (on) Box(Modifier.size(14.dp).background(C.primary, androidx.compose.foundation.shape.CircleShape))
                }
                Text(label, style = T.titleS, color = C.textPrimary, maxLines = 1)
            }
        }
    }
}

// ---- 휠 피커 (iOS식, 3줄 보임, 가운데 스냅) ----
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun Wheel(items: List<String>, index: Int, onIndex: (Int) -> Unit, modifier: Modifier = Modifier) {
    val itemH = 72.dp
    val state = androidx.compose.foundation.lazy.rememberLazyListState(index)
    val fling = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(state)
    val centered by remember { derivedStateOf {
        val info = state.layoutInfo; val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
        info.visibleItemsInfo.minByOrNull { Math.abs(it.offset + it.size / 2 - mid) }?.index ?: index
    } }
    LaunchedEffect(state.isScrollInProgress) { if (!state.isScrollInProgress && centered != index) onIndex(centered) }
    LaunchedEffect(index) { if (!state.isScrollInProgress && centered != index) state.scrollToItem(index) }
    Box(modifier.height(itemH * 3), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(itemH).clip(RoundedCornerShape(16.dp)).background(C.surface)) // 가운데 선택 띠
        androidx.compose.foundation.lazy.LazyColumn(state = state, flingBehavior = fling, contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = itemH), modifier = Modifier.fillMaxWidth()) {
            items(items.size) { i ->
                val on = i == centered
                Box(Modifier.fillMaxWidth().height(itemH), contentAlignment = Alignment.Center) {
                    Text(items[i], style = if (on) T.displayL.copy(fontSize = 48.sp, lineHeight = 52.sp) else T.titleL, color = if (on) C.textPrimary else C.textTertiary, maxLines = 1)
                }
            }
        }
    }
}
