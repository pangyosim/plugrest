# 32. 코드 리뷰 — app/ 첫 빌드 (2026-11-03, 검수B)

대상: `app/src/main/java/ai/pleos/plugrest/**`, `AndroidManifest.xml`, 병합 매니페스트(`build/intermediates/merged_manifest/release/…`), `RestSessionTest`.
기준: PRD v0.5 §4·§5·§6·§9, 앞선 4항목(수용 기준 / 안전 경로 / 권한 / 네트워크·개인정보).
방법: 전 파일 정독(정적 분석). **실행 검증은 하지 않음.** 단위테스트 결과 파일은 5/5 통과(`build/test-results`, 2026-11-02T19:23). 줄 번호는 `app/src/main/java/ai/pleos/plugrest/` 기준.
코드는 수정하지 않았음 — 수정은 개발 세션 담당.

## 요약
| 심각도 | 건수 | 핵심 |
|---|---|---|
| **차단** | 2 | ① 서비스가 시작 직후 스스로 종료 → "휴식 모드 시작"이 동작 안 할 가능성 높음 ② 주행 제한 진입 시 진행 중인 시트 이동·set을 멈추지 않음(STOP 미전송) |
| 높음 | 4 | 시간 기반 시트 복원 과/중복 이동, 기어 N/미확인을 정차로 취급, 리클라인 중 터치 정지 없음, 착좌 미확인을 "빈 좌석"으로 취급 |
| 중간 | 8 | 충전 판정이 PRD와 다름, 실행 중 2시간 만료 없음, Done에서 FGS 무기한, 스레드 안전성, 백그라운드 FGS 시작, 리스너 해제 없음, TTS SDK의 INTERNET, 재안내 조건 |
| 낮음 | 8 | 권한명 확인, PRD 표 누락, 취소 버튼 타이밍, DEMO 기본값, APK 이름, 처리방침 미작성 등 |

---

## 차단

### B1. 서비스가 시작 직후 `stopSelf()` → 세션 시작 코루틴이 취소됨
- 위치: `session/RestService.kt:50-51, 79` (+ `:85-96`)
- 문제: `onCreate`에서 `g.session.phase.collect`를 시작하면 StateFlow가 **현재 값 `Phase.Idle`을 즉시 방출**하고, `Phase.Idle -> { …; stopSelf() }`가 실행된다. 이어지는 `onStartCommand`가 띄운 코루틴은 `readSnapshot()`에서 suspend(Fake 200ms, Pleos는 SDK 호출 10개 × 최대 3초)하는 동안 `onDestroy → scope.cancel()`로 취소되어 `session.start()`에 도달하지 못한다(정적 분석, **Fake AVD에서 1회 재현 확인 요청**). 도달하더라도 서비스가 죽어 있어 카운트다운·TTS·복원이 안 돈다 → PRD §9 "백그라운드에서도 목표 도달 시 TTS·복원" 불충족.
- 수정안: 종료는 **Done→Idle 전이에서만**. 예)
  ```kotlin
  var prev: Phase? = null
  g.session.phase.collect { p -> …; if (p is Phase.Idle && prev != null && prev !is Phase.Idle) stopSelf(); prev = p }
  ```
  또는 `phase.drop(1)`. 그리고 `onStartCommand`에서 받은 `startId`로 `stopSelf(startId)`를 써서 늦게 도착한 start 요청을 죽이지 않게 한다.
- 테스트: Fake AVD에서 시작 탭 → 홈으로 나감 → SOC 패널로 80 도달 → TTS·복원 로그 확인.

### B2. 주행 제한 진입 시 "모든 제어 즉시 중단"이 구현되지 않음 (시트 모터 STOP 미전송)
PRD §4.4·§4.5: 제한 진입(UX 제한 또는 기어≠P) 시 **복원 없이 즉시 모든 제어 중단**. 현재는 phase만 `Done(RESTRICTED)`로 바뀌고 이미 시작된 제어는 계속된다. 주행 규제 정책상 "시트, 차량 설정"은 주행 중 전 지역 ⛔ → 심사 반려·안전 사유.
- (a) `RestService.kt:89-96` — `applyRest()`(조명→공조→시트 3초 리클라인)가 추적되지 않는 `scope.launch`에서 돈다. 도중에 D로 바꿔도 끝까지 실행된다.
- (b) `RestService.kt:44-45` — 브레이크 선제 복원 `launch { restoreSeat(snap) }`도 추적되지 않는다. **브레이크를 밟고 P→D로 바꾸는 바로 그 순간이 이 시나리오**라서 D 상태에서 시트가 1~2초 더 움직인다.
- (c) `RestService.kt:61-66, 70` — 카운트다운이 끝나 `restore()`가 도는 중에 제한 → `Done` → `countdown.cancel()`. 그런데 `PleosVehicleGateway.kt:210-220` `moveBackrest`의 `while … delay(100)`가 취소 예외로 빠져나가면서 **`stopSeatMotion()`이 호출되지 않는다** → 모터에 FORWARD/BACKWARD 명령만 남고 STOP이 안 간다(SDK가 자동 정지하는지 미확인). `onDestroy`의 `scope.cancel()`(`:101-103`)도 같은 문제.
- (d) `MainActivity.kt:60` — "되돌리기"가 Activity의 `rememberCoroutineScope`에서 `restore()`를 실행한다. 제한 진입 시 취소되지 않고, 화면을 벗어나면 (c)와 같이 STOP 없이 끊긴다.
- 수정안:
  1. 제어는 전부 서비스의 **단일 `controlJob`** 으로 실행(applyRest / restore / 브레이크 복원 / 되돌리기). 되돌리기는 `RestService`에 intent(또는 `session.requestOfferedRestore()` → 서비스가 수행)로 위임.
  2. 상태 collect에서 `if (g.session.isDrivingRestricted(s)) { controlJob?.cancel(); g.gateway.stopSeatMotion() }`.
  3. `moveBackrest`를 `try { … } finally { if (seatMoving) withContext(NonCancellable) { stopSeatMotion() } }`로 감싼다.
  4. 방어선 이중화: `PleosVehicleGateway.set()`(`:233`) 첫 줄에 `if (restricted(_state.value)) return false` — 게이트웨이 단에서 제한 중 set 호출 0회 보장(PRD v0.3 수용 기준 "주행 제한 진입 시 Seat set 호출 0회").
- 테스트(1개): `FakeVehicleGateway`로 `applyRest` 진행 중 `setGear(DRIVE)` → 로그에 `STOP`이 찍히고 이후 set 호출이 없음을 assert.

---

## 높음

