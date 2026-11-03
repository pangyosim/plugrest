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
| 앰비언트 | 화면에 "이 차량 미지원" → capability 미지원 또는 권한 문제. 콘솔 brightness 100 그대로. **원인 조사 필요** |
| 종료 → 복원 | 종료 시 카운트다운(5초) 화면 정상 표시 |

## 미확인 / 후속
- 앰비언트 Light set 미지원 원인(capability vs 권한 vs 영역)
- HVAC 풍량 매핑(앱 1단 → 콘솔 fan 3?) 확인
- 선라이즈·출발 모드 전체 플로우 실측, 완료 TTS, 하차 체크(창문)
- Pleos 모드 스크린샷·TTS 포함 녹화(OBS)
