# Pleos Connect 에뮬레이터 실측 (2026-11-03 재부팅 후, PM 직접)

## 환경
- AVD Pleos_Connect_v2_1 (API 34, Pleos Connect v2.1.0), **RAM 8GB, -gpu host, -cores 4** → 8.9초 부팅, 행 없음. 4~6GB에서는 부팅 직후 행 감지로 종료됨(호스트 여유 메모리 부족 상태였음).
- Virtual Console: 게스트 8088 → `adb forward tcp:8088 tcp:8088`, http://127.0.0.1:8088. **REST로 직접 제어 가능**하나 브라우저 하트비트가 없으면 `503 emulator_inactive` → `POST /browser/session?client=<id>`를 1초마다 보내면 활성화.
  - 기어: `GET /vehicle/gear?gear=Park|Drive|...`
  - 배터리: `GET /vehicle/ev-battery?field=soc&value=50`, `field=charge_port_open&value=true`, `field=charge_switch&value=true`
  - 바디: `/vehicle/body?ambient_brightness=..`, 창문 `windows`; 공조 `/vehicle/hvac?fan_speed=..`; 상태 `GET /vehicle/state`, 카탈로그 `GET /vehicle/catalog`

## 앱 동작 (실제 PleosVehicleGateway)
| 항목 | 결과 |
|---|---|
| 설치·실행 | debug APK 설치 OK, 런처 독에 아이콘 표시 |
| 레이아웃 | 앱 영역이 landscape 2-window(약 1900dp). `screenOrientation=portrait`면 검은 레터박스 → 제거 후 두 열 레이아웃(cd572a8) |
| SOC·기어 읽기 | 100% / 기어 P 즉시 표시. 콘솔에서 SOC 50 변경 시 반영 |
| 충전 판정 | 포트 열림+스위치 ON+SOC 50 → 버튼 활성(“충전 중 · 기어 P”) |
| 휴식 모드 시작 | FGS 시작, **TTS 온디바이스 재생**(GAAS OnDeviceStreamPlayer 로그), 시트 리클라인 **실제 실행**(콘솔 driver recline 80, Pleos Driving view에 "시트 위치 변경됨 저장할까요?" 표시), HVAC 22°C 설정 반영(하단 바 22.0° A/C), 뒷좌석 착좌 감지(기본 occupied → 절반 리클라인 안내) |
| 앰비언트 | 1차: "이 차량 미지원". 원인 = capability 영역이 운전석 단일이 아니라 `[ROW_1_LEFT, ROW_1_RIGHT, ROW_2_*]` 복합 영역 하나로 옴(밝기 0..100, READ_WRITE). 운전석 우선·없으면 첫 영역을 쓰도록 수정 후 **콘솔 brightness 10, color #ffb36b 반영 확인** |
| 종료 → 복원 | 종료 시 카운트다운(5초) 화면 정상 표시 |

## 2차 실측 (앰비언트 수정 후)
- 앱이 건드린 속성(Pleos 서비스 권한 검증 로그): SEAT_BACKREST_ANGLE_1_MOVE(2회=시작/정지), INTERIOR_LIGHTS_AMBIENT_BRIGHTNESS(+_COLOR), HVAC_TEMPERATURE_SET, HVAC_RECIRC_ON, HVAC_POWER_ON, HVAC_FAN_SPEED, CABIN_LIGHTS_SWITCH — 모두 권한 통과, 거부 없음.
- 시트: move 명령은 전달되나 Virtual Console의 recline 위치 값은 변하지 않음(콘솔 위치는 콘솔 입력 전용으로 보임). 실차 각도 피드백은 미확인.
- HVAC 온도 22°C는 Pleos 하단 바에 반영. 풍량은 콘솔 fan_speed 3 그대로(콘솔 값이 앱 set을 반영하는지 불명).
- 뒷좌석 착좌: 콘솔 occupancy false인데 앱은 "뒷좌석에 사람이 있어 절반만" 표시 → occupancy 읽기가 null로 떨어져 보수 경로(절반) 사용 추정. 확인 필요.

## 3차 실측 (배터리 모드 전체 플로우, SOC 50→80 램프, docs/research/emu/run1/)
- 계산 중 → 실측 "약 N분" 전환 확인(62%쯤부터). 79%에서 **선라이즈 발동**: 배경 전환, "곧 깨워 드릴게요 · 조명을 천천히 밝히는 중", 풍량 2단 표시, 콘솔 앰비언트 밝기 상승 로그. 80% 도달 → TTS 재생(GAAS 로그 jobId 증가) → 복원.
- 시트: 리클라인 시 Pleos Driving view에 "시트 위치 변경됨 — 운전석 0에 저장할까요?" 프롬프트 표시 → 시트 이동이 차량 쪽에 실제 반영됨(콘솔 위치값과 별개).
- 착좌: SEAT_OCCUPANCY 원시값 1=비어 있음, 2=착좌(콘솔 토글과 일치). 앱의 "사람이 있어" 표시는 초기 읽기 null 시 보수 경로 → 문구를 "뒷좌석 확인이 안 돼"로 분기(빌드5), 원인 로그 추가.
- 사용자가 에뮬레이터에서 직접 세션(90%·통풍)을 시작해 조작 가능 확인.

## 미확인 / 후속
- 앰비언트 Light set 미지원 원인(capability vs 권한 vs 영역)
- HVAC 풍량 매핑(앱 1단 → 콘솔 fan 3?) 확인
- 선라이즈·출발 모드 전체 플로우 실측, 완료 TTS, 하차 체크(창문)
- Pleos 모드 스크린샷·TTS 포함 녹화(OBS)