### H1. 시트 복원이 "3초 FORWARD" 고정 + 중복 실행 → 원위치를 넘어감
- 위치: `vehicle/PleosVehicleGateway.kt:181, 198-201`, `session/RestService.kt:44-45, 64`, `MainActivity.kt:60`
- 문제: 리클라인은 `RECLINE_MS`(3초) 또는 절반(1.5초)이고, 터치로 멈추거나 타임아웃으로 시작에 실패할 수도 있다. 그런데 복원은 **항상 3초 FORWARD**다. 또 브레이크 선제 복원(1회)을 한 뒤에도 카운트다운 `restore()`와 "되돌리기"가 다시 `restoreSeat()`를 호출한다 → 원래보다 앞으로 최대 6초 이동(운전자를 핸들 쪽으로 밀 수 있음, 추정). `snapshot.backrestAngle`은 읽기만 하고 쓰지 않는다.
- 수정안: 게이트웨이에 `reclinedMs`(실제 BACKWARD 이동 시간 누적)를 두고 `restoreSeat`은 **그만큼만 FORWARD 후 0으로 리셋**. 각도 get이 에뮬레이터에서 동작하면 `backrestAngle` 도달 시 STOP으로 교체(주석의 계획 그대로). 세션 측도 `seatRestored`이면 `restore()`에서 시트를 건너뛴다.

### H2. 기어 N / UNKNOWN / 미확인(null)을 "정차"로 취급
- 위치: `session/RestSession.kt:57-58`
- 문제: `s.gear == DRIVE || s.gear == REVERSE`만 제한이다. PRD §4.4는 "기어 ≠ P". N(굴러갈 수 있음)이나 UNKNOWN에서 시작·제어가 허용된다. 일반 AVD나 UX 서비스가 없는 환경에서 Gear 미지원(null)이면 제한 신호가 아예 없다.
- 수정안: `s.uxRestricted || (s.gear != null && s.gear != Gear.PARK)`. `blockReason`의 시작 조건은 `s.gear == Gear.PARK || (s.gear == null && uxAvailable)`. UX 매니저도 없고 기어도 null이면 시작 금지 + "기어 상태를 확인할 수 없어요".

### H3. 리클라인 중 "화면 터치 시 즉시 정지"가 Resting 화면에 없음
- 위치: `ui/Screens.kt:159-183` (RestingScreen), 대비 `:188` (RestoringScreen에만 `pointerInput`)
- 문제: PRD §4.2 "리클라인 중 터치 시 즉시 정지". 리클라인은 Resting 진입 직후 일어나는데, Resting 화면에는 터치 정지도 "시트 이동 중" 표시도 없다.
- 수정안: RestingScreen 루트에 `Modifier.pointerInput(Unit) { detectTapGestures { onTouch() } }`를 넣고 `onTouch = { g.gateway.stopSeatMotion() }`. `s.seatMoving`이면 RestoringScreen처럼 안내 문구 표시.

### H4. 뒷좌석 착좌 "확인 불가"를 "비어 있음"으로 취급 → 무조건 전체 리클라인
- 위치: `vehicle/PleosVehicleGateway.kt:122-130`, `vehicle/VehicleGateway.kt:25`, `session/RestService.kt:90`
- 문제: `rearSeatOccupied`는 리스너 콜백이 와야 true가 된다. 기본값 false, 미지원/권한 실패(OCCUPANCY unsupported)여도 false → 뒷좌석에 사람이 있어도 등받이를 끝까지 젖힐 수 있다(끼임).
- 수정안: `rearSeatOccupied: Boolean?`(null = 미확인). 시작 시 PRD §5대로 `getCurrentSeatOccupancy`를 ROW_2_LEFT에 대해 직접 get(타임아웃 포함). `halfRecline = occupied != false`(미확인이면 절반). 미확인일 때는 화면에 "뒷좌석을 확인할 수 없어 절반만 젖혔어요".

---

## 중간

### M1. 충전 판정이 PRD v0.4와 다름
- 위치: `session/RestSession.kt:54-55`
- 문제: `chargePortConnected != false && (SOC 상승 || chargeSwitchOn == true)`. ① 충전 스위치 ON만으로 "충전 중"이 된다(스위치 = 충전 허용 설정, 30-review R7). ② 포트 미확인(null)도 연결로 본다. PRD는 "포트 연결 AND 최근 60초 SOC 상승, 스위치는 보조".
- 수정안: `s.chargePortConnected == true && socRisingWithin(chargingWindowMs)`. 스위치는 Idle 문구 보조로만 쓴다. Fake는 SOC가 6초마다 오르므로 데모 판정 10초에 영향 없음.

### M2. 실행 중 2시간 만료가 없음 (재시작 시에만 검사)
- 위치: `session/RestSession.kt:76-81`(resume에만 존재), `evaluate` `:108-125`
- 문제: PRD §6 "충전 포트 분리 또는 저장 후 2시간 경과 시 스냅샷·Resting 폐기". 프로세스가 살아 있으면 Resting이 무기한 유지되고, 몇 시간 뒤 포트 분리 시 시트가 움직인다.
- 수정안: `evaluate`의 Resting/Done 분기에서 `now() - startedAt > MAX_SESSION_MS`면 **시트 이동 없이** `reset()` + 스냅샷 폐기(새 `EndReason.EXPIRED`). Phase.Done에도 `endedAt`을 넣어 같은 규칙 적용.

### M3. Done 상태에서 FGS·리스너가 무기한 유지
- 위치: `session/RestService.kt:69-78`
- 문제: Idle(사용자 "확인")에서만 `stopSelf`. 사용자가 앱을 다시 열지 않으면 상시 알림과 서비스가 남는다(리소스 가이드 "백그라운드 최소화").
- 수정안: 재안내 3회가 끝나면(또는 Done 후 5분, 또는 포트 분리 시) `session.reset()` → Idle → 서비스 종료. 하차 체크는 앱을 다시 열면 Idle 화면에서 볼 수 있게 하거나 생략.

### M4. RestSession이 여러 스레드에서 동시에 변경·조회됨
- 위치: `session/RestSession.kt:37, 41-51, 97-105` / 호출부 `RestService.kt:41-43`(Dispatchers.Default), `MainActivity.kt:50, 55-56`(Main)
- 문제: `history`(ArrayDeque)를 Default 스레드에서 `addLast/removeFirst`하는 동안 Main에서 `filter`(blockReason → isCharging, remainingMinutes)로 읽는다 → `ConcurrentModificationException` 가능. `evaluate`도 Activity와 Service 양쪽에서 호출되고 `wasRestricted`를 공유한다.
- 수정안: 세션 접근을 한 디스패처로 한정(서비스 scope를 `Dispatchers.Main.immediate`로; 차량 상태 업데이트는 가볍다). Activity의 `evaluate` 호출(`MainActivity.kt:50`)은 삭제하고 서비스만 평가. 최소한 `history` 접근을 `synchronized(history)`로.

