# 디자인 토큰 — 충전쉼표 (Plug & Rest)  v0.1 / 2026-11-03

목업: `mockup.html` (1px = 1dp). 남은 시간은 5분 단위 반올림 + "약" (PRD v0.3). 근거: Pleos Connect 디자인 가이드 원문(아래 출처 칸 **G**), PRD §4, research/02 "기획 제약".
**G** = 가이드 원문에서 직접 확인한 값. **앱** = 이 앱이 따로 정한 값 (이유 적음).

## 원칙 (가이드 요약)
- 1~2초 안에 읽혀야 함(Glanceability). 응답 0.25초 이내, 그보다 늦으면 busy 표시. **G**
- 텍스트·아이콘 대비 4.5:1 이상. 주요 텍스트 높이 5mm 이상, 보조 텍스트 3mm 이상, 최소 12sp. **G**
- 애니메이션·자동 스크롤·장식 금지. 화면당 버튼 1~2개. 아이콘은 반드시 텍스트 라벨과 함께. **G/PM**
- 터치하면 햅틱이나 소리로 피드백. **G** → Compose `LocalHapticFeedback`

## 색 (다크 고정)
| 토큰 | Hex | 용도 | 대비 | 출처 |
|---|---|---|---|---|
| `bg` | `#1D1E21` | Idle·Done 배경 | — | G background_secondary(dark) |
| `bgRest` | `#000000` | Resting 배경 (저휘도) | — | 앱: 밤·지하 충전소 눈부심 방지 |
| `surface` | `#313236` | 카드·칩·보조 버튼 | — | G background_primary(dark) |
| `surfaceRest` | `#141518` | Resting 종료 버튼 | — | 앱 |
| `textPrimary` | `#FFFFFF` | 본문·숫자 | 16.7 on bg | G static_light_100 |
| `textSecondary` | `#B4B5B8` | 라벨·보조 | 8.1 on bg / 7.3 on surface | 앱 (G 가이드 PNG에서 근사) |
| `textTertiary` | `#8A8B90` | 캡션 (**bg 위에서만**) | 4.9 on bg (surface 위는 3.8이라 사용 금지) | 앱 |
| `primary` | `#0064FF` | 주 버튼 배경, 선택 칩 테두리 | 흰 글자 4.9 | G |
| `onPrimary` | `#FFFFFF` | 주 버튼 글자 | — | G |
| `charge` | `#43E0A0` | SOC 링, 충전 중 표시 | 9.9 on bg | 앱 (아이콘 민트) |
| `warn` | `#FFC75A` | 하차 체크 경고 | 8.3 on surface | 앱 (아이콘 앰버) |
| `restText` | `#B4B5B8` | Resting 숫자 | 10.2 on 검정 | 앱: 흰색보다 휘도 낮게 |
| `restTextDim` | `#7A7B80` | Resting 라벨 | 5.0 on 검정 | 앱 |
| `restCharge` | `#2B8F67` | Resting SOC 링 | 5.2 on 검정 | 앱: 민트를 어둡게 |
| `track` | `#26272B` | 링 바탕 트랙 | — | 앱 |
| `disabledBg` / `disabledText` | `#2A2B2F` / `#6B6C70` | 비활성 버튼 | (비활성은 대비 기준 예외) | 앱 |

색은 파랑(동작) · 민트(충전) · 앰버(경고) 3가지만 씀. 그 외에는 회색조만.

## 타이포
폰트: **Asta Sans** (Google Fonts, 한/영). **G** → `res/font`에 넣고 `FontFamily`로 등록.
| 토큰 | size / line (sp) | weight | 용도 | 출처 |
|---|---|---|---|---|
| `displayXL` | 160 / 168 | ExtraBold | Resting 링 안 SOC % | 앱 ↓ |
| `displayL` | 112 / 120 | Bold | Resting 남은 시간 · Idle SOC % | 앱 ↓ |
| `headlineL` | 60 / 80 | ExtraBold | Done "목표 80% 도달" | G |
| `titleL` | 40 / 52 | Bold | 화면 제목 | G |
| `titleS` | 32 / 40 | Bold | 버튼 Large 라벨, 칩 라벨 | G |
| `bodyL` | 30 / 38 | Normal·Strong | 본문, 카드 문장 | G |
| `bodyS` | 26 / 34 | Normal | 보조 설명 | G |
| `labelM` | 24 / 28 | Normal | 작은 라벨 ("목표 80%", "남은 시간") | G |
| `labelS` | 20 / 24 | Normal | "학습 기록 지우기" 텍스트 버튼, "실측" 태그 (v0.6) | G |

↓ **display 값을 따로 둔 이유**: 1.5m 거리에서 읽으려면 글자 높이가 대략 10mm 이상 필요(시거리 1:150 기준). 가이드에서 가장 큰 Headline 60sp는 화면 밀도에 따라 숫자 높이가 7~8mm 정도라 부족하고, 112sp 이상이면 대략 13mm 이상. 숫자에만 쓰고 한글 문장에는 쓰지 않음. 실제 기기 dpi를 확인한 뒤 줄일 수 있음.
숫자는 `fontFeatureSettings = "tnum"`(고정폭)으로 값이 바뀔 때 글자 폭이 흔들리지 않게 함.

