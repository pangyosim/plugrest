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