### M5. Application.onCreate에서 Foreground Service 시작
- 위치: `App.kt:30-31`
- 문제: 저장된 세션이 있으면 프로세스 시작 시마다 `startForegroundService`. Android 12+는 앱이 백그라운드일 때 FGS 시작을 막는다(`ForegroundServiceStartNotAllowedException`) → 프로세스가 브로드캐스트 등으로 깨어날 때 크래시 가능(추정, AAOS 예외 정책 미확인).
- 수정안: `try/catch`로 감싸고 실패 시 MainActivity `onStart`에서 재시도. START_STICKY 재시작 경로는 서비스가 이미 생성 중이므로 `session.resume`만 하면 된다.

### M6. 리스너 해제·SDK release가 어디서도 호출되지 않음
- 위치: `vehicle/PleosVehicleGateway.kt:73-77`(`stop()` 미사용), `:89-131`(unregister 없음), `App.kt:29`, `Narrator.release()` 미사용
- 문제: 앱 프로세스가 살아 있는 동안 리스너 9개 이상과 Car 연결이 상시 유지된다. Idle·백그라운드에서도 마찬가지 → 리소스 가이드 위반 소지. 샘플 패턴(`unregisterXxx`, `Vehicle.release()` in onDestroy)과도 다르다.
- 수정안: 리스너 등록과 해제를 "Activity 표시 중 OR 세션 활성" 구간으로 한정. `stop()`에 `unregister*` 추가 + `car.disconnect()` + `release()`. Idle로 돌아가 서비스가 끝나고 Activity가 `onStop`이면 `gateway.stop()`.

### M7. "네트워크 0건"은 앱 코드 기준으로만 성립
- 위치: 병합 매니페스트 `:44` — `android.permission.INTERNET` ← `[ai.pleos.playground:TextToSpeech:3.3.1]`(주석 "CCG Validation 체크를 위한 권한")
- 확인: 앱 코드에는 HTTP·소켓·위치·기기 식별자 사용이 **0건**(grep). 저장은 `SharedPreferences`의 차량 상태값뿐, `allowBackup=false`. 그러나 TTS SDK가 ON_DEVICE 모드에서도 검증 통신을 하는지는 **미확인**.
- 수정안: 에뮬레이터에서 TTS 재생 시 네트워크 로그 확인(`adb shell dumpsys netstats` 또는 프록시). 개인정보처리방침 문구는 "앱은 개인정보를 수집·전송하지 않음. 음성 안내 SDK(Pleos TTS)가 서비스 검증 목적으로 네트워크를 사용할 수 있음(개인정보 미포함)" 수준으로. `tools:node="remove"`로 INTERNET을 빼는 것은 TTS가 깨질 위험이 있어 비권장.

### M8. "목표 도달" 재안내 조건이 너무 넓음
- 위치: `session/RestService.kt:75-77`
- 문제: `reason == TARGET_REACHED`만 본다. ① 카운트다운 중 사용자가 "취소"(=깨어 있음)해도 3회 반복된다. ② Restoring 중 제한 진입으로 Done(TARGET_REACHED, restored=false)가 되면 **주행 중에** 30초마다 음성이 나온다.
- 수정안: `p.restored && !isDrivingRestricted(state)`일 때만 재안내. 반복 루프 안에서도 매번 제한 여부를 확인하고, 사용자 입력이 있으면 중단.

---

## 낮음
- **L1** `AndroidManifest.xml:17` `android.car.permission.CAR_UX_RESTRICTIONS` — 실제 존재하는 권한인지 확인 필요. `CarUxRestrictionsManager.registerListener`는 권한이 필요 없는 것으로 알고 있음(추정). 없으면 삭제(최소 권한 원칙, 심사 가이드).
- **L2** PRD §5 표에 `CAR_WINDOWS`가 없음("위와 동일"로 뭉뚱그림). 매니페스트(`:13`)가 맞고 **PRD를 고칠 것**. 제안서 §5 권한 목록에도 포함.
- **L3** `ui/Screens.kt:186-199` — 카운트다운이 0이 되어 복원이 진행 중일 때도 "취소" 버튼이 보이고, 누르면 `Done(restored=false)`인데 실제로는 일부가 이미 복원된 상태 → 문구("지금 상태 그대로") 불일치. 0초 이후 버튼 숨김 또는 "되돌리는 중…".
- **L4** `build.gradle.kts:18` `DEMO` 기본값이 true → **제출용 release APK가 데모 모드**(계산 중 20초, 판정 10초). README "테스트 환경 및 필요한 설정"에 명시하거나 release 기본값을 false로. `:24` release를 디버그 키로 서명 — 공모전에는 OK, 주석대로 배포 시 교체.
- **L5** APK 경로·파일명이 규칙과 다름: `build/outputs/apk/release/PlugRest-release.apk` → 제출 시 저장소 `apk/팀명_앱이름_release.apk`. 아직 `apk/` 폴더가 없음.
- **L6** 개인정보처리방침 1장 미작성(`docs/` 아래에서 찾지 못함) — PRD §9 항목. M7 문구 반영.
- **L7** `Graph`(`App.kt:19`) — Pleos 서비스가 안 잡히면 **조용히 Fake로 전환**. PT 현장에서 에뮬레이터 바인딩이 실패해도 눈치채기 어려움. Fake일 때 화면 상단에 "Fake 차량" 배지 표시(FakePanel만으로는 접힌 상태에서 약함).
- **L8** `vehicle/Narrator.kt:22-29` — Pleos TTS 초기화 실패 시 안내 없이 무음. 실패하면 `AndroidNarrator`로 폴백하거나 화면에 "음성 안내 불가" 표시. TTS 오디오 포커스 처리 여부는 SDK 동작 확인 필요.

---

