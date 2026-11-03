# Pleos Vehicle 2.3.2 / TextToSpeech 3.3.1 — 실제 API 시그니처 (2026-11-03)

> **출처**: Dokka 페이지는 클라이언트 렌더링이라 curl/WebFetch로 본문이 안 나옴. 대신 **Maven AAR을 직접 내려받아 `javap`로 뽑은 바이너리 시그니처**가 1차 근거이고, 가이드 문서(SSR 되는 페이지)와 샘플앱 소스(raw.githubusercontent)로 파라미터 이름·사용법을 교차 확인함.
> - `https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/Vehicle/2.3.2/Vehicle-2.3.2.aar`
> - `.../TextToSpeech/3.3.1/TextToSpeech-3.3.1.aar`, `.../vehicle-data/1.1.11/vehicle-data-1.1.11.aar`
> - Maven에 존재하는 Vehicle 버전: **2.0.3, 2.3.2 뿐** (문서 기준 2.3.1은 404 → 2.3.2 고정)

## 0. 개발A가 바로 써야 할 요약 (TL;DR)

| 항목 | 결론 |
|---|---|
| 콜백 형태 | 인터페이스가 아니라 **Kotlin 람다**: `onComplete: (T) -> Unit`, `onFailed: (Exception) -> Unit`. set 계열은 둘 다 기본값 있음(생략 가능) |
| 파라미터 이름 | `area`, `value`, `unit`, `onComplete`, `onFailed`, `listener` (Kotlin 메타데이터로 확인, named arg 사용 가능) |
| 리스너 | `interface XxxListener : VehicleListener { fun onXxxUpdated(v: T?); fun onFailed(e: Exception) }` — 값은 **nullable 박싱 타입**(`Integer?`, `Boolean?`) |
| 기어 | **`Vehicle.getGear()` 도메인이 2.3.2에 존재** (문서 2.3.1엔 없음). `getCurrentGearSelectionState { VehicleGear }`, `registerGearSelection(GearSelectionListener)`. 권한 `CAR_DRIVE` |
| 충전 한도 원래값 | **`getEvChargePercentLimit` 없음**(set·check만 존재). 스냅샷에 원래값을 못 넣음 → 대안 §8 |
| VehicleException | 2.3.2 AAR에서 **클래스명이 `ai.pleos.playground.vehicle.callback.exception.a`로 난독화됨**. `e is VehicleException` 컴파일 불가. `e.message`가 `ErrorCode.name`("ERROR_SECURITY_PERMISSION" 등)이므로 `ErrorCode.valueOf(e.message)`로 복원 (§7) |
| Capability | `CapabilityConfig(accessType: AccessType{NONE,READ,WRITE,READ_WRITE})` + `AreaBounds<T>(minValue, maxValue)` 또는 `List<Area>` |
| 라이프사이클 | `Vehicle(context.applicationContext)` → `initialize()`(동기, 예외 throw 가능) → `getXxx()` → ... → `release()` (콜백 끝나기 전 release 금지) |
| TTS | `TextToSpeech(ctx, Mode.ON_DEVICE)` → `initialize(listener)` → `setLocale(Locale.KOREA)` → `speak(text)`. 권한 `pleos.car.permission.TTS_SERVICE`. ON_DEVICE는 registerApp 불필요. **ko-KR 지원** |

## 1. 공통 — Vehicle 진입점 / 권한 / 의존성

```kotlin
// settings.gradle.kts  (FAIL_ON_PROJECT_REPOS)
maven("https://nexus-playground.pleos.ai/repository/maven-releases/")
// app/build.gradle.kts
implementation("ai.pleos.playground:Vehicle:2.3.2")      // transitive: vehicle-aidl 1.1.1, vehicle-data 1.1.11, kotlin-reflect 1.9.25, gson 2.10.1, coroutines-android 1.6.3
implementation("ai.pleos.playground:TextToSpeech:3.3.1") // transitive: universal-access 0.6.0, gson, coroutines
```

```kotlin
package ai.pleos.playground.vehicle
class Vehicle(context: Context) {
    fun initialize()   // 동기. 서비스 바인딩 실패 시 Exception throw (샘플은 try/catch로 감쌈)
    fun release()
    fun getEvBattery(): EvBattery;  fun getSeat(): Seat;  fun getLight(): Light;  fun getHVAC(): HVAC
    fun getWindow(): Window;        fun getBrake(): Brake; fun getGear(): Gear;  fun getCarInfo(): CarInfo
    fun getDrivingMode(): DrivingMode; fun getDoor(): Door; fun getDisplay(); getOdometer(); getSafety(); getSteeringWheel(); getToll(); getTurnSignal(); getWiper(); getTire(); getSideMirror(); getFuel()
}
```
AAR 매니페스트가 자동 병합하는 것: `uses-permission ai.pleos.playground.service.vehicle.VEHICLE_BINDING`, `<queries>` action `ai.pleos.playground.vehicle.VEHICLE_START`, 서비스 최소 버전 2.3.13. **앱 매니페스트에 선언할 것** (protectionLevel normal, 런타임 요청 없음):

