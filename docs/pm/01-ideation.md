# 아이디어 후보 및 PM 추천 (2026-11-03)

평가축 = 1차 심사 4항목 (차량특화성 / 창의성 / 기술활용성 / 사업성) + 에뮬레이터 시연 가능성(2차 PT 필수). 5점 척도.

| # | 후보 | 차량특화 | 창의성 | 기술활용 | 사업성 | 시연가능 | 합 |
|---|---|---|---|---|---|---|---|
| B | **충전 시간 휴식 모드 (가칭 "Charge & Chill")** — 충전 시작 감지 → 원탭으로 시트 리클라인·앰비언트·공조 세팅, SOC 기반 남은시간, 목표 SOC 도달 시 TTS 알림·자동 복원 | 5 | 4 | 5 | 5 | 5 | 24 |
| A | 반려동물 동승 케어 — 뒷좌석 착좌감지로 펫 모드, 하차 시 공조 유지·창문 잠금, 펫 동반 가능 경유지 | 5 | 3 (Tesla Dog mode 유사) | 4 | 4 | 4 | 20 |
| D | 공유/법인차 인수인계 체크 — 텔테일·TPMS·SOC·주행거리 자동 점검 리포트, 시트 메모리 | 4 | 3 | 4 | 4 (B2B, Pleos Fleet 연계) | 5 | 20 |
| E | 하차 가드 — P단+문열림 시 창문/라이트/뒷좌석 착좌(아이 방치) 경고 TTS | 5 | 3 | 3 | 3 (기능 단순, 단독 앱 약함) | 5 | 19 |
| H | EV 에코 드라이빙 코치 — 순간/누적 효율·브레이크·크루즈로 코칭 | 4 | 2 (클러스터 기본 제공) | 4 | 3 | 3 (속도 신호 없음) | 16 |

## 추천: B "Charge & Chill" (+ E의 "자동 복원/하차 체크"를 종료 플로우에 흡수)

근거
- 주최측 예시 주제 "충전·휴식"에 정면으로 대응. 모바일로는 불가능(시트/조명/공조 제어) → 차량특화성 만점 논리.
- Vehicle SDK 4개 도메인(EvBattery·Seat·Light·HVAC) + TTS + (선택) NaviHelper/FusedLocation → 기술활용성 서술이 자연스럽다.
- Virtual Console에 SOC 슬라이더·Charge Switch·Seat·Light·HVAC·Gear가 전부 있어 2차 PT 라이브 시연 가능.
- 사업성: EV 보유자는 충전 1회 20~40분 대기, 주 2~3회 → 사용 빈도·Retention 근거가 명확. 충전소 주변 상권/콘텐츠 제휴로 확장.

## MVP 범위 (ponytail: 2차 PT까지 이것만)
1. 충전 감지: `registerEvBatteryStateOfCharge` + `getEvChargeSwitchState` + Gear P
2. 휴식 모드 ON (원탭): `moveSeatBackrestAngle`, `setHvacSeatVentilation/Temperature`, 앰비언트 Light set, HVAC 온도 set
3. 목표 SOC: `setEvChargePercentLimit`, SOC 변화율로 남은 시간 계산, 큰 링 UI(주행 불가 상태이므로 화면 제약 완화)
4. 도달/충전 종료/기어 변경 → TTS 안내 + 시트/조명 자동 복원
5. (가산) 주변 걷기 코스/카페 → NaviHelper 경유지

비범위: 결제, 충전소 검색(기존 내비 영역), 계정, 백엔드.