## 매니페스트 권한 vs PRD §5
| 권한 | 매니페스트 | PRD §5 | 판정 |
|---|---|---|---|
| CAR_ENERGY | ○ | ○ | ○ |
| **CONTROL_CAR_ENERGY** | **없음** | 없음(충전 한도 변경 안 함) | ○ (`setEvChargePercentLimit` 호출 0건도 확인) |
| CAR_DRIVE | ○ | ○ | ○ |
| CAR_SEATS / CONTROL_CAR_SEATS | ○ / ○ | ○ / ○ | ○ |
| CAR_LIGHTS / CONTROL_CAR_LIGHTS | ○ / ○ | ○ / ○ (앰비언트 = CAR_LIGHTS 분리) | ○ — 앰비언트 setter는 CAR_LIGHTS, `setCabinLightSwitch`는 CONTROL_CAR_LIGHTS |
| CAR_CLIMATE / CONTROL_CAR_CLIMATE | ○ / ○ | ○ / ○ | ○ |
| CAR_WINDOWS | ○ | (표에 없음) | △ PRD 보완(L2) |
| TTS_SERVICE | ○ | ○ | ○ |
| android.car CAR_UX_RESTRICTIONS | ○ | (명시 없음) | △ L1 |
| FOREGROUND_SERVICE(+SPECIAL_USE), POST_NOTIFICATIONS | ○ | §6 FGS | ○ |
| (병합) VEHICLE_BINDING, INTERNET, `<queries>` VEHICLE_START | SDK 병합 | — | ○ (`<queries>` 병합으로 `isAvailable`의 패키지 가시성 문제 없음) / INTERNET은 M7 |
| DO(distractionOptimized) 선언 | 없음 | v0.5 미선언 | ○ |

## PRD v0.5 §9 수용 기준 대조
| 기준 | 판정 | 근거 |
|---|---|---|
| Fake·Pleos 에뮬레이터 양쪽에서 플로우 1~4 | **× (위험)** | B1로 시작 자체가 실패할 가능성. 실행 미검증 |
| 미지원 도메인 크래시 없이 건너뜀 | ○ | `get/set`이 예외·타임아웃을 흡수하고 SKIP 코드(권한 오류 포함)에 해당하면 Feature 미지원 표시(`PleosVehicleGateway.kt:224-240`). 단 타임아웃은 미지원으로 표시되지 않음 |
| 기어 D에서 제어 버튼 비활성 | △ | D/R은 비활성. N·UNKNOWN·null은 활성(H2). DO 미선언이라 주행 중에는 플랫폼이 앱을 가림 |
| 프로세스 종료 후 Resting·스냅샷 복원 | △ | 저장·복구는 구현됨(`SessionStore`, `App.kt:30`). FGS 백그라운드 시작 리스크(M5), 실행 중 만료 없음(M2) |
| 백그라운드에서 목표 도달 시 TTS·복원 | **×** | 서비스 구조는 맞지만 B1 |
| 주행 제한 진입 시 복원 없이 제어 중단 | **×** | phase 전이만 있음. 진행 중 제어·모터를 멈추지 않음(B2) |
| 개인정보처리방침 1장 + 카테고리 Mobility Services | × | 문서 없음(L6). 카테고리는 마켓 등록 메타라 코드 무관 |
| release APK, `apk/팀명_앱이름_release.apk` | △ | release APK는 빌드됨. 경로·이름 미준수(L5), DEMO 기본 ON(L4) |

## 그 밖에 OK인 항목
- `setEvChargePercentLimit` 호출 0건(PRD v0.4) / 목표 칩 80·90만(100% 삭제) / 외기 유입 기본(`setHvacRecirculation(false)`) / TTS 문구가 PRD v0.5와 일치 / 10초 카운트다운 + 취소 → 복원 없이 Done / 주행 후 재정차 시 1회 "되돌릴까요?" 제안 / 2시간 초과 세션 재개 거부(재시작 경로) / Idle에서만 10초 폴링 / Resting 화면 밝기 0.1 / `allowBackup=false` / 위치·계정·서버 없음 / ErrorCode 난독화 우회에 주석으로 교체 계획 명시.
- 단위테스트 5개 통과. **추가 권장 1개**: B2 회귀 테스트(제한 진입 시 STOP + set 0회).

## 수정 우선순위 (개발 세션용)
1. B1 (5줄) → Fake AVD로 시작·백그라운드 도달 재현 확인
2. B2 (controlJob + finally STOP + set 게이트) + 회귀 테스트 1개
3. H1·H2·H3·H4
4. M1·M4·M2·M3 → 나머지

---

## 재검 (2026-11-03, 개발 수정분)
방법: 변경 파일 재정독(RestService, RestSession, VehicleGateway, PleosVehicleGateway, App, MainActivity, FakeVehicleGatewayTest). 실행은 개발 세션의 Fake AVD 재현 보고(04:39~04:40)를 근거로 함. 테스트 결과 파일 8/8 통과 확인(RestSessionTest 6, FakeVehicleGatewayTest 2).

### 판정
| ID | 결과 | 확인 내용 |
|---|---|---|
| B1 | **해소** | `RestService.kt:36, 67, 98` — `sessionSeen` 이후의 Idle에서만 `stopSelf`. START_STICKY 재생성 경로(Graph가 먼저 Resting으로 resume)에서도 정상 |
| B2 | **해소** | 단일 `control` Job(`:35-46`), 제한 시 `abortControl()`(`:57`), `moveBackrest` finally + NonCancellable STOP(`PleosVehicleGateway.kt:246-255`), `set()` 게이트(`:270-274`, STOP은 게이트를 거치지 않아 제한 중에도 전송됨 — 맞음), 되돌리기는 서비스 경로(`MainActivity.kt:56`). 브레이크 복원도 `!drivingRestricted` 조건 + control Job |
| H1 | 해소(잔여 1건 → N1) | `reclinedMs`만큼만 FORWARD, 멱등 테스트 있음 |
| H2 | 해소 | N 포함 제한. UNKNOWN/null은 판정 보류 + UX 제한 1순위 — PRD와 합치(UX 매니저 없는 일반 AVD는 Fake가 기어를 항상 줌) |
| H3 | 해소 | `RestingScreen` onTouch → stopSeatMotion |
| H4 | 해소(잔여 1건 → N2) | `Boolean?`, null이면 절반, refresh에서 직접 get |
| M1 충전 판정 | **PM 결정 사항으로 이관** | 개발 근거(SOC 갱신 1%/분이라 60초 창은 깜빡임)는 타당. 다만 현재식은 포트 `null`+스위치 ON만으로 "충전 중" → 충전 완료·예약 충전 대기 상태에서 시작 가능, 그러면 SOC가 안 올라 2시간 만료까지 Resting. 절충안: `chargePortConnected == true && (socRisingWithin(max(window, 3분)) \|\| chargeSwitchOn == true)` — 최소한 포트 null은 불인정 |
| M2 2시간 | 해소(PRD 문구 차이) | 만료 시 "폐기"가 아니라 `Restoring(USER)`로 10초 예고 후 복원. 같은 주차 중 스냅샷이므로 안전 측면에선 이쪽이 낫다고 봄 → **PRD §6 문구를 구현에 맞춰 수정 권장** |
| M3 Done 타임아웃 | 보류(PM 결정) | 개발 보고대로 |
| M4 스레드 | **부분** → N3 | 변경 메서드만 `@Synchronized` |
| M5 | 해소 | `App.kt:32` try/catch |
| M6 | **부분** → N4 | unregister 구현됐으나 `gateway.stop()` 호출부 0건 |
| M7 | 변동 없음 | 처리방침 문구 이슈(문서 작업) |
| M8 | 해소 | `restored && !drivingRestricted`, 반복 중에도 매번 확인 |
| L1 | 해소 | `CAR_UX_RESTRICTIONS` 삭제 |
| L4 | 해소(결정) | release DEMO ON + 화면 "· 시간 단축 데모" 라벨(`Screens.kt:288`) |
| L5 | 해소 | `apk/TEAM_PlugRest_release.apk` — **팀명 확정 후 파일명 교체 필요**(규칙 `팀명_앱이름_release.apk`) |
| L7 | 해소(대체) | 하단 "Fake 차량 패널" 바 |