```xml
<uses-permission android:name="pleos.car.permission.CAR_ENERGY" />
<uses-permission android:name="pleos.car.permission.CAR_DRIVE" />      <!-- Gear, Brake -->
<uses-permission android:name="pleos.car.permission.CAR_SEATS" />
<uses-permission android:name="pleos.car.permission.CONTROL_CAR_SEATS" />
<uses-permission android:name="pleos.car.permission.CAR_LIGHTS" />
<uses-permission android:name="pleos.car.permission.CONTROL_CAR_LIGHTS" />
<uses-permission android:name="pleos.car.permission.CAR_CLIMATE" />
<uses-permission android:name="pleos.car.permission.CONTROL_CAR_CLIMATE" />
<uses-permission android:name="pleos.car.permission.CAR_WINDOWS" />
<uses-permission android:name="pleos.car.permission.TTS_SERVICE" />
```
바이너리의 `@RequiresCustomPermission` 실측: Gear/Brake=CAR_DRIVE, EvBattery get=CAR_ENERGY(+`getEvBatteryCapacity`만 CAR_INFO), set=CONTROL_CAR_ENERGY, Seat/Light/HVAC/Window 각각 CAR_*/CONTROL_CAR_*. `PleosPermission` 상수 객체(`ai.pleos.playground.vehicle.constant.PleosPermission.CAR_ENERGY` 등)도 있음.

## 2. 공통 모델 타입

```kotlin
// ai.pleos.playground.vehicle.api.model.capability
class CapabilityConfig(val accessType: AccessType = NONE)
enum class AccessType { NONE, READ, WRITE, READ_WRITE }
class AreaBounds<T>(val minValue: T, val maxValue: T)
class HvacTemperatureConfigArray(minCelsius, maxCelsius, incrementCelsius, minFahrenheit, maxFahrenheit, incrementFahrenheit: Float, raw: List<Int>)

// ai.pleos.playground.vehicle.api.model.area
abstract class Area
class SeatArea(vararg values: SeatValue) : Area      // data-class 아님. Map 키로 쓸 때 equals 확인 필요 → 문서 예제는 areaMap[DoorArea(DoorValue.ROW_1_LEFT)] 형태로 조회하므로 equals 구현돼 있다고 가정
class WindowArea(vararg values: WindowValue) : Area
object GlobalArea : Area

// ai.pleos.playground.vehicle.constant.area
enum class SeatValue : AreaValue { ROW_1_LEFT, ROW_1_CENTER, ROW_1_RIGHT, ROW_2_LEFT, ROW_2_CENTER, ROW_2_RIGHT, ROW_3_LEFT, ROW_3_CENTER, ROW_3_RIGHT }
enum class WindowValue : AreaValue { FRONT_WINDSHIELD, REAR_WINDSHIELD, ROW_1_LEFT, ROW_1_RIGHT, ROW_2_LEFT, ROW_2_RIGHT, ROOF_TOP_1, ROOF_TOP_2 }

// ai.pleos.playground.vehicle.constant.control
enum class VehicleGear { GEAR_UNKNOWN, GEAR_NEUTRAL, GEAR_REVERSE, GEAR_PARK, GEAR_DRIVE, GEAR_1..GEAR_9 }
enum class MoveControl { BACKWARD, STOP, FORWARD }
enum class LightSwitch { OFF, ON, DAYTIME_RUNNING, AUTOMATIC }
enum class LightState  { OFF, ON, DAYTIME_RUNNING }
enum class EvChargeState { UNKNOWN, CHARGING, FULLY_CHARGED, NOT_CHARGING, ERROR }   // 현재 public API에서 반환하는 메서드 없음(참고용)
enum class IgnitionState { UNDEFINED, LOCK, OFF, ACC, ON, START }                     // 동일
// ai.pleos.playground.vehicle.constant.unit
enum class TemperatureUnit { CELSIUS, FAHRENHEIT }

// ai.pleos.playground.vehicle.listener
interface VehicleListener { fun onFailed(e: Exception) }
```
운전석 = `SeatArea(SeatValue.ROW_1_LEFT)` (국내 좌핸들 가정).

