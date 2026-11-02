# 충전쉼표 (Plug & Rest)

> 전기차 충전을 시작하면 원탭으로 시트·조명·공조를 휴식 공간으로 바꾸고, SOC를 남은 분으로 보여 주며, 목표 충전량에 이르면 음성으로 깨워 원래 상태로 되돌려 주는 Pleos Connect 충전 대기 앱.

- 팀: {팀명}
- 저장소: https://github.com/pangyosim/plugrest
- 출품: 2026 현대자동차그룹 차량용 앱 개발 공모전

## 주요 기능
- **충전 감지 → 휴식 모드 버튼**: 충전 포트가 연결되고 최근 60초 동안 SOC가 오르면 "휴식 모드 시작" 버튼과 목표 SOC(80/90%) 칩을 켭니다.
- **원탭 휴식 모드**: 한 번 누르면 차량을 휴식 상태로 바꿉니다.
  - 뒷좌석 착좌를 먼저 확인하고 운전석 등받이를 젖힙니다. 운전석 뒤에 사람이 있으면 절반만 젖힙니다. 통풍·열선 시트는 선택입니다(기본 OFF).
  - 앰비언트 조명을 어둡고 따뜻한 색으로 바꾸고 실내등을 끕니다.
  - 공조를 22°C·풍량 1단·외기 유입으로 맞춥니다.
  - 목표 SOC는 앱이 감시만 합니다. **차량 충전 한도 설정은 바꾸지 않습니다.**
- **SOC → 남은 분**: 처음 3분은 "계산 중"으로 표시하고, 이후 최근 5분 SOC 상승률로 남은 시간을 5분 단위로 보여 줍니다. 80% 이후 느려지는 구간은 보정합니다.
- **목표 도달 깨움 + 자동 복원**: 목표 SOC에 이르면 "목표 80%에 도달했습니다"라고 음성으로 알리고, 10초 카운트다운 뒤 시트·조명·공조를 시작 전 상태로 되돌립니다. 완료 화면을 확인하지 않으면 30초 간격으로 최대 3회 다시 알린 뒤 종료합니다.
  - "취소"를 누르면 지금 상태 그대로 끝냅니다. 시트가 움직이는 중 화면을 터치하면 즉시 멈춥니다.
  - 정차 중 브레이크를 밟으면 출발 의도로 보고 시트를 먼저 되돌립니다.
  - 다른 앱으로 넘어가도 Foreground Service가 감시·깨움·복원을 계속합니다.
  - 충전 포트를 분리하거나 2시간이 지나면 10초 예고 후 원래 상태로 되돌리고 종료합니다.
- **하차 체크 카드**: "조수석 창문 열림", "뒷좌석에 사람이 있어요"처럼 창문·실내등·착좌 상태를 한 줄로 안내합니다.
- **정차 전용**: Distraction Optimized를 선언하지 않아 주행 중에는 플랫폼이 앱 사용을 막습니다. 주행 중 화면은 없습니다. 추가로 `CarUxRestrictionsManager`가 주행 제한을 알리거나 기어가 P를 벗어나면 모든 차량 제어를 즉시 멈추고, 시트 복원도 하지 않습니다. 다시 정차하면 "되돌리기 / 그대로"를 묻습니다.

## 실행 방법