### 새로 발견 / 잔여
- **N1 (중간)** `PleosVehicleGateway.kt:225-228` — `restoreSeat`가 이동 **전에** `reclinedMs = 0`. 복원 도중 취소되면(주행 제한 진입, 또는 `control()`이 새 Job으로 이전 Job을 취소 — 예: 브레이크 선제 복원 중 카운트다운 복원 시작) 남은 거리가 사라져 이후 "되돌리기"·카운트다운 복원이 **시트를 안 움직임**(등받이가 젖혀진 채로 끝남). 수정: 0으로 미리 두지 말고 finally에서 FORWARD면 `reclinedMs = (reclinedMs - moved).coerceAtLeast(0)`. Fake(`FakeVehicleGateway`)도 같은 규칙. 테스트 1개: FORWARD 중 cancel → 다시 restoreSeat 시 남은 시간만 FORWARD.
- **N2 (낮음)** `PleosVehicleGateway.kt:95-96` — ROW_2_LEFT 읽기 실패 + ROW_2_RIGHT=false면 `rearOccupied`가 비어 있지 않아 `false`(전체 리클라인). 운전석 뒤(ROW_2_LEFT)가 미확인이면 null로: `rearSeatOccupied = if (SeatValue.ROW_2_LEFT !in rearOccupied) null else rearOccupied.values.any { it }`.
- **N3 (중간)** `RestSession.kt:49-53, 88-114` — 읽기 메서드(`socRisingWithin`, `slopePctPerMin` ← `remainingMinutes`/`estimateMinutes`/`blockReason`)는 Main 스레드에서 `history.filter`를 돌리는데 동기화가 없음 → `onSoc`(Default)와 겹치면 `ConcurrentModificationException`으로 크래시 가능. 수정: 읽기 메서드에도 `@Synchronized`(비용 무시 가능).
- **N4 (중간)** `gateway.stop()`·`narrator.release()` 호출부가 여전히 없음 → 리스너 9개+Car 연결이 프로세스 수명 내내 유지. 수정(최소): MainActivity `onStop`에서 `if (phase is Idle) gateway.stop()`, `onStart`에서 `gateway.start()`(start는 재호출 안전하게: 이미 연결이면 return). 서비스 Idle 종료 시에도 Activity가 안 보이면 stop.
- **N5 (낮음)** silent Restoring(되돌리기)에도 `RestoringScreen`이 "10초 후 시트를 원래대로"·카운트 0·"취소" 버튼을 보여줌. silent면 "되돌리는 중…"만 표시.
- **N6 (낮음)** 세션 시작 `control {}` 안에서 예외가 나면 SupervisorJob + 핸들러 없음 → 앱 크래시, 시작에 실패해도 FGS가 "휴식 모드 진행 중" 알림으로 남음(sessionSeen=false라 종료 안 됨). `CoroutineExceptionHandler` 1개 + 실패 시 `stopSelf()`.
- 참고: `FakeVehicleGatewayTest.cancelDuringRecline…`은 STOP을 테스트에서 직접 호출(`:21`)하므로 **Pleos 게이트웨이의 finally STOP 경로는 단위테스트로 검증되지 않음** → Pleos 에뮬레이터에서 "리클라인 중 Gear D" 수동 확인 1회(Virtual Console Seat 값이 멈추는지).

### 결론
**차단 0건.** 2차 PT·제출 기준으로 진행 가능. 제출 전 권장: N1·N3(크래시/미복원), N4. PM 결정 필요: M1 충전 판정식, M3 Done 자동 종료, PRD §6 만료 문구.

---

## 3차 재검 (2026-11-03, N1~N6 + PM 후속 결정 + 개인정보처리방침 대조)
테스트 결과 파일: RestSessionTest 9 + FakeVehicleGatewayTest 3 = **12/12 통과**(2026-11-02T23:12).

### 코드
| ID | 결과 | 확인 |
|---|---|---|
| N1 | 해소 | `PleosVehicleGateway.kt:229-231, 253-256` — 이동 전에 0으로 만들지 않고 finally에서 BACKWARD +moved / FORWARD −moved(0 하한). Fake `:93, 113-116` 동일. 테스트 추가 |
| N2 | 해소 | `rearOccupancy()` `:286-287` — ROW_2_LEFT 미확인이면 null |
| N3 | 해소 | 읽기 메서드 `socRisingWithin`·`isCharging`·`blockReason`·`remainingMinutes`에 `@Synchronized`, `slopePctPerMin`은 동기화된 메서드 안에서만 호출 |
| N4 | 해소 | `start()` 멱등(`:67-69`), `MainActivity.kt:28-33` onStart→start / onStop→Idle이면 stop, `RestService.onDestroy`에서 Activity가 안 보이면 stop. narrator 미해제는 프로세스 수명 객체라 수용 |
| N5 | 해소 | `Screens.kt:202-204` silent일 때 "되돌리는 중…"만 표시 |
| N6 | 해소 | `RestService.kt:32-35` CoroutineExceptionHandler → stopSeatMotion·reset·store.clear·stopSelf |
| M1 충전 판정 | 해소(PM 결정 그대로) | `RestSession.kt:65-72` 포트 == true(미지원이면 생략) AND (3분 상승 OR 스위치), 포트 null은 충전 아님, 90초 유지, 분리 시 즉시 해제. 창은 `App.kt` 3분 / 데모 20초 |
| M3 Done 종료 | 해소(PM 결정 그대로) — **단 C1 참고** | 재안내 3회 뒤 reset(`RestService.kt:101`), 포트 분리 시 reset(`RestSession.kt:135`) |