## 3. EvBattery (`ai.pleos.playground.vehicle.api.EvBattery`)

```kotlin
// 조회  — @RequiresPermission CAR_ENERGY
fun getEvBatteryStateOfCharge(onComplete: (Int) -> Unit, onFailed: (Exception) -> Unit)     // SOC %
fun getEvBatteryLevel(onComplete: (Float) -> Unit, onFailed: (Exception) -> Unit)           // kWh(추정)
fun getEvBatteryCapacity(onComplete: (Float) -> Unit, onFailed: (Exception) -> Unit)        // CAR_INFO
fun getEvChargeSwitchState(onComplete: (Boolean) -> Unit, onFailed: (Exception) -> Unit)    // 충전 스위치 ON/OFF
fun isEvChargePortOpen(onComplete: (Boolean) -> Unit, onFailed: (Exception) -> Unit)
fun getEvChargeCurrentDrawLimit(onComplete: (Float) -> Unit, onFailed: (Exception) -> Unit)
// ❌ getEvChargePercentLimit 없음

// 제어 — CONTROL_CAR_ENERGY. onComplete/onFailed 기본값 있음. ※ PM 결정(11-03): 앱은 setEvChargePercentLimit를 호출하지 않음(사용자 설정 변경 금지). 목표 SOC는 앱 내부 값.
fun setEvChargePercentLimit(value: Float, onComplete: (Float) -> Unit = {}, onFailed: (Exception) -> Unit = {})
fun setEvChargeSwitch(value: Boolean, onComplete: (Boolean) -> Unit = {}, onFailed: (Exception) -> Unit = {})
fun setEvChargePortOpen(value: Boolean, ...)
fun setEvChargeCurrentDrawLimit(value: Float, ...)

// Capability
fun checkEvBatteryStateOfChargeCapabilities(onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit)
fun checkEvChargeSwitchCapabilities(onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit)
fun checkEvChargePercentLimitCapabilities(onComplete: (CapabilityConfig, List<Float>) -> Unit, onFailed: (Exception) -> Unit) // List<Float> = 허용 퍼센트 목록
fun checkEvChargePortOpenCapabilities(onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit)

// 리스너
fun registerEvBatteryStateOfCharge(listener: EvBatteryStateOfChargeListener)
fun unregisterEvBatteryStateOfCharge(listener: EvBatteryStateOfChargeListener)
fun registerEvBatteryLevel(listener: EvBatteryLevelListener) / unregister...
fun registerEvChargePortOpen(listener: EvChargePortOpenListener) / unregister...
// ❌ 충전 스위치 리스너 없음 → 폴링(get) 필요 (§8)

interface EvBatteryStateOfChargeListener : VehicleListener { fun onEvBatteryStateOfChargeUpdated(value: Int?) }
interface EvBatteryLevelListener : VehicleListener { fun onEvBatteryLevelUpdated(value: Float?) }
interface EvChargePortOpenListener : VehicleListener { fun onEvChargePortOpenUpdated(value: Boolean?) }
```

## 4. Gear / Brake (기어 P 판정)

```kotlin
// ai.pleos.playground.vehicle.api.Gear — CAR_DRIVE. 문서(2.3.1)에 없지만 2.3.2 바이너리에 존재, Vehicle.getGear()로 획득
fun getCurrentGearSelectionState(onComplete: (VehicleGear) -> Unit, onFailed: (Exception) -> Unit)
fun registerGearSelection(listener: GearSelectionListener)
fun unregisterGearSelection(listener: GearSelectionListener)
interface GearSelectionListener : VehicleListener { fun onSelected(gear: VehicleGear?) }
// 에뮬레이터 Virtual Console "Motion > Gear P/R/N/D"가 이 신호에 대응할 가능성 높음 — 1일차 실측 필요. 실패 시 §8 대안.

// ai.pleos.playground.vehicle.api.Brake — CAR_DRIVE (브레이크 페달 눌림 여부, Boolean)
fun checkBrakePedalStatusCapabilities(onComplete: (CapabilityConfig) -> Unit, onFailed: (Exception) -> Unit)
fun getCurrentBrakePedalStatus(onComplete: (Boolean) -> Unit, onFailed: (Exception) -> Unit)
fun registerBrakePedalStatus(listener: BrakePedalStatusListener)
fun unregisterBrakePedalStatus(listener: BrakePedalStatusListener)
interface BrakePedalStatusListener : VehicleListener { fun onBrakePedalStatusUpdated(pressed: Boolean?) }
// 파킹 브레이크는 Brake 도메인에 없음. Safety 텔테일(TelltaleType)에 PARKING_BRAKE 계열이 있을 수 있으나 샘플앱 PublicApiPolicy가 Safety.* 를 blocked 처리 → 사용 금지.
```
`DrivingMode`는 `getCurrentDrivingModeState{DrivingModeType}`·크루즈뿐, 기어 없음. `CarInfo`는 VIN/CRN만.

