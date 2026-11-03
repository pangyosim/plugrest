# 화면 리뷰 — 실제 빌드 vs mockup.html  (2026-11-03)

대상: `docs/design/screenshots/01~09.png` (Fake 모드, 1600×2560 = 800×1280dp @2x, 데모 라벨 ON). 하단 "▲ Fake 차량 패널" 줄은 대조에서 제외.
방법: 목업을 1:1(1px = 1dp)로 렌더링해 스크린샷을 800dp 폭으로 줄여 나란히 비교했고, 원인 확인을 위해 `ui/Theme.kt`, `ui/Screens.kt`를 읽었다(코드는 수정하지 않음).

**맞게 적용된 것**: 색 토큰 전부(배경 #1D1E21 / Resting 검정 / 카드 #313236 / 버튼 #0064FF / 링 #2B8F67 / 트랙), 좌우 여백 48dp, 버튼 L 100dp·모서리 32dp, 칩 80dp·모서리 24dp, 링 두께 28dp와 목표 눈금, "계산 중…" 자리 높이 120dp, 확정 문구(Restoring·TTS·Done 그대로 둠), "충전 완료" 표현 없음.

## 4차 대조 (v0.6 충전 속도 학습, 07~09) — 결과: 수정 필수 0건, 권장 1건 → **승인**
- ⑦ Idle·학습됨 (07): 보조 문구 bodyS·textSecondary "지난 충전 1회 기준, 80%까지 보통 약 20분" ✅, 바로 아래 밑줄 텍스트 버튼 ✅, 주 버튼 1개 ✅, 시트 통풍 위 간격 24 ✅
- ⑧ Resting·계산 중+학습값 (08): "계산 중…" 아래 "지난 충전 기준 약 20분" bodyS·restTextDim ✅
- ⑨ Resting·실측 (09): 태그를 숫자가 아니라 "남은 시간" 라벨 옆에 둠 → 숫자 가운데 정렬이 유지돼 목업보다 나음. **목업·tokens.md를 앱 쪽으로 맞춤**. 태그 테두리·r8·글자색 ✅
- **R1 (권장)** "학습 기록 지우기" 글자가 목업보다 약 1.2배 큼(같은 화면의 bodyS 문장 폭 대비 측정 → 약 24sp). labelS(20sp)가 실제로 적용됐는지 확인. 24sp로 의도한 것이면 알려 주면 토큰 쪽을 바꿈.
- 참고 (수정 불필요): Resting은 내용이 세로 가운데 정렬이라 ⑧→⑨ 전환 때 보조 줄 1줄만큼(약 28dp) 하단 상태줄·종료 버튼이 한 번 위로 움직임. 세션당 1회·애니메이션 없음이라 허용.
- 07의 화면 상단·하단 구성(앱 이름, "시간 단축 데모", 하단 안내 문구)은 3차 승인본 01과 같음.

## 3차 대조 — 결과: 수정 필수 0건, 권장 0건 → **목업 대비 승인**
- ① Idle: 예상 시간 줄 → "시작 후 실제 충전 속도로 남은 시간을 계산해요" ✅ (PM 결정 반영). 시트 통풍 끔/켬 ✅
- ② Resting·계산 중: 링 520dp, "계산 중…", 상단 "휴식 중 · 목표 80%" ✅. 이번 촬영본에는 착좌 줄이 없고 "등받이 리클라인"이 나옴 → 착좌 없는 상태로 찍힌 것으로 보이며, 이 경우 표시 규칙상 맞음.
- ③ Resting·착좌: SOC 61% · 약 20분, 착좌 문구 + 2줄(등받이 줄 생략) = 3줄 ✅ (N1 해결)
- ④ Restoring: "8초 후 시트를 원래대로" + 숫자 8 + "초" ✅
- ⑤ Done·복원됨: 카드 제목 흰색 titleS, 경고 3줄 아이콘 + 흰 글자 ✅ (N2 해결)
- ⑥ Done·그대로 둠: "휴식 모드 종료"(목표 미도달 경로) + "확인할 것 없음" ✅
- Fake 패널: 24dp 핸들만 남음 → 녹화 화면에 거의 보이지 않음 ✅
- N3 "현재 배터리" 라벨: 선택 사항이라 미반영 그대로 허용.
- 남은 확인: 실제 Pleos 에뮬레이터 해상도에서 링 크기와 1.5m 가독성 1회 확인(일반 태블릿 AVD로만 검증함).

---

## 2차 대조 (재촬영본) — 결과: 수정 필수 0건, 권장 3건
| # | 1차 지적 | 2차 상태 |
|---|---|---|
| R1 | 글꼴 굵기 | ✅ 해결. 제목·버튼·숫자 Bold/ExtraBold 정상 |
| R2 | 링 크기 | ✅ 해결. 지름 520dp, "50%" 한 줄, 1.5m 기준 160sp 확보 |
| R3 | 카운트다운 문구 | ✅ 해결. "9초 후 시트를 원래대로" + 숫자 9 일치 |
| R4 | Done 제목 | ✅ 해결. 종료 사유 분기("목표 80% 도달" / "휴식 모드 종료") |
| R5 | 하차 체크 | ✅ 해결. 경고 아이콘 + 흰 글자, "내리기 전 확인". 세부 권장 N2 |
| R6 | Resting 정렬 | ✅ 가운데 정렬·"끔" 반영. 세부 권장 N1 |
| R7 | Idle | ✅ 통풍 끔/켬 2칩(기본 끔). 제목 "충전쉼표" 유지는 허용. **PM 후속 결정**: "80%까지 약 30분"은 "계산 중" 원칙과 모순이라 삭제 → 보조 문구 "시작 후 실제 충전 속도로 남은 시간을 계산해요"(bodyS 26/34sp, #8A8B90, 목표 칩 아래 24dp) |
| R8 | Restoring 세부 | ✅ 해결. "초" 단위, 문구 가운데 정렬 |

남은 권장 (데모에 지장 없음, 여유 있을 때):
- **N1 (③ Resting, 착좌 시)**: 상태줄이 4줄이고, "뒷좌석에 사람이 있어 등받이를 절반만 젖혔어요" 바로 아래 "등받이 리클라인"이 또 나와 중복. 착좌일 때는 "등받이 리클라인" 줄을 빼서 3줄로: ① 착좌 문구 ② "앰비언트 어둡게 · 실내등 끔" ③ "공조 22°C · 풍량 1단". 상단 라벨은 "목표 80%" → "휴식 중 · 목표 80%"(labelM, #7A7B80).
- **N2 (⑤⑥ Done)**: 카드 제목 "내리기 전 확인" 색이 회색으로 보임 → `textPrimary #FFFFFF`(titleS 32/40sp Bold). 경고 줄 간격이 약 12dp로 좁음 → `Arrangement.spacedBy(24.dp)`(목업은 아이콘 40dp + 간격 24dp).
- **N3 (① Idle)**: SOC 위 "현재 배터리" 라벨(labelM, #B4B5B8)이 없음. 지금처럼 SOC 아래 상태줄만 있어도 의미는 통하므로 선택 사항.

---

## 1차 대조

## 요약
| # | 화면 | 내용 | 심각도 |
|---|---|---|---|
| R1 | 전체 | 글꼴 굵기가 전부 Regular로 나옴 (Bold·ExtraBold 미적용) | **수정 필수** |
| R2 | ②③ Resting | SOC 링 지름이 약 270dp (기준 520dp). ②에서는 "50%"가 "50 / %"로 줄바꿈되어 깨짐 | **수정 필수** |
| R3 | ④ Restoring | 숫자는 9인데 문구는 "10초 후"로 고정 → 서로 안 맞음 | **수정 필수** |
| R4 | ⑥ Done | 제목을 종료 사유로 분기 (**PM 채택**) | **수정 필수** |
| R5 | ⑤⑥ Done | 경고가 아이콘 없이 앰버 글자로만 표시됨. 카드 제목이 "하차 체크" | 권장 |
| R6 | ②③ Resting | 남은 시간·적용 목록이 왼쪽 정렬, 적용 목록 4줄, "OFF" 영문 | 권장 |
| R7 | ① Idle | 시트 칩 → "시트 통풍 끔/켬"(기본 끔)으로 (**PM 결정**), "80%까지 약 30분" 빠짐, 화면 제목 "충전쉼표" 추가 | 시트 칩 수정 필수 · 나머지 권장 |
| R8 | ④ Restoring | 숫자 아래 "초" 단위 없음, 문구 왼쪽 정렬 | 권장 |

---

## R1. 글꼴 굵기 — 수정 필수 (전체)
- 현상: 제목("목표 80% 도달", "휴식 모드 종료"), 버튼 라벨, 칩, SOC 숫자, 카운트다운이 모두 가는 Regular로 나옴. 1.5m 거리 가독성과 화면 위계가 무너짐.
- 원인: `res/font/asta_sans.ttf`가 **가변 폰트**(fvar 테이블 있음). `Theme.kt:33-35`에서 같은 파일을 `Font(R.font.asta_sans, FontWeight.Bold)`로 3번 등록했는데, `variationSettings`가 없으면 가변 폰트의 기본 인스턴스(Regular)만 그려짐.
- 수정안 (Compose UI 1.5+, minSdk 28이라 그대로 가능):
```kotlin
@OptIn(ExperimentalTextApi::class)
val AstaSans = FontFamily(
    Font(R.font.asta_sans, FontWeight.Normal,    variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.asta_sans, FontWeight.Bold,      variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.asta_sans, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
)
```
- 확인: displayXL·headlineL은 ExtraBold(800), displayL·titleL·titleS·버튼은 Bold(700), body·label은 Normal(400).

## R2. SOC 링 크기 — 수정 필수 (② ③)
- 현상: 링 지름 약 270dp(목업 520dp). 링 안 SOC 숫자가 약 84sp로 줄어 1.5m 기준(displayXL 160sp)에 미달. ②에서는 "50%"가 두 줄로 깨짐.
- 원인: `Screens.kt` `SocRing`이 `Modifier.weight(1f)` 안에서 `maxHeight * 0.45`를 계산. weight 영역은 링 아래 요소를 빼고 남은 높이라 0.45를 곱하면 다시 작아짐. 그리고 글자 크기가 `d * 0.31`로 같이 줄어듦.
- 수정안:
  - 지름은 **화면 전체** 기준으로: `val cfg = LocalConfiguration.current; val d = min(cfg.screenWidthDp * 0.65f, cfg.screenHeightDp * 0.45f).coerceAtMost(520f).dp` → 800×1280dp에서 520dp.
  - `SocRing`에서 `Modifier.weight(1f)`를 빼고, 링 아래에 `Spacer(Modifier.weight(1f))`를 둬서 남는 공간을 흡수.
  - 링 안 텍스트에 `maxLines = 1, softWrap = false`를 넣어 줄바꿈 방지.
  - 높이 검산(800×1280dp): 여백 96 + 상단 라벨 28 + 24 + 링 520 + 48 + "남은 시간" 28 + 120 + 상태줄 최대 3줄 102 + 24 + 버튼 100 = 1090dp → 상태바(약 48)와 Fake 패널(약 52)을 빼도 들어감.

## R3. Restoring 문구와 숫자 불일치 — 수정 필수 (④)
- 현상: 위 문구 "10초 후 시트를 원래대로", 가운데 숫자는 9. 사용자 입장에서 모순.
- 수정안: 두 번째 줄을 남은 초에 맞춰 바꿈 → `Text("${n}초 후 시트를 원래대로", style = T.bodyL, color = C.restText)`. 처음(n = 10)엔 PM이 확정한 문구와 똑같음. TTS는 확정 문구 그대로 1회만 재생.

## R4. Done(그대로 둠) 제목 — 수정 필수 (⑥) · PM 채택: 종료 사유 분기
- 현상: 제목 "휴식 모드 종료". 목업과 PM 결정은 "목표 80% 도달".
- 판단: Done(그대로 둠)에는 **목표에 도달하지 않은 경로**(Resting에서 "종료", 충전 커넥터 분리, 기어 변경)도 들어올 수 있음. 이 경우 "목표 80% 도달"은 사실이 아니어서 개발 쪽 선택에도 이유가 있음.
- 수정안 (제목을 종료 사유로 결정):
  - 목표 도달 경로(Restoring 완료 또는 취소) → `"목표 ${target}% 도달"`
  - 그 외(사용자 종료 / 충전 중단 / 기어 변경) → `"휴식 모드 종료"`
  - 본문은 지금처럼 복원 여부에 따라 "원래대로 되돌렸어요" / "지금 상태 그대로예요".
  - 스타일: headlineL 60/80sp, **ExtraBold**(R1), `textPrimary #FFFFFF`.

## R5. Done 하차 체크 카드 — 권장 (⑤ ⑥)
- 경고를 색으로만 구분하지 말 것(가이드: 아이콘에는 텍스트 라벨을 함께. 색만으로 의미 전달 금지). 각 줄 = 경고 아이콘 + 흰 글자:
  `Row(verticalAlignment = CenterVertically, horizontalArrangement = spacedBy(20.dp)) { Icon(Icons.Filled.Warning, null, tint = Color(0xFFFFC75A), modifier = Modifier.size(40.dp)); Text(msg, style = T.bodyL, color = Color.White) }`
- 카드 제목 "하차 체크"(내부 용어, labelM 회색) → **"내리기 전 확인"**, `T.titleS`(32/40sp Bold), `textPrimary`. 줄 간격 24dp, 카드 안쪽 여백 32dp, 모서리 24dp(이미 맞음).
- 경고 순서: 뒷좌석 → 창문 → 실내등 (tokens.md).
- 경고가 없을 때 "확인할 것 없음"은 지금 형태 그대로 OK(bodyL, textSecondary).

## R6. Resting 정렬과 상태줄 — 권장 (② ③)
- 남은 시간 블록: 목업은 링 아래 **가운데 정렬**. `Column(horizontalAlignment = CenterHorizontally)` + `Box(..., contentAlignment = Alignment.Center)` (지금은 `CenterStart`).
- 적용 목록: 지금은 4줄 왼쪽 정렬("등받이 리클라인 / 앰비언트 라이트 어둡게 / 실내등 OFF / 공조 22°C · 풍량 1단"). 저휘도 화면에 글이 많음 → 가운데 정렬, 최대 3줄로:
  - 1줄(착좌 시만): "뒷좌석에 사람이 있어 등받이를 절반만 젖혔어요"
  - 2줄: "등받이 · 조명 어둡게 · 실내등 끔 · 공조 22°C" (적용된 것만, ` · `로 연결)
  - 3줄(미지원이 있을 때만): "앰비언트 조명 · 이 차량 미지원"
  - 스타일: `T.bodyS`(26/34sp), `C.restTextDim #7A7B80`, `TextAlign.Center`. 영문 "OFF" → "끔".
- 상단 라벨: "목표 80%" → "휴식 중 · 목표 80%"(데모 태그는 뒤에 그대로).

## R7. Idle (①) — PM 결정: 시트 칩은 "시트 통풍 끔/켬" 2칩(기본 끔)으로 축소, mockup.html 반영됨
- **"시트: 없음 / 통풍" 칩 줄 추가**: 목업·PRD에 없는 선택 단계. 원탭 시작 원칙과 화면 단순성에 어긋남 → PM 결정 필요. 유지한다면 라벨을 "시트 통풍"으로 하고 **끔/켬** 2칩, 기본값 끔, 목표 칩과 같은 스타일(80dp, 단일 선택).
- ~~**"80%까지 약 30분" 빠짐**~~ (PM 후속 결정으로 철회, 2차 대조 R7 참고): 시작 전에 걸리는 시간을 보여주는 것이 시작 결정에 가장 중요한 정보. 목표 칩 아래 `T.bodyL`, `textSecondary #B4B5B8`, 칩과 간격 24dp. 데이터가 부족하면 이 줄을 숨김.
- **화면 제목 "충전쉼표" 추가**: 목업에는 없음. 앱 이름은 시스템이 보여주므로 빼서 SOC를 위로 올리는 것을 권장. 남긴다면 `T.labelM`, `textTertiary`.
- 상태 표시: 목업은 SOC 위에 "P · 충전 중" 칩(번개 아이콘 #43E0A0). 지금의 SOC 아래 텍스트 한 줄도 허용. 다만 "현재 배터리" 라벨(labelM, textSecondary)을 SOC 위에 추가 권장.
- 버튼 아래 안내 "목표 80%에 도달하면 깨워 드릴게요": OK(bodyS, textTertiary, bg 위라 대비 4.9).

## R8. Restoring 세부 — 권장 (④)
- 카운트다운 아래 단위 "초": `T.labelM`, `C.restTextDim`, 숫자와 간격 0.
- 위 문구 2줄: 목업은 가운데 정렬 → `textAlign = TextAlign.Center, Modifier.fillMaxWidth()`.
- 카운트다운 숫자: displayXL 160sp **ExtraBold**(R1), `restText #B4B5B8`, 고정폭 숫자(tnum)는 이미 적용됨.

## 다음 확인
- 실제 Pleos 에뮬레이터(차량 화면 해상도·밀도)에서 R2 링 크기와 1.5m 가독성을 다시 확인.
- 뒷좌석 착좌 상태줄(③)과 경고 3줄(⑤)은 이번 스크린샷 시나리오에 없어서 확인하지 못함 → 수정 후 해당 상태로 1장씩 추가 요청.