### 새로 발견
- **C1 (중간, PM 결정 필요) — 포트 분리 시 Done 종료가 "주행 후 재정차 시 되돌리기 제안"(PRD v0.5 §4.4)을 사실상 무력화.**
  `RestSession.kt:134-138`: Done에서 `chargePortConnected == false`이면 바로 `reset()`(스냅샷 폐기)한다. 그런데 실제로는 **충전 케이블을 뽑아야 주행할 수 있으므로**, 미복원 상태로 주행하는 모든 현실적 경로에서 포트는 이미 false다. 예: 포트 분리 → Restoring(CHARGING_STOPPED) → 10초 안에 D → Done(restored=false) → 다음 평가에서 포트 false → reset. 따라서 offerRestore는 발생하지 않는다. 단위테스트 `drivingRestriction_…_offersOnceWhenParked`는 **포트 연결 상태로 주행**하는 비현실적인 입력이라 통과한다(`RestSessionTest.kt:16, 64-71`).
  수정안(택1): ① 포트 분리 reset은 `p.restored || snapshot == null`일 때만 하고, 미복원 Done은 제안이 처리될 때까지(또는 2시간 만료까지) 유지 → 서비스·알림도 그동안 유지됨. ② 제안 기능을 PRD에서 삭제하고 Done(restored=false) 화면 문구로 대체("시트를 직접 조정해 주세요"). 검수 권장은 ①. 테스트는 `charging.copy(gear = DRIVE, chargePortConnected = false)`로 바꿔 한 번 더.
- **C2 (낮음)** `PleosVehicleGateway.stop()`은 `vehicle.release()`를 동기로 호출하는데, 직전 `refresh()`가 띄운 `scope.launch`가 아직 이전 `vehicle`로 get을 돌고 있을 수 있음(Activity onStop 직후). 크래시 가능성은 낮지만(get이 예외를 흡수) `stop()`에서 진행 중인 refresh Job을 취소하면 깔끔함.

### 개인정보처리방침(docs/submission/privacy-policy.md) ↔ 코드 대조
| 방침 문장 | 코드 | 판정 |
|---|---|---|
| §1 계정·위치·광고 ID·분석·VIN 수집 없음 | 해당 API·권한·라이브러리 0건(`getVINInfo` 미호출, 위치 권한 없음) | ○ |
| §2 처리 항목 표 | SOC·포트·기어·브레이크·UX 제한·시트(등받이/통풍/열선)·조명·공조·착좌(ROW_2)·창문 = 일치. **충전 스위치 상태(`getEvChargeSwitchState`)가 표에 없음**, 공조 **전원·내/외기** 설정도 스냅샷 대상인데 "온도·풍량"만 적힘 | △ 표에 "충전 스위치 상태", "공조 전원·내외기"를 추가 |
| §2 착좌는 여부만, 식별 안 함 | Boolean만 사용 | ○ |
| §3 스냅샷 + 목표·시작 시각만 저장 | `SessionStore`는 그 외에 `seatMode`, `halfRecline`도 저장 | △ "휴식 설정(시트 모드)"을 문장에 추가 |
| §3 종료 시 즉시 삭제 | Done 진입 시 `store.clear()`(`RestService.kt:92`), 예외 시에도 clear | ○ (Restoring 10초 동안은 남아 있음 — 표현상 허용 범위) |
| §3 2시간 후 다음 실행 때 삭제 | `App.kt` resume 실패 시 `store.clear()` | ○ |
| §3 백업 제외 | `allowBackup="false"` | ○ |
| **§4 "앱이 외부 서버와 통신하지 않습니다" / "앱 기능에는 이 권한(INTERNET)을 사용하지 않습니다"** | 앱 코드 네트워크 0건은 맞음. 그러나 INTERNET은 TTS SDK가 "CCG Validation" 목적으로 선언한 것이라 **SDK가 실제로 통신하는지는 미확인**. "사용하지 않는다"는 단정은 반증 리스크 | **× 문구 수정**: "앱 자체는 외부로 데이터를 보내지 않습니다. 음성 안내 SDK(Pleos TextToSpeech)는 서비스 검증을 위해 네트워크를 사용할 수 있으며, 이 앱은 그 과정에 차량 정보나 개인정보를 넘기지 않습니다." — 에뮬레이터 트래픽 확인 후 확정 |
| §6 종료 버튼·데이터 삭제 | 화면 "종료" 있음 | ○ |
| §7·시행일 | 플레이스홀더 `{…}` | 제출 전 채울 것 |
| 로그 | `Log.i("Narrator", text)`는 안내 문구만, 차량 상태 경고 로그에 개인정보 없음 | ○ |

### 결론
**차단 0건.** N1~N6 모두 해소. 남은 것: **C1(PM 결정)**, 처리방침 §2·§3 보완과 §4 문구 수정, C2(선택). 에뮬레이터 수동 확인(리클라인 중 D → Seat 정지)은 사용자 체크리스트(05 §11)로 이관된 것을 확인함.

---

## 4차 확인 (2026-11-03, C1·열선 제거)
테스트 결과 파일: RestSessionTest 10 + FakeVehicleGatewayTest 3 = **13/13 통과**(2026-11-02T23:28).

| 항목 | 결과 | 확인 |
|---|---|---|
| C1 | 해소 | `RestSession.evaluate` Done 분기: `포트 false && (restored \|\| snapshot == null)`일 때만 reset, 미복원 Done은 `sessionStartedAt` 기준 2시간에 reset. 테스트가 현실 순서(포트 분리 → Restoring 중 D → Done → 포트 빠진 채 P → 제안 → 되돌리기 → 포트 분리로 Idle)로 교체됨 + 만료 테스트 추가 |
| C2 | 해소 | (3차 이후 유지) |
| 열선 제거 | 해소 | `SeatMode { NONE, VENT }`, `seatHeat`·`SEAT_HEAT`·Fake 분기·"heat" 키 grep 0건. 남은 것은 주석 1줄(`PleosVehicleGateway.kt:204` "통풍/열선") — 정리 권장(낮음) |
| SessionStore 방어 | 해소 | `runCatching { SeatMode.valueOf(…) }.getOrDefault(NONE)` |
| 처리방침 "열선" | 해소 | 0건 |