## 5. Seat (`ai.pleos.playground.vehicle.api.Seat`) — CAR_SEATS / CONTROL_CAR_SEATS

```kotlin
// 등받이: 각도 "이동" 명령(FORWARD/BACKWARD/STOP) + 위치 조회. 절대각 set 없음.
fun checkMoveSeatBackrestAngleCapabilities(onComplete: (CapabilityConfig, List<SeatArea>) -> Unit, onFailed: (Exception) -> Unit)
fun checkSeatBackrestAnglePositionCapabilities(onComplete: (CapabilityConfig, Map<SeatArea, AreaBounds<Int>>) -> Unit, onFailed: (Exception) -> Unit)
fun moveSeatBackrestAngle(area: SeatArea, value: MoveControl, onComplete: (SeatArea, MoveControl) -> Unit = {_,_->}, onFailed: (Exception) -> Unit = {})
fun getCurrentSeatBackrestAnglePosition(area: SeatArea, onComplete: (Int) -> Unit, onFailed: (Exception) -> Unit)
fun registerSeatBackrestAnglePosition(area: SeatArea, listener: SeatBackrestAnglePositionListener) / unregister...
interface SeatBackrestAnglePositionListener : VehicleListener { fun onSeatBackrestAnglePositionUpdated(value: Int?) }

// 열선 / 통풍 (Int 단계, 0=OFF. 범위는 capability AreaBounds로)
fun checkSeatTemperatureCapabilities(onComplete: (CapabilityConfig, Map<SeatArea, AreaBounds<Int>>) -> Unit, onFailed: (Exception) -> Unit)
fun checkSeatVentilationCapabilities(onComplete: (CapabilityConfig, Map<SeatArea, AreaBounds<Int>>) -> Unit, onFailed: (Exception) -> Unit)
fun setHvacSeatTemperature(area: SeatArea, value: Int, onComplete: (SeatArea, Int) -> Unit = {_,_->}, onFailed: (Exception) -> Unit = {})
fun getHvacSeatTemperature(area: SeatArea, onComplete: (Int) -> Unit, onFailed: (Exception) -> Unit)
fun setHvacSeatVentilation(area: SeatArea, value: Int, onComplete: (SeatArea, Int) -> Unit = {_,_->}, onFailed: (Exception) -> Unit = {})
fun getHvacSeatVentilationState(area: SeatArea, onComplete: (Int) -> Unit, onFailed: (Exception) -> Unit)
fun registerHvacSeatTemperature(area, listener: HvacSeatTemperatureListener)   // onHvacSeatTemperatureUpdated(Int?)
fun registerHvacSeatVentilation(area, listener: HvacSeatVentilationListener)   // onHvacSeatVentilationUpdated(Int?)

// 착좌
fun checkSeatOccupancyCapabilities(onComplete: (CapabilityConfig, List<SeatArea>) -> Unit, onFailed: (Exception) -> Unit)
fun getCurrentSeatOccupancy(area: SeatArea, onComplete: (Boolean) -> Unit, onFailed: (Exception) -> Unit)
fun registerSeatOccupancy(area: SeatArea, listener: SeatOccupancyListener) / unregisterSeatOccupancy(area, listener)
interface SeatOccupancyListener : VehicleListener { fun onSeatOccupancyUpdated(occupied: Boolean?) }

// 메모리 (복원 대안): selectSeatMemory(area: SeatArea, value: Int, onFailed: (Exception) -> Unit = {})
```
※ 등받이 복원 전략: 시작 전 `getCurrentSeatBackrestAnglePosition` 저장 → `moveSeatBackrestAngle(BACKWARD)` 후 리스너로 위치 감시, 목표(저장값−Δ) 도달 시 `STOP`. 복원은 FORWARD로 동일. Δ·에뮬레이터 반영 여부는 1일차 실측.

## 6. Light / HVAC / Window

