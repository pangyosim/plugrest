# Pleos Connect SDK 인벤토리 (2026-11-03 조사)

출처: https://document.pleos.ai/docs/pleos-only/* , 샘플앱 https://github.com/Pleos-Playground/pleos-sample-vehicle-app

## 플랫폼
- AAOS 기반. Android Studio Automotive 템플릿, Kotlin, minSdk 28+, Gradle 8+, JDK 17
- Maven: `https://nexus-playground.pleos.ai/repository/maven-releases/` (settings.gradle.kts, FAIL_ON_PROJECT_REPOS)
- Vehicle SDK `ai.pleos.playground:Vehicle:2.3.2` <!-- 검수 정정 2026-11-03 --> (공식 최신·Importing 문서·릴리즈노트·Nexus = 2.3.2. 샘플 repo·getting-started 문서는 2.3.1이나 **2.3.1은 Nexus에 없음(404)** → 2.3.2 사용)
- 에뮬레이터: Pleos Connect Emulator 2.1.0, API 34 system image (SDK Update Sites: mac `crp-arm64-sys-img.xml`), AVD Automotive / Cold boot / 4GB
- Virtual Console: Emulation Controls로 차량 상태 시뮬레이션 (아래)

## SDK 모듈 5종
| SDK | 용도 |
|---|---|
| Vehicle | 차량 상태 조회/제어 (19 도메인) |
| NaviHelper | 목적지 설정/확인/취소, 경로 옵션, 경유지 추가/조회/삭제, 내비 정보 조회, 내비 화면 호출, NaviHelperEventListener |
| TextToSpeech | 음성 합성 |
| SpeechToText | 음성 인식 |
| FusedLocation | 위도/경도/고도/heading/신뢰도/timestamp, getLastKnownLocation, register/unregisterFusedLocationCallback, addProximityAlert(영역 진입/이탈) — 권한 `pleos.car.permission.FUSED_LOCATION` <!-- 검수 정정 2026-11-03 --> **protectionLevel=dangerous → 런타임 권한 요청 필요**(Vehicle 권한과 다름) |

## Vehicle 도메인 (각각 checkXxxCapabilities / get / set / register·unregister 패턴)
| 도메인 | Set | 권한(read) | 비고 |
|---|---|---|---|
| Brake | - | CAR_DRIVE | 브레이크 페달, 파킹 브레이크 |
| CarInfo | - | CAR_INFO | 제조사/모델연도/치수 |
| Display | - <!-- 검수 정정 2026-11-03 --> | CAR_INFO | 온도 단위 조회/구독만 (setter 없음. Intro 표의 '설정'은 API ref와 불일치) |
| Door | O | CAR_DOORS | 열림/잠금 |
| DrivingMode | - | CAR_DRIVE | 드라이브 모드, 크루즈 상태/목표속도 |
| EvBattery | O | CAR_ENERGY | SOC, 충전 스위치/포트/전류제한/퍼센트제한, 효율(순간/현재/누적/충전후) |
| Fuel | - | CAR_ENERGY | 연비 |
| HVAC | O | CAR_CLIMATE | 온도/풍량/방향/AC/재순환/디포그 |
| Light | O | CAR_LIGHTS | 앰비언트 밝기·색, 실내등 |
| Odometer | O <!-- 검수 정정 2026-11-03 --> | CAR_DRIVE | 총/트립 거리·시간. `setRangeRemainingDistance`(CONTROL_CAR_DRIVE) |
| Safety | - | CAR_SAFETY | 텔테일(경고등) |
| Seat | O(move/set/memory) | CAR_SEATS | 위치 11축 move, 열선/통풍, 착좌감지(occupancy), 벨트, 메모리 |
| SideMirror | O | CAR_MIRRORS | 열선 |
| SteeringWheel | O | CAR_DRIVE(조향각) / CAR_CLIMATE(열선) <!-- 검수 정정 2026-11-03 --> | `getCurrentSteeringWheelAngle`(CAR_DRIVE), 열선 set은 CONTROL_CAR_CLIMATE |
| Tire | - | CAR_SAFETY | TPMS |
| Toll | O | CAR_TOLL | 하이패스 |
| TurnSignal | - | CAR_LIGHTS | |
| Window | O | CAR_WINDOWS | 위치/잠금 |
| Wiper | O | CAR_WINDOWS | |
- 제어는 `CONTROL_CAR_*` 권한. <!-- 검수 정정 2026-11-03 --> **예외: Light의 `setAmbientLightBrightness` / `setAmbientLightBrightnessColor` / `setAmbientLightSwitch`는 `CAR_LIGHTS`** (나머지 Light setter는 CONTROL_CAR_LIGHTS). 권한 없으면 onFailed(VehicleException ERROR_SECURITY_PERMISSION)
- 호출은 전부 비동기 콜백 (onComplete/onFailed). 리스너는 onXxxUpdated.
- <!-- 검수 정정 2026-11-03 --> **차속: Vehicle SDK에는 없음**(2.3.2에서 `VehicleSpeedListener` 삭제). **AAOS `PERF_VEHICLE_SPEED`(CarPropertyManager)로 조회 가능**(Vehicle App FAQ, 기획 가이드 '표준 데이터: 속도, 기어 상태'). 차종별 지원 확인 필요, Virtual Console에는 속도 카드 없음. 주행/정차 판단은 CarUxRestrictions 우선 + Gear + 차속.

## Virtual Console로 시뮬레이션 가능한 값
Gear P/R/N/D, Brake 0~100%, Odometer, 조향각, 크루즈 / Battery SOC 0~100%·Energy·Capacity·Charge Switch·Port·전류·퍼센트 제한 / Door 위치·잠금·트렁크 / 실내등·앰비언트 밝기·색·외부등·하이빔·비상등·방향지시 / Window / **Seat Occupancy** / 와이퍼 / Telltale(파킹브레이크, 벨트, ABS, TPMS) / HVAC 전체(온도 16~30, 풍량, 시트 열선, 공기청정) / 하이패스 / 차량 알림

## 기획 제약 (planning guide / review guide)
- 주행 중 키보드 입력 금지 → 프리셋/최근/음성. 큰 터치타깃, 적은 텍스트, 애니메이션 최소화, 주행 중 영상 금지
- `CarUxRestrictions`로 주행 제한 상태 판단, 제한 기능은 UI 축소/비활성
- 화면: Driving view(고정) + 앱 영역 1~2 window. 기본 portrait 1 window, `screenOrientation=landscape`면 2 window. 반응형 필수
- 백그라운드: 폴링/타이머/센서 최소화. 상태 복원 전략
- 개인정보/위치 사용 시 약관+개인정보처리방침 필요
- 오디오 포커스 정책 준수 (TTS)