### 새로 발견
- **D1 (중간)** C1 변경으로 **"취소" 경로가 바뀜**. `cancelRestore()`는 `Done(restored=false)`만 만들고 스냅샷을 남긴다. 그래서 사용자가 카운트다운 중 "취소"(= "지금 상태 그대로 끝내기", PRD v0.4) → 케이블 분리 → 주행 → 재정차하면 **원치 않은 "되돌릴까요?" 제안 + TTS**가 나오고, 그동안 FGS도 유지된다(최대 2시간). C1 이전에는 포트 분리로 reset되어 이 문제가 없었다.
  수정안(1줄): `cancelRestore()`에서 `snapshot = null` — 사용자가 복원을 명시적으로 거절했으므로 되돌릴 대상이 없음 → 포트 분리 시 reset, 제안 없음. 테스트: `cancelDuringCountdown_leavesCabinAsIs`에 "포트 분리 → D → P → Idle, offerRestore 없음" 3줄 추가.
- **D2 (낮음)** Done의 2시간 만료는 `evaluate`가 불릴 때(차량 상태 변화)만 검사됨. 시동 OFF로 상태 변화가 없으면 Done과 FGS가 2시간을 넘겨 남을 수 있음. 서비스의 Done 분기에 `delay(남은 시간)` 후 `session.evaluate(state.value)` 1회로 충분.

### 결론
차단 0건. D1은 제출 전 수정 권장(1줄 + 테스트 3줄), D2·주석은 선택.

---

## 5차 확인 (2026-11-03, D1·D2)
테스트 결과 파일 **13/13 통과**(2026-11-02T23:30).

| 항목 | 결과 | 확인 |
|---|---|---|
| D1 취소 후 원치 않는 제안 | 해소 | `cancelRestore()`에서 `snapshot = null` → 포트 분리 시 reset, 제안 없음. 테스트에 "포트 분리 → D → P → Idle" 추가 |
| D2 상태 변화 없는 2시간 만료 | 해소 | `sessionRemainingMs()` + 서비스 Done(미복원) 분기의 1회 `expiry` 타이머, Idle 전이 시 cancel |
| 주석 | 해소 | `PleosVehicleGateway.kt:204` "통풍(칩 선택)" |

참고(조치 불필요): 미복원 Done → 되돌리기 → Done(restored)로 바뀌어도 `expiry`는 남아 2시간 시점에 `evaluate` 1회를 호출할 뿐(그때 Done이면 reset, 아니면 무동작) — 무해.

**결론: 코드 리뷰 잔여 0건.** 남은 확인은 Pleos 에뮬레이터 수동 체크(리클라인 중 D → Seat 정지, 05 §11)와 33-submission-check의 X1(빌드 주석)·`.gitignore` 항목.

---

## 6차 확인 (2026-11-03, capability 사전 확인 + clamp)
테스트 결과 파일 **14/14 통과**(2026-11-02T23:39). 대상: `PleosVehicleGateway.kt:51-110, 226, 246`, `VehicleGateway.kt:37`, `FakeVehicleGatewayTest.kt:55-60`.

| 확인 | 결과 |
|---|---|
| `start()` 끝에서 `checkCapabilities()` 1회(비동기) — Seat 등받이 이동, 앰비언트 밝기(운전석 AreaBounds → `ambientRange`), 앰비언트 색 | ○ |
| WRITE/READ_WRITE 아님 또는 운전석 영역 없음 → `unsupported` 추가 → `set()` 게이트가 건너뜀 | ○ 기존 onFailed 경로도 유지(이중) |
| `SeatArea` 비교를 `values.contentEquals`로 | ○ (data class가 아니면 `==`는 참조 비교라 맞는 처리) |
| check 호출이 실패하거나 타임아웃이면 | ○ `get()`이 null → 판정 보류, SKIP 코드면 `fail()`이 미지원 표시 — 보수적 |
| `clampTo` 순수 함수 + 테스트 4케이스 | ○ |
| 시작 직후 check가 끝나기 전에 휴식 시작 | ○ 무해(범위 null → clamp 안 함, set 실패 시 기존대로 건너뜀) |

### 새로 발견 (모두 낮음)
- **E1** `VehicleGateway.kt:37` `clampTo` — 차량이 `minValue > maxValue`(잘못된 bounds)를 주면 `IntRange`가 비어 `coerceIn`이 `IllegalArgumentException`("Cannot coerce value to an empty range") → applyRest가 control Job 안에서 예외 → N6 핸들러가 세션을 reset·종료(크래시는 아니지만 휴식 시작 실패). 수정: `range?.takeIf { !it.isEmpty() }?.let { value.coerceIn(it) } ?: value` + 테스트 1줄 `assertEquals(20, clampTo(20, 50..10))`.
- **E2** `PleosVehicleGateway.kt:104-106` — **색** capability가 쓰기 불가면 `Feature.AMBIENT` 전체를 미지원 처리 → 밝기 조절까지 막힘. 색만 막힌 차종에서 과잉 차단. 선택: `Feature.AMBIENT_COLOR` 분리, 또는 현행 유지(보수적이라 안전 문제 없음).
- **E3** `start()`가 Activity `onStart`마다 불리므로 check 3건도 매번 재호출. 비용 미미하나 결과는 캐시돼 있으니 `ambientRange != null`이면 생략 가능(선택).

### 문서 정합 (기획)
docx §5·PRD §5·PT `:152`는 "check…Capabilities 사전 확인을 **추가할 계획/예정**"으로 되어 있음 → 이제 **등받이·앰비언트는 구현됨**. 과소 기술이라 사실 오류는 아니지만 정확하게: "등받이·앰비언트 조명은 시작 시 check…Capabilities로 쓰기 가능 여부와 밝기 허용 범위를 확인하고(범위 밖 값은 맞춰 보냄), 나머지 도메인은 set 실패 시 건너뛴다. (에뮬레이터 실측 전)". PT 슬라이드 6의 "check → set → register" 강조도 이 범위라면 사용 가능.

**결론: 차단 0건.** E1은 1줄 수정 권장, E2·E3 선택.

**6차 후속 (08:41):** E1 해소 — `VehicleGateway.kt:37` 빈 범위 무시 + 테스트(`clampTo(20, 50..10) == 20`), 14/14 통과(23:41). E2·E3는 PM 결정으로 미반영(보수적 처리 유지, 안전 문제 없음). **코드 리뷰 잔여 0건.**

---

## 7차 — 충전 속도 학습 (PRD §4.6) (2026-11-03)