```kotlin
// Light — CAR_LIGHTS / CONTROL_CAR_LIGHTS. 앰비언트·독서등은 SeatArea 단위, 실내등(cabin)은 전역
fun checkAmbientLightBrightnessCapabilities(onComplete: (CapabilityConfig, Map<SeatArea, AreaBounds<Int>>) -> Unit, onFailed)
fun checkAmbientLightBrightnessColorCapabilities(onComplete: (CapabilityConfig, List<SeatArea>) -> Unit, onFailed)
fun checkAmbientLightSwitchCapabilities(onComplete: (CapabilityConfig, Map<SeatArea, List<LightSwitch>>) -> Unit, onFailed)
fun checkCabinLightSwitchCapabilities(onComplete: (CapabilityConfig, List<LightSwitch>) -> Unit, onFailed)
fun getAmbientLightBrightness(area: SeatArea, onComplete: (Int) -> Unit, onFailed)
fun setAmbientLightBrightness(area: SeatArea, value: Int, onComplete: (SeatArea, Int) -> Unit, onFailed)      // 기본값 없음(둘 다 넘길 것)
fun getAmbientLightBrightnessColor(area: SeatArea, onComplete: (Int) -> Unit, onFailed)
fun setAmbientLightBrightnessColor(area: SeatArea, value: Int, onComplete: (SeatArea, Int) -> Unit, onFailed) // Int 색 값(ARGB 추정, 실측)
fun getAmbientLightSwitch(area: SeatArea, onComplete: (LightSwitch) -> Unit, onFailed)
fun setAmbientLightSwitch(area: SeatArea, value: LightSwitch, onComplete: (SeatArea, LightSwitch) -> Unit, onFailed)
fun getAmbientLightState(area: SeatArea, onComplete: (LightState) -> Unit, onFailed)
fun getCabinLightSwitch(onComplete: (LightSwitch) -> Unit, onFailed)
fun setCabinLightSwitch(value: LightSwitch, onComplete: (LightSwitch) -> Unit = {}, onFailed = {})
fun getCurrentCabinLightState(onComplete: (LightState) -> Unit, onFailed)
fun getReadingLightSwitch(area: SeatArea, ...) / setReadingLightSwitch(area, value: LightSwitch, onComplete: (LightSwitch)->Unit = {}, onFailed = {}) / getCurrentReadingLightState(area, ...)
fun getAmbientColorAutoByDriveModeEnabled(onComplete: (Boolean) -> Unit, onFailed) / setAmbientColorAutoByDriveModeEnabled(value: Boolean, ...)
// 리스너: registerCabinLightState(LightStateListener), registerAmbientLightBrightnessListener(area, AmbientLightBrightnessListener{onBrightnessUpdated(Int?)}),
//        registerAmbientLightBrightnessColorListener(area, ...{onBrightnessColorUpdated(Int?)}), registerAmbientLightStateListener(area, LightStateListener{onLightStateUpdated(LightState?)}), registerReadingLightState(area, LightStateListener)
// 기타 외부등: high/low beam, fog, tail lamp — 미사용

// HVAC — CAR_CLIMATE / CONTROL_CAR_CLIMATE. 전부 SeatArea 단위 (운전석 존 = ROW_1_LEFT)
fun checkHvacTemperatureCapabilities(onComplete: (CapabilityConfig, Map<SeatArea, AreaBounds<Float>>, HvacTemperatureConfigArray) -> Unit, onFailed)
fun checkHvacFanSpeedCapabilities(onComplete: (CapabilityConfig, Map<SeatArea, AreaBounds<Int>>) -> Unit, onFailed)
fun checkHvacPowerCapabilities(onComplete: (CapabilityConfig, List<SeatArea>) -> Unit, onFailed)
fun getHvacTemperature(area: SeatArea, unit: TemperatureUnit, onComplete: (Float) -> Unit, onFailed)
fun setHvacTemperature(area: SeatArea, value: Float, unit: TemperatureUnit, onComplete: (SeatArea, Float) -> Unit = {_,_->}, onFailed = {})
fun getHvacTemperatureCurrent(area: SeatArea, [unit], onComplete: (Float) -> Unit, onFailed)   // 실내 현재 온도(외기온 아님)
fun getHvacFanSpeed(area, onComplete: (Int) -> Unit, onFailed) / setHvacFanSpeed(area, value: Int, onComplete: (SeatArea, Int)->Unit = .., onFailed = ..)
fun getHvacPowerState(area, onComplete: (Boolean) -> Unit, onFailed) / setHvacPower(area, value: Boolean, ...)
fun getHvacAcState(area, ...) / setHvacAc(area, value: Boolean, ...) ; setHvacAuto, setHvacRecirculation, setHvacDefroster(WindowArea, Boolean), setHvacFanDirection(area, FanDirectionType) 등
// 리스너: registerHvacTemperature(area, HvacTemperatureListener{onHvacTemperatureUpdated(Float?)}), registerHvacFanSpeed(area, HvacFanSpeedListener{onHvacFanSpeedUpdated(Int?)}), registerHvacPowerState(area, HvacPowerStateListener{onHvacPowerStateUpdated(Boolean?)})
// 외기온 API 없음 → PRD "여름/겨울" 판단은 setHvacTemperature 설정값 또는 getHvacTemperatureCurrent(실내온) 기준

// Window — CAR_WINDOWS / CONTROL_CAR_WINDOWS. 위치 Int(0=닫힘 추정, 범위는 capability)
fun checkWindowPositionCapabilities(onComplete: (CapabilityConfig, Map<WindowArea, AreaBounds<Int>>) -> Unit, onFailed)
fun getCurrentWindowPosition(area: WindowArea, onComplete: (Int) -> Unit, onFailed)
fun setWindowPosition(area: WindowArea, value: Int, onComplete: (WindowArea, Int) -> Unit = .., onFailed = ..)
fun registerWindowPosition(area: WindowArea, listener: WindowPositionListener) / unregisterWindowPosition(area, listener)
interface WindowPositionListener : VehicleListener { fun onWindowPositionUpdated(value: Int?) }
fun getCurrentWindowLockState(area, onComplete: (Boolean)->Unit, onFailed) / setWindowLock(area, value: Boolean, ...) / registerWindowLockState(area, WindowLockStateListener)
```

