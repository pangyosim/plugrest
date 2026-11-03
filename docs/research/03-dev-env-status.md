# 개발환경 상태 (2026-11-03, 읽기 전용 점검)

## 로컬 머신
- Apple M4 Pro / 24GB RAM / 여유 디스크 45GB → RAM·디스크는 충분. <!-- 검수 정정 2026-11-03 --> **단 문서상 에뮬레이터는 "그래픽 카드가 필수", 최소 RTX 3060 Laptop 6GB / RX 6600M 8GB이며 Apple Silicon 언급 없음** → 구동 여부 미확인(아래 리스크)
- JDK 17 (`/opt/homebrew/opt/openjdk@17`), gradle, Android Studio, Android SDK `~/Library/Android/sdk` (platforms 34/35/36, build-tools 35/36, cmdline-tools, emulator, platform-tools)
- Pleos AVD 없음 (수동 설치 필요, 아래)

## 네트워크 확인 (인증 불필요, 전부 200)
- Maven: `https://nexus-playground.pleos.ai/repository/maven-releases/` — 공개
- 시스템 이미지 update site (공개, 42dot SDK 라이선스 동의 필요):
  - mac arm64: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
  - win/linux: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`

## 사용 가능한 SDK 아티팩트 (ai.pleos.playground, 최신 버전)
| artifact | latest | 비고 |
|---|---|---|
| Vehicle | 2.3.2 | <!-- 검수 정정 2026-11-03 --> 샘플 repo(`libs.versions.toml`)·getting-started = 2.3.1이나 **2.3.1은 Nexus에 없음(404)**. Importing 문서·릴리즈노트·Nexus = 2.3.2 → **2.3.2 사용**. 샘플 그대로 빌드 시 의존성 실패 가능(추정) |
| NaviHelper | 3.0.5 | |
| TextToSpeech | 3.3.1 | ON_DEVICE 모드는 앱 등록 불필요, HYBRID/SERVER는 앱 등록 필요 |
| SpeechToText | 3.3.1 | |
| FusedLocation | 1.1.3 | |
| **LLM** | 2.1.3.2 | 문서 미공개(404). Retrofit/Gson 의존 → 서버 호출형. 검증 전까지 기획에 의존하지 말 것 |

## 샘플앱 스택 (github.com/Pleos-Playground/pleos-sample-vehicle-app, 읽기만 함)
- AGP 8.8.2 / Kotlin 2.0.0 / Compose BOM 2024.04.01 / Material3 / compileSdk 35 / minSdk 23 / JDK 17
- settings.gradle.kts: `FAIL_ON_PROJECT_REPOS`, google(content filter) + mavenCentral + pleos nexus
- 권한은 `protectionLevel=normal` → 런타임 다이얼로그 없음, 매니페스트 선언만
- 패턴: `checkXxxCapabilities` → 범위 확인 → `getCurrentXxx` / `setXxx` / `registerXxx` / `unregisterXxx`, `Vehicle.release()` in onDestroy
- 매니페스트에 문서에 없는 권한도 존재: `CAR_SPEED`, `CAR_POWERTRAIN`, `CAR_MILEAGE`, `READ_ADAS_STATES`, `READ_CAR_STEERING` (문서화 안 된 도메인이 서비스엔 있을 수 있음 — 에뮬레이터에서 확인)

## 사람이 해야 하는 일 (수동)
1. Android Studio → Tools > SDK Manager > SDK Update Sites > + → 위 mac URL 추가
2. SDK Platforms > API 34 > "Pleos Connect" 시스템 이미지 체크(Show Package Details) → 설치 → AS 재시작
3. Device Manager > Create device > Automotive > Pleos Connect v2.1.0 > API 34 > Advanced: Cold boot, RAM 4GB
4. Virtual Console 설정 가이드 따라 연결 (document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide)

## 미확인/리스크
- <!-- 검수 정정 2026-11-03 --> **에뮬레이터 GPU 요구 vs Apple Silicon**: configure-pleos-connect-emulator 문서의 최소 사양이 외장 GPU 기준(RTX 3060 Laptop 6GB / RX 6600M 8GB), Apple Silicon 언급 없음. M4 Pro에서 부팅·Virtual Console 연결을 1일차 최우선 검증. 실패 시 대안: Windows/Linux + 외장 GPU 장비 확보, 또는 FakeVehicleGateway로 일반 AVD 시연(2차 PT는 에뮬레이터 또는 실기기 시연 필수)
- 에이전트의 외부 repo 클론·빌드가 권한 정책으로 차단됨 → 첫 gradle 빌드는 사용자 세션 또는 권한 허용 후 진행
- TTS HYBRID/SERVER "앱 등록"이 Pleos Playground 콘솔 프로젝트 생성을 뜻하는지 확인 필요 (ON_DEVICE로 시작하면 무관)
- 에뮬레이터가 Seat move / Light set / HVAC set을 실제 반영하는지(Virtual Console에 값 보이는지) 1일차에 검증

## Pleos 에뮬레이터 설치 결과 (2026-11-03, PM 실측)
- CLI 설치 성공: `~/.android/repositories.cfg`에 update site 등록 → `sdkmanager "system-images;android-34;pleos_car_emulator;arm64-v8a"` (4.4GB 다운로드, 압축 해제 후 19GB). 장치 정의 "Pleos_Connect_v2_0"이 이미지에 포함됨.
- AVD: `avdmanager create avd -n Pleos_Connect_v2_1 -k ... -d Pleos_Connect_v2_0`, config.ini에 hw.ramSize=4096, fastboot.forceColdBoot=yes.
- **Apple Silicon(M4 Pro) 부팅 성공**: 첫 실행에서 "Boot completed in 12434 ms", GPU 체크 통과(OpenGL ES Translator, Metal).
- 단, 다른 에뮬레이터(Medium_Tablet) + gradle 데몬이 떠 있는 상태(물리 메모리 23G/24G)에서는 행 감지("detected a hanging thread")로 종료됨. **Pleos 에뮬레이터를 띄울 때는 다른 AVD·빌드 데몬을 내린 뒤 단독 실행** 필요.
- 실행: `emulator -avd Pleos_Connect_v2_1 -no-snapshot -gpu host -cores 4 -memory 4096`