### A. APK 설치 (심사용 권장)
1. 아래 [테스트 환경](#테스트-환경--필요-설정)대로 Pleos 에뮬레이터를 실행합니다.
2. APK를 설치합니다.
   ```bash
   adb install -r apk/TEAM_PlugRest_release.apk
   ```
3. 에뮬레이터 앱 목록에서 **충전쉼표**를 실행합니다.

### B. 소스 빌드
```bash
git clone https://github.com/pangyosim/plugrest.git
cd plugrest/app                    # app/ 폴더가 Gradle 루트(단일 모듈)
./gradlew assembleRelease          # 결과: app/build/outputs/apk/release/PlugRest-release.apk
./gradlew installDebug             # 실행 중인 에뮬레이터에 바로 설치 (PlugRest-debug.apk)
```
- JDK 17, Android SDK 35가 필요합니다.
- release APK는 디버그 키로 서명돼 있어 바로 `adb install`할 수 있습니다.

| 빌드 옵션 | 기본값 | 설명 |
|---|---|---|
| `-Pdemo=false` | 데모 모드 켜짐 | 데모 모드는 "계산 중" 3분 → 20초, 충전 판정 60초 → 10초로 줄입니다. 실제 시간으로 테스트하려면 이 옵션으로 끕니다. 10초 카운트다운은 동일합니다 |
| `-Pfake=true` | 꺼짐 | Pleos 에뮬레이터에서도 Fake 차량 모드로 실행합니다 |
- 의존성은 Google, Maven Central, Pleos Nexus(`https://nexus-playground.pleos.ai/repository/maven-releases/`)에서 받습니다. 별도 인증은 필요 없습니다.

## 테스트 환경 / 필요 설정

| 항목 | 값 |
|---|---|
| 테스트 기기 | Pleos Connect 에뮬레이터 v2.1.0 (Automotive, API 34) |
| 호스트 | Windows / Linux / macOS. Pleos 문서상 GPU 필수(최소 RTX 3060 Laptop 6GB / RX 6600M 8GB). Apple Silicon 지원은 공식 문서에 언급이 없습니다. Pleos 에뮬레이터가 실행되지 않는 환경에서는 Fake 차량 모드로 확인할 수 있습니다 |
| 빌드 | Android Studio, JDK 17, AGP 8.8.2, Kotlin 2.0, compileSdk 35, minSdk 28 |
| Pleos SDK | Vehicle 2.3.2 (Gear 도메인은 2.3.2부터), TextToSpeech 3.3.1 (ON_DEVICE, 한국어) |
| 네트워크 | 실행 중에는 불필요 (TTS ON_DEVICE 모드, 서버 없음) |
| 계정 / 로그인 | 없음 |

- **Pleos 에뮬레이터 실측 체크리스트**: SDK 신호가 에뮬레이터에서 실제로 동작하는지 확인할 항목은 [docs/research/05-sdk-signatures.md §8](docs/research/05-sdk-signatures.md#8-prd-대비-갭과-대안-pm-확인-필요)에 정리돼 있습니다.

### Pleos 에뮬레이터 설치 요약
1. **업데이트 사이트 추가**: Android Studio에서 **Tools > SDK Manager > SDK Update Sites > +**로 들어가 아래 URL을 추가합니다.
   - macOS (arm64): `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
   - Windows / Linux: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`
2. **시스템 이미지 설치**: **SDK Platforms > API 34**에서 Show Package Details를 켜고 **Pleos Connect** 시스템 이미지를 설치합니다. 42dot SDK 라이선스 동의가 필요하고, 설치 후 Android Studio를 재시작합니다.
3. **가상 기기 생성**: **Device Manager > Create device > Automotive > Pleos Connect v2.1.0 > API 34**를 고릅니다. Advanced 설정에서 Boot option을 **Cold boot**, RAM을 **4GB**로 지정합니다.
4. **Virtual Console 연결**: [Virtual Console 가이드](https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide)를 따라 연결합니다.

> **Pleos 에뮬레이터 없이 확인하기 (Fake 차량 모드)**
> Pleos Vehicle 서비스가 없는 기기(일반 Android Automotive AVD 등)에서는 앱이 **자동으로 Fake 차량 모드**로 실행됩니다. 따로 설정할 것은 없습니다.
> 화면 맨 아래 **"▲ Fake 차량 패널"**을 누르면 SOC 슬라이더와 충전 포트·기어 D·뒷좌석 착좌·브레이크·조수석 창문 토글이 나옵니다. 앱이 보낸 차량 제어 호출 로그도 볼 수 있습니다.
> 아래 시연 순서의 "Virtual Console 조작"을 이 패널로 똑같이 따라 할 수 있습니다. Charge Switch는 패널에 없지만, 충전 판정에 필요한 신호가 아니라 생략해도 됩니다.

## Virtual Console 시연 순서 (약 2분 30초)

| # | Virtual Console 조작 | 앱에서 확인할 것 |
|---|---|---|
| 0 | 초기값: Gear **P**, SOC **50%**, 충전 포트 **미연결**, Charge Switch **OFF**, 뒷좌석 착좌 **OFF**, 조수석 Window **0%** | 대기 화면, 버튼 비활성 |
| 1 | 충전 포트 **연결** → Charge Switch **ON** → SOC를 51%로 올림 | SOC 상승이 확인되면 "휴식 모드 시작" 버튼과 목표 칩(80/90) 활성 |
| 2 | 앱에서 목표 **80%** 탭 | 휴식 화면(SOC 링, 남은 시간 "계산 중"), "목표 80%에 도달하면 깨워 드릴게요" 음성. Console에서 Seat·Light·HVAC 값이 바뀌었는지 확인 |
| 3 | SOC 슬라이더를 5초마다 5%씩 올려 79까지 | "계산 중"이 풀린 뒤 "약 N분"으로 갱신 |
| 4 | SOC **80** | "목표 80%에 도달했습니다. 10초 후 시트를 원래대로 되돌립니다" 안내와 카운트다운·"취소" 버튼 → 10초 뒤 Console에서 시트·조명·공조가 시작 전 값으로 돌아감 |
| 5 | 조수석 Window **30%** | 하차 체크 카드 "조수석 창문 열림" |

- **다시 하려면**: 앱에서 "종료"를 누르고 0번 초기값으로 되돌립니다.
- **추가 확인 (선택)**:
  - 2번 전에 뒷좌석 착좌를 **ON**으로 두면 등받이를 절반만 젖힙니다.
  - 2번 다음에 **Brake**를 밟으면 시트가 즉시 복원됩니다.
  - 2번 다음에 **Gear D**로 바꾸면 모든 제어가 멈추고 시트는 복원하지 않습니다. 다시 **Gear P**로 바꾸면 "되돌리기 / 그대로" 카드가 뜹니다.
- 제출 APK는 **데모 모드가 켜진 상태**이며, 앱 상단 라벨로 데모 모드임을 표시합니다. "계산 중"은 20초, 충전 판정은 10초 걸립니다. 실제 시간(3분·60초)으로 보려면 `-Pdemo=false`로 빌드합니다.

## 권한

권한 이름은 모두 `pleos.car.permission.` 접두사를 씁니다(예: `pleos.car.permission.CAR_ENERGY`). 에뮬레이터에서는 매니페스트 선언만으로 동작하고, 실행 중 권한 요청 창은 뜨지 않습니다. App Market 출시 시에는 Vehicle SDK 퍼미션 사전 협의·승인이 필요합니다(Pleos 앱 심사 가이드). 차량 충전 설정 제어 권한(`CONTROL_CAR_ENERGY`)은 요청하지 않습니다.

| 권한 | 용도 |
|---|---|
| `CAR_ENERGY` | SOC·충전 포트·충전 스위치 상태 읽기 |
| `CAR_DRIVE` | 기어(`Gear.registerGearSelection`, `GEAR_PARK`)·브레이크 페달(`Brake.registerBrakePedalStatus`) |
| `CAR_SEATS` / `CONTROL_CAR_SEATS` | 착좌 감지, 등받이 각도, 열선·통풍 시트 |
| `CAR_LIGHTS` | 조명 상태 읽기, 앰비언트 밝기·색·스위치 설정 |
| `CONTROL_CAR_LIGHTS` | 실내등 등 앰비언트 외 조명 제어 |
| `CAR_CLIMATE` / `CONTROL_CAR_CLIMATE` | 공조 온도·풍량 |
| `CAR_WINDOWS` | 하차 체크용 창문 위치 읽기 |
| `TTS_SERVICE` | 음성 안내(ON_DEVICE) |

- 개인정보·위치 데이터는 수집하지 않습니다. 외부 전송도 없습니다.

## APK 위치
- 저장소 내 `apk/` 폴더: [`apk/TEAM_PlugRest_release.apk`](apk/TEAM_PlugRest_release.apk) (약 38MB)

## 프로젝트 구조 (요약)
- 단일 Activity + Jetpack Compose. 화면은 대기 / 휴식 / 완료 3개입니다.
- `VehicleGateway`: 실제 Pleos SDK 구현과 Fake 구현으로 나뉩니다. Pleos Vehicle 서비스가 없으면 Fake 구현이 자동으로 선택됩니다.
- `RestSession`: 상태머신으로, 스냅샷 저장·복원과 남은 시간 계산을 맡습니다.