## 7. 오류 처리 — VehicleException 난독화 이슈

문서는 `VehicleException.errorCode` 를 말하지만 **2.3.2 AAR에는 `ai.pleos.playground.vehicle.callback.exception.a` 로만 존재**(R8 리네임, kotlin 메타데이터도 없음). 따라서:
- `e is VehicleException` / `import ...VehicleException` → **컴파일 에러**. 쓰지 말 것.
- 생성자 로직(바이트코드): `ErrorCode.findById(id) ?: ERROR_UNDEFINED` 를 저장하고 `message = errorCode.name`. 즉 `e.message == "ERROR_SECURITY_PERMISSION"` 같은 문자열.
- `ErrorCode` enum 자체는 `vehicle-data` 아티팩트에 **정상 이름으로** 존재: `ai.pleos.playground.vehicle.constant.ErrorCode { ERROR_INTERNAL_SERVICE, ERROR_SERVICE_CONNECTION_FAILURE, ERROR_DRIVING_RESTRICTED, ERROR_NOT_SUPPORTED_PROPERTY, ERROR_NOT_SUPPORTED_VALUE_TYPE, ERROR_NOT_SUPPORTED_AREA, ERROR_EMPTY_RESULT, ERROR_SECURITY_PERMISSION, ERROR_UNDEFINED; val id: Int; companion findById(Int) }`

```kotlin
// ponytail: 난독화 우회. SDK가 고쳐지면 (e as? VehicleException)?.errorCode 로 교체
fun Exception.vehicleErrorCode(): ErrorCode? =
    message?.let { m -> ErrorCode.entries.firstOrNull { it.name == m } }
```
권장 분기: `ERROR_NOT_SUPPORTED_PROPERTY / _AREA` → 해당 기능만 건너뛰고 UI "미지원" 표시, `ERROR_SECURITY_PERMISSION` → 매니페스트 확인, `ERROR_SERVICE_CONNECTION_FAILURE / INTERNAL` → initialize 재시도, `ERROR_DRIVING_RESTRICTED` → 주행 중 안내.

## 8. PRD 대비 갭과 대안 (PM 결정 반영 완료)

| PRD 항목 | SDK 현실 | 대안 |
|---|---|---|
| 충전 한도 원래값 스냅샷 | `getEvChargePercentLimit` 없음 | `checkEvChargePercentLimitCapabilities`의 `List<Float>` 중 **최댓값(보통 100)** 으로 복원. 사용자가 원래 80으로 뒀어도 100으로 바뀜 → 데모 시연 멘트에 "복원 = 한도 해제"로 표현하거나, 앱이 설정한 값만 기억하고 종료 시 한도 100 |
| 충전 스위치 변화 감지 | 스위치 리스너 없음 | `getEvChargeSwitchState` 5초 폴링(휴식 중에만), 또는 `registerEvChargePortOpen` 로 포트 닫힘을 종료 트리거로 사용 |
| 기어 P 판정 | `Gear` 도메인(2.3.2) 있음, 문서 없음 | 1순위 Gear. 에뮬레이터에서 `ERROR_NOT_SUPPORTED_PROPERTY` 나오면 2순위: Brake 페달 눌림 + 충전 스위치 ON을 "정차 충전 중"으로 간주, 버튼 활성 조건을 "충전 스위치 ON && 기어 미확인" 으로 완화 |
| 외기온 기준 계절 판단 | 외기온 API 없음 | `getHvacTemperature`(설정 온도) ≥ 24 → 통풍, 그 외 열선 |
| 등받이 "리클라인" | 절대각 set 없음, FORWARD/BACKWARD/STOP | 시작 위치 저장 → BACKWARD + 위치 리스너 → Δ 도달 시 STOP. 에뮬레이터 미반영 시 열선/통풍·조명·HVAC만 시연 |
| 파킹 브레이크 | Brake 도메인에 없음 | 미사용. 브레이크 "페달" 눌림(`registerBrakePedalStatus`)을 출발 의도로 사용 (PRD v0.2 선제 복원 트리거에 그대로 맞음) |