## 크기 · 간격
| 토큰 | dp | 출처 |
|---|---|---|
| 버튼 Large 높이 / 모서리 | 100 / 32 | G |
| 버튼 Medium 높이 / 모서리 | 80 / 24 | G |
| 버튼 Small 높이 / 모서리 | 64 / 16 | G (PM 전달값. 원문 이미지에서 L·M만 실측) |
| 최소 터치 영역 | 64×64 | 앱 (Small 버튼 기준) |
| 카드 모서리 | 24 | 앱 (M 버튼과 맞춤) |
| 그리드 | 8의 배수 | 앱 |
| 화면 좌우 여백 | 48 | 앱 |
| 섹션 간격 | 48 | 앱 |
| 요소 간격 | 24 | 앱 |
| 카드 안쪽 여백 | 32 | 앱 |
| SOC 링 지름 / 두께 | 520 / 28 | 앱 (portrait 800 기준. 가로 폭의 65%) |

가이드 Display Layout 페이지에는 앱 창 해상도·그리드 수치가 없음(17″/14.6″/12.9″ 디스플레이 크기만 있음). 그래서 portrait 800×1200dp를 기준으로 잡고 **폭에 비례**하게 배치: 링 지름 = `min(maxWidth*0.65, maxHeight*0.45)`.

## 화면별 규칙
| 화면 | 배경 | 버튼 | 비고 |
|---|---|---|---|
| Idle | `bg` | 목표 칩 2개 80 / 90%(M 80dp, 각 칩 폭 = (가용폭−24)/2, 단일 선택) + 시트 통풍 끔/켬 칩 2개(같은 스타일, 기본 끔, 목표 칩과 간격 48dp). 목표 칩 아래 보조 문구 "시작 후 실제 충전 속도로 남은 시간을 계산해요"(bodyS, textTertiary, 간격 24dp) — Idle에는 예상 시간 숫자를 표시하지 않음 + 주 버튼 1개(L 100dp, `primary`) | 칩은 버튼 수에서 제외(값 선택용). 충전 아님·기어≠P이면 주 버튼 비활성 + 이유 한 줄 |
| Resting | `bgRest` | "종료" 1개 (L, `surfaceRest`, 글자 `restText`) | 상단 상태바 없음. 화면 밝기 최소는 `window.attributes.screenBrightness = 0.1f`(앱). 애니메이션 없이 값만 바뀜 · 하단 상태줄(`bodyS`, `restTextDim`, 가운데 정렬): 적용 항목 + 미지원 항목. 뒷좌석 착좌 감지 시 맨 위에 "뒷좌석에 사람이 있어 등받이를 절반만 젖혔어요" 1줄 추가 |
| Resting · 계산 중 | `bgRest` | "종료" 1개 | 시작 후 3분. 남은 시간 자리에 "계산 중…"(`titleL`, `restTextDim`), 자리 높이 120dp 유지해 숫자로 바뀔 때 레이아웃이 흔들리지 않게 |
| Restoring | `bgRest` | **"취소" 1개** (L, `surfaceRest`) — 이 화면만 | 목표 도달 직후 10초. 문구 "목표 80%에 도달했습니다 · 10초 후 시트를 원래대로" ("충전 완료" 표현은 앱 전체 금지, PRD v0.5). 카운트다운 `displayXL` 1초마다 숫자만 교체(애니메이션 없음). 시트 이동 중 화면 어디든 터치하면 즉시 정지 (PRD v0.3). "취소" → 복원 없이 세션 종료 → Done(그대로 둠) |
| Done (2변형) | `bg` | "확인" 1개 (L, `primary`) | 제목은 종료 사유로: 목표 도달(Restoring 완료·취소) → "목표 N% 도달" / 그 외(사용자 종료·충전 중단·기어 변경) → "휴식 모드 종료" (PM 결정). 하차 체크 카드는 경고가 있을 때만. 없으면 "확인할 것 없음" 한 줄. 문구 형식 "뒷좌석에 사람이 있어요"(아동 표현 금지), 순서: 뒷좌석 → 창문 → 실내등. 변형 2개 — 복원됨: "시트·조명·공조를 원래대로 되돌렸어요." / 그대로 둠(Restoring 취소): "시트·조명·공조는 지금 상태 그대로예요." 되돌리기 버튼 없음 |
| Idle · 학습됨 (v0.6) | `bg` | 주 버튼 1개 그대로 | 학습 기록 n ≥ 1. 보조 문구 → "지난 충전 n회 기준, 80%까지 보통 약 N분"(`bodyS`, **`textSecondary`** — 실제 정보라 한 단계 올림). 그 바로 아래 텍스트 버튼 "학습 기록 지우기"(`labelS`, `textTertiary`, 밑줄, 터치 높이 64dp, 주 버튼에서 멀리 둠). 시트 통풍 섹션 간격은 48 → 24(텍스트 버튼 64dp가 여백 역할). 지우면 기본 문구로 돌아감 |
| Resting · 계산 중 + 학습값 (v0.6) | `bgRest` | "종료" 1개 | "계산 중…" 120dp 자리 아래 보조 줄 "지난 충전 기준 약 N분"(`bodyS`, `restTextDim`) |
| Resting · 실측 (v0.6) | `bgRest` | "종료" 1개 | 학습값을 보여준 세션만: "남은 시간" 라벨(labelM) 오른쪽 16dp에 태그 "실측"(숫자 옆이 아님 → 숫자 가운데 정렬 유지)(`labelS`, `restTextDim`, 테두리 2dp `restTextDim`, r8, 안쪽 4/12). 학습값 보조 줄은 제거. 학습값이 없던 세션은 태그 없음(③과 동일) |

## 미지원 표시
도메인 미지원 → Resting의 적용 목록에서 해당 항목 끝에 `· 이 차량 미지원`(`textTertiary` / Resting은 `restTextDim`). 아이콘이나 색으로 따로 경고하지 않음.

주행 중 전용 화면은 없음 (PRD v0.5: Distraction Optimized 미선언 → 주행 중에는 플랫폼이 앱을 가림).