대상: `ChargeRateMemory.kt`(신규), `RestSession.kt`(learn·etaMinutes), `SessionStore.kt`(charge_rate prefs), `RestService.kt:123`(시작 TTS), `App.kt:27`, `MainActivity.kt:59-60`, `ui/Screens.kt:135-190`, 테스트 2+2줄. **검수 측 재실행 16/16 통과**(ChargeRateMemoryTest 2, RestSessionTest 10, FakeVehicleGatewayTest 4).

이상 없음: EMA·n 누적, ≤0 기울기 무시, 한쪽 버킷만 있을 때 테이퍼 추정, `clear()`가 세션 prefs와 분리됨, `allowBackup=false` 유지, learn이 Resting 종료 경로(복원 시작·주행 제한)에서만 1회 호출됨, 프로세스 재생성 후엔 history가 비어 학습 안 함(안전), 개인정보처리방침 §3·영문 요약에 학습값 보관이 이미 반영됨.

| ID | 등급 | 내용 | 제안 |
|---|---|---|---|
| F1 | 중 | **"실측" 태그가 실측이 아닐 때도 붙음.** `Screens.kt:188` 조건이 `remaining != null`뿐인데, `remainingMinutes`는 기울기가 없으면(SOC가 5분간 안 오름·완속·포트만 연결) `FALLBACK_PCT_PER_MIN`(1.08%/분 가정)으로 값을 낸다. 데모 빌드만이 아니라 **실차 완속에서도** 가정값에 "실측"이 붙는다. | 실측 여부를 UI에 넘긴다(예: `remainingMinutes`가 기울기 출처를 함께 반환, 또는 `hasMeasuredSlope()` 노출). 이 경우 태그를 숨긴다. |
| F2 | 하 | **학습값이 있어도 남은 시간 계산은 고정 가정값을 씀.** 실측 기울기가 없을 때 `RestSession.kt` `remainingMinutes`는 학습 EMA가 있어도 `FALLBACK`만 쓴다. 계산 중 끝 ↔ 실측 확보 사이(또는 완속)에 화면 숫자가 학습값 보조 줄보다 엉뚱해질 수 있다. | 폴백을 `rates.estimateMinutes(soc,target)` → 없으면 `FALLBACK` 순으로. 한 줄. |
| F3 | 하 | **튀는 기울기도 영구 학습.** 데모 패널 SOC 슬라이더(`FakeVehicleGateway.setSoc`)로 2분 넘게 걸쳐 크게 끌어올리면 10%/분 이상이 기록되고, α=0.3이라 몇 세션 동안 "보통 약 5분"이 남는다. PT 리허설 기기에서 그대로 보일 수 있다. 실차 SOC 점프에도 같은 영향. | `record`에서 비현실 값 무시(예: `> 5.0`%/분 ≈ 350kW/77kWh 초과). 한 줄. |
| F4 | 참고 | 학습 기울기 = 세션 **마지막 5분** 창. 목표(80) 직전 구간이라 ≤80 버킷이 실제 평균보다 느리게(예상이 길게) 잡힌다. 보수적 방향이라 안전. 80 걸침 혼합(개발 보고 한계)과 같이 실데이터로 교정할 항목. | 미반영 가능. |

**판정:** F1은 사용자에게 사실과 다른 표시라 반영 권장(데모 빌드 20초 케이스도 같은 수정으로 해소). F2·F3은 각각 한 줄이라 함께 권장. F4는 보류 가능.

**7차 PM 결정:** F1~F3 채택, F4 보류. 개발이 데모 학습 구간 40초 단축(실차 2분 유지)과 함께 반영 예정 → 8차 확인 항목: 데모 단축이 실차(release) 빌드 동작에 영향 없음.

## 8차 — 7차 반영 + 데모 학습 40초 + 런처 아이콘 (2026-11-03)

검수 재실행 16/16 통과.

| 항목 | 결과 | 근거 |
|---|---|---|
| F1 실측 태그 | 해소 | `RestSession.hasMeasuredSlope()`(:121) → `MainActivity.kt:60` → `Screens.kt:188` `&& measured`. 스크린샷 08(계산 중·학습 보조 줄·태그 없음), 09(실측 태그) 확인 |
| F2 학습값 폴백 | 해소 | `RestSession.kt:113` 실측 → `rates.estimateMinutes` → FALLBACK. fullFlow assert 추가 |
| F3 상한 | 해소 | `ChargeRateMemory.kt:24` `> MAX_PCT_PER_MIN(5.0)` 무시, 테스트 `record(12.0)` |
| **데모 40초가 실차 빌드에 영향 없음 (PM 지정)** | **확인** | `minSlopeSpanMs` 기본값 2분(`RestSession.kt:37`), 데모 값은 `App.kt` 한 곳에서만 `if (BuildConfig.DEMO) 40_000L else 2*60_000L` — 기존 `calculatingMs`·`chargingWindowMs`와 같은 패턴. `-Pdemo=false`면 BuildConfig.DEMO=false 상수 → 2분. 단위테스트는 `RestSession(now=…)` 기본값(=실차 값)으로 돌아 실차 경로가 테스트됨. 같은 상수를 다른 곳에서 쓰는 코드 없음(구 `MIN_SLOPE_SPAN_MIN` 제거) |
| 런처 아이콘 | 이상 없음 | adaptive-icon(bg #16233F + fg mipmap), 밀도별 108~432px 정확, Manifest `@mipmap/ic_launcher`, 알림 small icon은 `drawable/ic_launcher`(벡터) 유지 — 타입 다른 리소스라 충돌 없음. 스크린샷 10 원형 마스크에서 잘림 없음 |

**참고(결함 아님):**
- G1 (문서) 제출 APK는 DEMO=true(기존 L4 결정)라 **제출본에선 학습 최소 구간도 40초**. README `:51`·`:98`, `docs/submission/readme-draft.md:53`·`:100`, PRD §v0.5 데모 줄은 단축 항목을 "계산 중·충전 판정" 2개만 적고 있음 → "학습(실측 기울기) 2분→40초" 추가 권장(마케팅/기획).
- G2 (데모 한정) 40초 창 + 정수 SOC라 Fake 1%/분이 1.5%/분으로 측정됨(스크린샷 09: 51%→80% "약 20분", 실제 페이스면 30분). 데모 값이 학습에도 들어가지만 상한 이하라 기록됨. 데모 기기 한정, 시연 멘트상 문제 없으면 그대로 두어도 됨.
- G3 `apk/충전쉼표_PlugRest_release.apk` 13:53:56 — 아이콘 xml(13:53:37)보다 뒤라 이번 변경 포함으로 보임.

**8차 판정: 코드 잔여 0건.** 문서 G1만 남음.