## 9. TextToSpeech 3.3.1

```kotlin
// implementation("ai.pleos.playground:TextToSpeech:3.3.1")   // 매니페스트: <uses-permission android:name="pleos.car.permission.TTS_SERVICE" />
package ai.pleos.playground.tts
class TextToSpeech(context: Context, mode: Mode /* 생략 시 HYBRID */) {
    fun initialize(); fun initialize(listener: OnInitializeListener)
    fun release()
    fun addEventListener(listener: EventListener); fun removeEventListener(listener: EventListener)
    fun setLocale(locale: java.util.Locale)         // ko-KR, en-US, fr-CA, es-US, es-MX 지원
    fun speak(text: String, isFinal: Boolean = true)
    fun stop()
    fun setAudioAttributes(attrs: android.media.AudioAttributes)
    fun setAudioZoneId(zoneId: Int); fun setSpeakerType(type: String)
    fun registerApp(clientID: String, clientSecret: String, listener: OnServerConnectionListener)   // SERVER/HYBRID만. ON_DEVICE 불필요
    companion object { val version: String }
}
enum class Mode { SERVER, ON_DEVICE, HYBRID }
enum class ErrorType { AUDIO_ERROR, MIC_ERROR, SERVER_INTERNAL_ERROR, NETWORK_ERROR, INVALID_REQUEST_ERROR, TIMEOUT, UNAUTHENTICATED, PERMISSION_DENIED, ON_DEVICE_ENGINE_ERROR, UNKNOWN, UNAVAILABLE_LANGUAGE, NOT_SUPPORTED_API }
interface OnInitializeListener { fun onSuccess(); fun onFailure(errorType: ErrorType) }
interface EventListener { fun onReady(); fun onStart(); fun onUpdatedRms(rms: Double); fun onDone(); fun onStop(); fun onError(errorType: ErrorType) }
interface OnServerConnectionListener { fun onConnected(); fun onFailed(errorType: ErrorType) }
```
ON_DEVICE 최소 예제 (앱 등록 없음):
```kotlin
val tts = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)
tts.initialize(object : OnInitializeListener {
    override fun onSuccess() { tts.setLocale(Locale.KOREA); ready = true }
    override fun onFailure(errorType: ErrorType) { Log.w("TTS", errorType.name) }
})
tts.addEventListener(object : EventListener {
    override fun onReady() {}  override fun onStart() {}  override fun onUpdatedRms(rms: Double) {}
    override fun onDone() {}   override fun onStop() {}
    override fun onError(errorType: ErrorType) { Log.w("TTS", errorType.name) }
})
if (ready) tts.speak("휴식 모드를 시작합니다. 목표 80%에 도달하면 깨워 드릴게요.")
// onDestroy: tts.removeEventListener(l); tts.release()
```
AAR 매니페스트가 자동 추가: `INTERNET`, `<queries>` action `ai.pleos.playground.caas.intent.action.SPEECH_START`, CAAS 최소 버전 4.1.29. 오디오 포커스는 서비스 측에서 처리(앱은 `setAudioAttributes`로 USAGE_ASSISTANT 등 지정 가능).

## 10. 샘플앱 스택(교차 확인, raw 읽기만)
AGP 8.8.2 / Kotlin 2.0.0 / compose-bom 2024.04.01 / core-ktx 1.16.0 / lifecycle 2.9.0 / activity-compose 1.10.1 / Gradle 8.10.2 / compileSdk 35 / JDK 17. 샘플 toml은 vehicleSdk 2.3.1이지만 Maven에 없으므로 **2.3.2로 고정**. 샘플은 `kotlin-reflect` 2.0.0을 명시 추가(SDK가 1.9.25 reflect를 끌어오므로 버전 충돌 방지용).

