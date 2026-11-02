# 충전쉼표 (Plug & Rest) 개인정보처리방침

시행일: {YYYY-MM-DD}

'충전쉼표'(이하 "앱")는 이용자의 개인정보를 수집하지 않습니다. 앱이 차량 정보를 어떻게 다루는지 아래에 안내합니다.

## 1. 수집하는 개인정보
**없습니다.** 앱은 회원가입, 로그인, 계정, 위치정보, 광고 식별자, 사용 통계·분석 도구를 사용하지 않습니다. 이름, 연락처, 차량번호, 차대번호(VIN)도 수집하지 않습니다.

## 2. 앱이 기기 안에서 처리하는 차량 정보와 처리 목적
앱은 아래 차량 신호를 **차량 기기 안에서만** 읽고 처리합니다. 외부로 전송하지 않습니다.

| 차량 정보 | 처리 목적 |
|---|---|
| 배터리 잔량(SOC), 충전 포트 연결 상태, 충전 스위치 상태 | 충전 중인지 판단, 목표 배터리까지 남은 시간 계산, 목표 도달 감지 |
| 기어·브레이크 상태, 주행 제한 상태 | 정차(P) 중에만 휴식 모드가 동작하도록 제한 |
| 시트 등받이·통풍 설정 | 휴식 자세로 바꾸고 원래대로 복원 |
| 조명(앰비언트·실내등), 공조(전원·온도·풍량·내외기) 설정 | 휴식 환경으로 바꾸고 원래대로 복원, 하차 전 실내등 점검 |
| 좌석 착좌 여부(사람이 앉아 있는지 여부만) | 등받이를 젖히기 전 뒷좌석 점검, 하차 전 안내 |
| 창문 열림 상태 | 하차 전 안내 |

좌석 착좌 정보는 앉아 있는지 여부만 확인합니다. 누가 앉았는지는 식별하지 않습니다.

## 3. 보관 기간과 파기
- 휴식 모드를 시작하면 원래대로 되돌리기 위해 **시트·조명·공조 설정값(스냅샷)**과 휴식 세션 정보(목표 배터리, 시작 시각, 시트 모드(통풍 끔/켬), 절반 리클라인 여부)만 기기 내부 저장소에 저장합니다.
- 휴식 모드가 끝나면(목표 도달, 충전 포트 분리, 사용자 종료 등) 즉시 삭제합니다. 앱이 비정상 종료되어 남은 경우에는 저장 후 **2시간**이 지나면 더 이상 쓰지 않고, 다음 실행 때 삭제합니다.
- 위 정보 외에는 저장하지 않습니다. 앱 데이터는 기기 백업 대상에서 제외됩니다. 앱을 삭제하면 모든 데이터가 함께 삭제됩니다.

## 4. 네트워크 사용
- 앱에는 자체 서버가 없고, 앱이 외부 서버와 통신하지 않습니다.
- 음성 안내는 차량용 TextToSpeech SDK의 **ON_DEVICE 모드**로 기기 안에서 합성합니다.
- 앱 자체는 외부로 데이터를 보내지 않습니다. 음성 안내 SDK는 서비스 검증을 위해 네트워크를 사용할 수 있으며, 앱은 그 과정에 차량·개인정보를 넘기지 않습니다.

## 5. 제3자 제공 및 처리 위탁
**없습니다.** 수집하는 개인정보가 없으므로 제3자에게 제공하거나 처리를 위탁하지 않습니다.

## 6. 이용자의 권리
- 앱 화면의 "종료" 버튼이나 차량 기본 조작으로 언제든 휴식 모드를 멈출 수 있습니다.
- 차량 설정의 앱 정보에서 '데이터 삭제' 또는 앱 삭제를 하면 저장된 설정값을 즉시 지울 수 있습니다.
- 개인정보를 보유하지 않으므로 열람·정정·삭제를 요청할 대상 정보가 없습니다. 궁금한 점은 아래 문의처로 연락해 주세요.

## 7. 문의처
- 이메일: {contact@example.com}

## 8. 방침 변경
이 방침이 바뀌면 시행 7일 전부터 앱마켓 앱 상세 페이지에 공지합니다.

---

### English Summary
- Plug & Rest collects **no personal data**: no account, server, location, ads, or analytics.
- Vehicle signals (battery level, charge port/switch, gear, seat/light/climate settings, seat occupancy, window state) are processed **only on the in-vehicle device** and never transmitted.
- Only a snapshot of seat, light, and climate settings (plus target level, start time, seat mode, and half-recline flag) is stored locally. It is deleted when the rest session ends, and a leftover snapshot older than **2 hours** is discarded on the next launch.
- The app has no own server. Voice guidance runs in the TTS SDK's ON_DEVICE mode. The app itself sends no data out; the TTS SDK may use the network for service verification, and the app passes no vehicle or personal data to it.
- No third-party sharing. Contact: {contact@example.com}. Effective: {YYYY-MM-DD}.