## 11. 에뮬레이터 1일차 실측 체크리스트
Pleos Connect 에뮬레이터(2.1.0, API 34) 설치 후 Virtual Console과 함께 아래를 순서대로 확인한다. 결과는 이 표에 적는다.

| # | 항목 | 확인 방법 | 기대 | 실패 시 대안 | 결과 |
|---|---|---|---|---|---|
| 1 | `Vehicle.initialize()` 성공, Idle 화면 "차량 연결 중…"이 사라짐 | 앱 실행 | SOC 숫자 표시 | logcat `PleosVehicle` 태그 확인, 매니페스트 권한 재확인 | |
| 2 | **Gear API** `getGear().getCurrentGearSelectionState` / `registerGearSelection` | Console Motion > Gear P↔D 전환 | 상태줄 "기어 P/D" 갱신, D에서 버튼 비활성 | 실패(`ERROR_NOT_SUPPORTED_PROPERTY`)면 기어 무시, CarUxRestrictions 단독 | |
| 3 | **CarUxRestrictions** `isRequiresDistractionOptimization` | Console에서 주행 상태(속도/기어 D) | Resting 중 D 전환 시 복원 없이 Done(그대로) | android.car 없으면 로그 "CarUxRestrictions unavailable" → 기어로만 판정 | |
| 4 | 충전 포트 `isEvChargePortOpen` / `registerEvChargePortOpen` | Console Energy > Charge Port 토글 | Idle 버튼 활성/비활성 | 미지원이면 충전 스위치(10초 폴링)만으로 판정 | |
| 5 | SOC 리스너 `registerEvBatteryStateOfCharge` | Console SOC 슬라이더 50→60 | 링·숫자 즉시 갱신 | 폴링으로 교체(refresh 10초) | |
| 6 | **등받이 각도 반영** `moveSeatBackrestAngle(BACKWARD/STOP)` + `getCurrentSeatBackrestAnglePosition` | 휴식 시작 후 Console Seat 값 | 각도 값이 변함 | 변하지 않으면 시연에서 시트 항목 제외("이 차량 미지원" 표시) / 반영되면 시간 기반→각도 목표 기반으로 코드 교체 (`PleosVehicleGateway.RECLINE_MS`) | |
| 7 | 통풍/열선 `setHvacSeatVentilation` / `setHvacSeatTemperature` 범위 | `checkSeatVentilationCapabilities` 로그 | Console Seat 값 변경 | `SEAT_LEVEL` 상수 조정 | |
| 8 | **앰비언트 값 범위** `checkAmbientLightBrightnessCapabilities`의 min/max 실측(0~100? 0~10?) / `setAmbientLightBrightnessColor`(ARGB?) | Console Light 값, logcat `ambientRange` | 밝기·색 변경, 어둡게=min+10%·선라이즈 목표=min+70%가 범위 안에서 동작 | 범위 비율(`AMBIENT_DIM_PCT`, `Sunrise.DEFAULT_TO_PCT`) 재조정, 색이 enum 인덱스면 매핑 추가 **[v0.7 검수 I2]** | |
| 9 | 실내등 `setCabinLightSwitch(OFF)` / `registerCabinLightState` | Console Light > 실내등 | OFF 반영, Done 카드 "실내등이 켜져 있어요" | — | |
| 10 | HVAC `setHvacTemperature(22, CELSIUS)` / `setHvacFanSpeed(1)` / `setHvacRecirculation(false)` | Console HVAC | 온도 22, 풍량 1 | 풍량 최소값이 1이 아니면 `REST_FAN` 조정 | |
| 11 | 창문 `registerWindowPosition` (0=닫힘 가정) | Console Window 조수석 30% | Done 카드 "조수석 창문이 열려 있어요" | 값 방향이 반대면 `openWindows` 판정 부호 수정 | |
| 12 | 뒷좌석 착좌 `registerSeatOccupancy(ROW_2_*)` | Console Seat Occupancy | 리클라인 절반 + 안내 1줄 | — | |
| 13 | 브레이크 `registerBrakePedalStatus` | Console Brake 0→50% | 시트 선제 복원(FORWARD) 1회 | — | |
| 14 | TTS ON_DEVICE ko-KR `speak` | 휴식 시작 | 음성 재생 | `ErrorType` 로그 확인(UNAVAILABLE_LANGUAGE면 en-US로 폴백) | |
| 15 | 프로세스 종료 후 재실행 | `adb shell am force-stop` 후 재실행 | Resting 화면·남은시간 복원, 알림 1줄 | — | |
