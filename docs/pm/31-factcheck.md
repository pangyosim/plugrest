# 31. 리서치 팩트체크 + 제출 규정 체크리스트 (2026-11-03, 검수B)

판정: ○ 일치 / △ 부분 일치·맥락 차이·출처 약함 / × 틀림·접근 불가. 원문 수정은 하지 않음(정정값만 제시).

## 요약 — 먼저 고칠 것 (우선순위)
1. **research/04 §2·초안 "급속 세션의 절반은 차 안에 머무른다" → 근거 없음, 반대 데이터 존재.** Gilbarco 2025 조사: 15분 급속 충전 중 차 안에 머무는 유럽 BEV 운전자 **25%**(74%는 상점·식당 방문). PRD §2도 같은 문장 사용 → 동시 수정.
2. **research/04 [7] "국내 실측" → 서울시, 2022-10-07 하루(24h)치.** "이용률"은 시간 가동률이 아니라 "하루 1회 이상 이용된 충전기 비율".
3. **research/04 "기아는 2027년부터 적용 전망"** → 인용 출처 [4][5][6] 어디에도 없음. 삭제 또는 "미확인".
4. **research/02·03 Vehicle 버전 서술이 반대.** 샘플 repo·getting-started가 2.3.1, 공식 최신(Importing 문서·릴리즈노트·Nexus)은 2.3.2. **2.3.1은 Nexus에 없음(404)** → 샘플 그대로 빌드 시 의존성 실패 가능(추정). 우리 앱은 2.3.2.
5. **research/01 README 필수 항목은 5개가 아니라 공식 4개.** 수정 제출 제목 `[수정제출]` 접두와 CC·서명 재첨부 규칙 누락.
6. **research/03 "4GB RAM이면 충분" → 문서에 GPU 필수(최소 RTX 3060 Laptop 6GB / RX 6600M 8GB), Apple Silicon 언급 없음.** M4 Pro 구동은 1일차 검증 필요(신규 리스크).
7. research/02 "차속 신호 없음" → Vehicle SDK 한정. AAOS `PERF_VEHICLE_SPEED`(CarPropertyManager)로 조회 가능(Vehicle App FAQ, 기획 가이드 "표준 데이터: 속도, 기어 상태"). 단 Virtual Console에 속도 카드 없음.

---

## 1. research/01-contest-brief.md (공식 사이트 대조)

확인 경로: `/`, `/guide/submission`, `/guide/judging-awards`, `/faq`, `/notice`(+개별 글 3건), `/guide/overview`, `/guide/schedule`, `/guide/shortform`, `/guide/rules`, 공식 docx. 모두 KST.

| 주장 | 판정 | 공식 근거 / 정정 |
|---|---|---|
| 접수 2026-10-21 ~ 11-15(일) 23:59 | ○ | "2026.10.21(수) ~ 11.15(일)", rules `2026-11-15T23:59:59+09:00` |
| 1차 결과 11-23 | ○ | "2026.11.23(월)" — 요일 보충 |
| 2차 PT 12-03~04 오프라인, 동작 앱 필수 | ○ | 장소 누락: **현대자동차 강남대로사옥 1층 UX스튜디오 서울(강남대로 350)**. 공지 006: 시연은 "에뮬레이터 구동 화면 또는 기기 시연" |
| 최종 발표 12-18 / 전시·시상 2027-02 | ○ | 누락: 12/19~2027.01 수상팀 전시 준비 기간(추가 개발 기간 아님) |
| 이메일 주소 | ○ | appdevchallenge@hyundai-partners.com |
| 메일 제목 `[차량용 앱 개발 공모전] 팀명(개인은 성명)_대표자명` | ○ | 바이트 단위 일치. **누락: 수정 제출 시 `[수정제출] [차량용 앱 개발 공모전] 팀명(개인은 성명)_대표자명`** |
| 필수 = 접수양식 docx(기획서) + 동의 서명 | ○ | 1~6 필수, 7·8 선택 |
| 선택: 시연영상(화면녹화 OK)/GitHub/숏폼, 단계별 가산점 | ○ | 숏폼은 가산점 없음 |
| APK `apk/팀명_앱이름_release.apk` | ○ | 누락: "실행 가능한 release 버전", 용량 초과 시 GitHub Releases 등록 후 Release 페이지 주소 제출 |
| README 필수 5항목 | **△** | **공식 4항목**: ① 앱 이름 및 한 줄 소개 ② 실행 방법 ③ 테스트 환경 및 필요한 설정 ④ APK 파일 위치 또는 GitHub Release 링크 |
| 비공개 repo + 심사계정 Collaborator | ○ | 누락: 마감 전 심사계정 접근 확인 의무, 개인계정 비공개 repo는 쓰기 권한까지 부여됨 주의 |
| 개인/팀 최대 5인 | ○ | |
| 대학(원)생 / 일반(스타트업 포함) | △ | 참가 대상(대학(원)생·일반인·스타트업)과 **시상 부문(대학생 / 일반(스타트업 포함))**을 구분해 표기 |
| 재제출 가능, 마지막 제출본 심사 | ○ (조건 누락) | 같은 발신 주소 + `[수정제출]` 제목 + **전체 자료·서명 동의서 전부 재첨부 + 팀원 전원 CC** 여야 유효(공지 006) |
| 1차 4개 기준, 배점 비공개 | ○ | 기술활용성 공식 문구: "Pleos Playground에서 제공하는 SDK·API를 앱의 핵심 기능과 사용 경험에 적절히 활용했는가" |
| 주제 예시 | ○ | overview 카테고리 추가: 모빌리티&라이프 편의 / 인카 엔터테인먼트 / 스마트 드라이빙&안전 / 패밀리&동승자 케어 / 기타 |
| 숏폼 특별상 50만원 | ○ | 누락: 30초~1분 세로, 개인/팀 계정 Instagram 릴스·YouTube Shorts, 필수 해시태그 `#HMG차량용앱개발공모전` 포함 2~3개 |
| 기획서 섹션 1~8, 02 개인정보 금지 | ○ | 추가: "공유 링크와 첨부자료에도 개인정보·계정정보·개발자 실명이 노출되지 않도록" |
| (누락) 상금 | — | 총 5,100만원. 부문별 대상 1,000 / 최우수 600 / 우수 400(부문별 2팀), 아이디어 특별상 150(통합 2팀), 숏폼 50 별도. 최종 10팀 |

### /notice 현황 (전체 3건, 게시일 표시 없음)
1. **[안내] 참가 및 출품 유의사항** (006, 최상단) — **1차 합격 20팀**, 2차는 에뮬레이터 또는 실기기 시연 필수, 1차 산출물은 PT 전까지 고도화 가능, 순위는 2차 결과로 확정, 재제출 유효 요건(위), 임직원 참가 가능·수상 제외, 마감 후 제출 불인정, 파일 오류 책임은 지원자, 상금은 대표자 일괄·원천징수, 통상실시권·4개월 우선협상권 가능, "세부 심사기준 및 과정은 공개하지 않습니다".
2. **[공고] 참가자 모집 공고** (001) — 일정·제출물·해시태그(필수 `#HMG차량용앱개발공모전`, 제안 `#PleosAppMarket` `#HMG앱공모전`).
3. **[설명회] 온라인 설명회** (005) — 10/28(수) 14:00, 다시보기 제공.

- **2차 PT 심사 기준: 미공지** ("확정되는 대로 공지사항으로 안내").
- **심사용 GitHub 계정명: 미공지** ("공지된 심사용 GitHub 계정"이라고만 기재). → 비공개 repo 계획이면 공지 모니터링 또는 메일 문의. **공개 repo가 안전.**
- 마감 변경 없음.
- 첨부 용량·파일 형식(PDF/HWP), 시연 영상 길이·형식: **규정 없음**. 배포 양식은 DOCX뿐.
- `/guide/submission`의 "저장소 주소와 APK 파일 경로를 제출 페이지에 입력" 문구는 실제 접수(이메일)와 불일치 → 기획서 7번 + 메일 본문에 모두 기재 권장(추정).

---

## 2. research/02-pleos-api-inventory.md

| 주장 | 판정 | 정정 / 근거 |
|---|---|---|
| Vehicle 19개 도메인 | ○ | 19개 일치. Gear·Speed 도메인 없음 (/connect-sdk-pleos/Vehicle/intro) |
| 권한 접두사 | ○ | `pleos.car.permission.{상수명}` (android.car 아님) |
| 도메인별 read 권한 (SteeringWheel 외) | ○ | 공식 표와 일치 |
| SteeringWheel 권한 "-" | × | 각도 조회 `CAR_DRIVE`, 열선 조회 `CAR_CLIMATE`, 열선 설정 `CONTROL_CAR_CLIMATE`. **조향각 API 누락** |
| 제어 = `CONTROL_CAR_*` 일괄 | △ | Light의 `setAmbientLightBrightness` / `setAmbientLightBrightnessColor` / `setAmbientLightSwitch`는 **`CAR_LIGHTS`** 요구. 나머지 Light setter는 CONTROL_CAR_LIGHTS |
| Display Set O | × | setter 없음 (TemperatureDisplayUnit check/get/register만) |
| Odometer Set "-" | × | `setRangeRemainingDistance` 존재 (`CONTROL_CAR_DRIVE`) |
| Door/EvBattery/HVAC/Light/Seat/SideMirror/SteeringWheel/Toll/Window/Wiper Set | ○ | 추가: Door `setGloveBoxLock`, Window SmartGlass·blind |
| Toll = 하이패스 | △ | API는 ETCS **음성 안내 볼륨** get/set/register뿐 |
| CarInfo = 제조사/연식/치수 | △ | API ref(2.3.2)엔 `getVINInfo`, `getCRNInfo`(deprecated)만 |
| FusedLocation 권한 | ○ | 단 **protectionLevel=dangerous → 런타임 권한 요청 필요** (Vehicle 권한과 다름) |
| ERROR_SECURITY_PERMISSION | ○ | |
| Maven URL / FAIL_ON_PROJECT_REPOS | ○ | |
| Vehicle 2.3.1 (샘플 2.3.2) | **×** | 반대. 샘플·getting-started = 2.3.1(Nexus 404), 공식 최신 = **2.3.2** |
| minSdk 28+ / Gradle 8+ | ○ | |
| JDK 17 | △ | SDK 환경 표엔 없음, 샘플 튜토리얼에만 |
| 에뮬레이터 2.1.0 / API 34 / mac URL / Cold / 4GB | ○ | v2.1.0 릴리즈 2026-10-19 |
| 차속 신호 없음 | △ | Vehicle SDK 2.3.2에서 `VehicleSpeedListener` 삭제는 맞음. 그러나 AAOS `PERF_VEHICLE_SPEED`로 조회 가능(차종별 지원 확인 필요). Virtual Console엔 속도 카드 없음 |
| Virtual Console 항목 | ○ | 콘솔에 없는 것: Steering Wheel Heat, Remaining Range, Glove Box, Trunk/Frunk |
| 화면 portrait 1 / landscape 2 window | ○ | app-structure-screen-model 원문 일치 |
| 백그라운드 최소화·상태 복원 | ○ | 앱 심사 가이드도 "마지막 사용 중이던 상태가 복원되어야" 명시 |

## 3. research/03-dev-env-status.md

| 주장 | 판정 | 정정 / 근거 |
|---|---|---|
| Maven 공개(200) | ○ | metadata 6종 200 |
| update site URL (mac / win·linux) | ○ | 둘 다 200, display-name "Pleos Connect v2.1.0 … System Image", api-level 34 |
| 42dot 라이선스 동의 | ○ | |
| Vehicle "문서 2.3.1, 샘플 2.3.2" | × | 위와 같음(반대) |
| NaviHelper 3.0.5 / TTS 3.3.1 / STT 3.3.1 / FusedLocation 1.1.3 | ○ | |
| TTS ON_DEVICE 등록 불필요 | ○ | **미확인 항목 해소**: "앱 등록" = Pleos Playground My Project의 Client ID/Secret로 `registerApp()`. ON_DEVICE만 쓰면 불필요 |
| LLM 2.1.3.2, 문서 404 | △ | 에뮬레이터 v2.1.0 릴리즈노트 "**LLM SDK 제공 종료**" → 사용 안 함 확정 |
| 샘플 AGP 8.8.2 / Kotlin 2.0.0 / BOM 2024.04.01 / compileSdk 35 / JDK 17 | ○ | targetSdk 35, Gradle wrapper 8.10.2 |
| 샘플 minSdk 23 vs 02의 28+ | ○ (모순 아님) | 샘플은 23, **배포 앱은 28+** 필수(튜토리얼 원문) |
| google(content filter) + mavenCentral + nexus | △ | 필터 있는 google()은 pluginManagement에만 |
| protectionLevel=normal | ○ (Vehicle 한정) | FusedLocation은 dangerous |
| 문서에 없는 권한(CAR_SPEED 등) | △ | 튜토리얼 "Sample App 전체 public permission" 목록엔 있음. 문서 지침: "전체 권한 목록을 그대로 복사하지 않습니다". 조향각 API 실제 권한은 READ_CAR_STEERING이 아니라 CAR_DRIVE |
| 수동 절차 1~3 | ○ | |
| 4. Virtual Console 연결 | △ | URL 유효하나 연결법은 별도: `adb forward tcp:8088 tcp:8088` → `http://127.0.0.1:8088/` (/docs/connect/guide/getting-started/application-development/use-vehicle-emulator) |
| 4GB RAM 충분 | △ **(신규 리스크)** | "그래픽 카드가 필수", 최소 RTX 3060 Laptop 6GB / RX 6600M 8GB. Apple Silicon 언급 없음 → 1일차 구동 검증 |

추가: **Vehicle SDK 퍼미션은 Pleos App Market과 사전 협의·승인된 경우에만 선언 가능**(앱 심사 가이드 기능·성능·안정성). 공모전 에뮬레이터 개발엔 영향 없으나 실출시·PT 질의 대비 필요.

---

## 4. research/04-market-ev-charging.md — 출처 20건

| # | URL | 접근 | 수치 | 비고 · 정정 |
|---|---|---|---|---|
| 1 | clien.net/…/19238114 | ○ | △ | 수치 전부 일치하나 커뮤니티 퍼온 글. 대체: 경북매일(국토부 상반기 등록현황 보도) https://kbmaeil.com/article/20260730500195 . 국산/수입 분리 수치는 2차 출처에서만 확인 |
| 2 | newsspace.kr/…no=12025 | ○ | △ | 브랜드별 수치 일치. **13.1%는 이 기사에 없음**(→[3]) |
| 3 | fnnews.com/…202601201405544489 | ○ | ○ | KAMA 인용, 전 수치 일치. 13.1%는 "구매 비중". **[2] 대신 주 출처로** |
| 4 | hyundaimotorgroup.com/…pleos-connect… | ○ | △ | 그랜저 5월·2,000만 대 ○. **AAOS·아반떼·아이오닉 3·기아 2027 없음** |
| 5 | hyundaimotorgroup.com/…42dot…new-apps | ○ | △ | 18종·확대 카테고리 ○. "30개+"와 아이오닉 3는 없음(→[6b]). §5 "멜론·라디오 앱 4종" → **신규 음악·오디오 앱 4종(멜론, Radioline, 래디오오토, 픽클)** |
| 6a | dealsite.co.kr/articles/161078 | ○ | △ | 그랜저·2,000만 대만. **아반떼·기아 2027 없음**. 아반떼·AAOS 대체: https://dealsite.co.kr/articles/164280 |
| 6b | starnewskorea.com/…2026072214461811448 | ○ | ○ | 아이오닉 3 맞춰 연말 30개+ 일치 |
| 7 | koreascience.kr/…JAKO202433472009703.pdf | ○ | △ | 수치 6개 일치. **서울시·2022-10-07 하루치**, 이용률 정의 다름, 2.3/1.2회는 이용된 충전기 기준 |
| 8a | bizwatch …2021/11/16/0012 | ○ | △ | 47분 / 희망 30분 / 불편 1순위 ○ (**2021년**, 729명). 89%는 없음 |
| 8b | getcha.kr/…/47976 | ○ | △ | 89%는 커뮤니티 재게시 글에만. 원문 보고서 미확인 |
| 9 | motor.com/2024/03/… | ○ | ○ | 51%/60%/19%/약 4만 명 ○. "그중 60%" 모수 불명확. **"차 안에 머문다" 내용 없음** |
| 10 | evfleetworld.co.uk/?p=63172 | **×** (401) | ○ (대체) | 수치는 타 매체로 확인. 대체: https://www.gilbarco.com/eu/blog/its-about-time-ev-drivers-want-convenient-charging-and-theyre-willing-pay-it . **같은 조사: 차 안 체류 25%** |
| 11 | notateslaapp.com/…/949 | ○ | ○ | 20% 자동 종료 ○. 공백 주장과 부합 |
| 12 | getjerry.com/…/tesla-theater-mode | **×** (404) | — | 내용은 사실. 대체: https://www.notateslaapp.com/news/4598/tesla-adding-more-tesla-theater-apps |
| 13 | philip.greenspun.com/…dog-mode/ | ○ | △ | "시동 OFF 공조 유지"는 원문에 없음. 2021 개인 블로그 → 현대 공식 매뉴얼 유틸리티 모드로 교체 권장 |
| 14 | ownersmanual.hyundai.com/…/Petmode.html | ○ | ○ | §5 "외부 디스플레이 안내"는 근거 없음 |
| 15a | hyundai.com/…/ioniq5 | ○ | △ | 무중력 시트 ○, 레그서포트·휴식 언급 없음 |
| 15b | dealsite.co.kr/articles/70962 | ○ | △ | **아이오닉 5만**. 아이오닉 9: https://www.hyundai.com/contents/repn-car/catalog/ioniq9-catalog.pdf . 아이오닉 6 국내 근거 미확인 |
| 16a | biz.newdaily.co.kr/…2024032200269 | ○ | ○ | E-pit 2025 500기 "목표"(2024 기준 286기). 2026 실적 미확인 |
| 16b | etoday.co.kr/…2350233 | ○ | ○ | 제주 PnC는 2024.4~10 **6개월 한시 프로모션** |
| 17a | asiae.co.kr/…2018121808441948987 | ○ | △ | **2018년** 기사, 이마트 없음 |
| 17b | dailian.co.kr/…810537 | ○ | **×** | 현대오일뱅크 기사 — **출처 불일치**. 이마트 근거 0건 → 문구 삭제 또는 신규 출처 |
| 18 | covesa.atlassian.net/… | ○ | ○ | 세션 준비 토의 주제(공식 결론 아님) |
| 19 | adjust.com/…retention-rate | **×** (429) | △ | Adjust 자체 값 D1 26 / D7 13 / D30 7%. 범위값은 Phiture: https://phiture.com/mobilegrowthstack/managing-retention-rate-benchmarks-and-expectations/ . "AppsFlyer" 표기 근거 없음 |
| 20 | emarketer.com/…utility-driven-apps… | **×** (403) | ○ (스니펫) | Airship 2025 Q3 **북미** D30: 금융 17.2%, 헬스 13.1%, 엔터 4.2% |

집계(출처 URL 25개 기준): 접근 ○ 21 / × 4. 수치 ○ 10 / △ 13 / × 1 / 확인불가 1.
산술: 기아 60,609 + 현대 55,461 = 116,070 = 220,177의 52.7% → "약 11.6만·53%" ○ (제네시스 제외 값임을 명기).

### research/04 정정 문장
- §2: "급속 세션의 **절반은 차 안에 머무른다**" → "유럽 BEV 운전자 중 급속 충전 중 차 안에 머무는 비율은 **25%**(Gilbarco 2025), 북미 51%는 편의시설 이용(FLO). 차내 체류 수요는 '4명 중 1명'으로 보수 산정."
- §2 [7]: "실측 세션 길이(국내 충전 데이터)" → "서울시 충전기 하루(2022-10-07) 실측".
- §1: "기아는 2027년부터 적용 전망" → 삭제(또는 "미확인").
- §5-2: "멜론·라디오 앱 4종" → "신규 음악·오디오 앱 4종(멜론·Radioline·래디오오토·픽클)".
- 초안 문단: "급속 충전자의 약 절반은 차량 안에 머무르는" → "유럽 급속 충전자 4명 중 1명은 차량 안에 머무르며".
- [8]: 2021년 조사임을 명기.

---

## 5. 제출 체크리스트

### 접수양식 docx
- [ ] **01 참가신청서**: 참가 단위(개인/팀), 참가 부문(대학(원)생/일반), 팀명, 앱명, 대표자 성명·역할·이메일·휴대폰, 참가 인원, **작성일** 기입 (소속은 선택). ※ 01에는 별도 서명란 없음 — 서명은 04에서
- [ ] 01 B. 참가 구성원 표: 대표자 포함 **전원** 성명·소속·휴대폰·이메일·역할
- [ ] **02 앱 소개**: 섹션 1~6 작성(필수), 7 참고자료(repo URL·영상 링크·APK 경로), 8 숏폼 링크(선택)
- [ ] **02에 성명·연락처·이메일·소속 미기재** (전 페이지 검색: 팀원 실명, 학교명, 회사명, 이메일 도메인)
- [ ] 02 첨부·링크에도 실명·계정 노출 없음 — **GitHub 계정명/커밋 author/README/APK 파일명의 팀명**에 실명 없는지 확인
- [ ] **03 개인정보 수집·이용 동의서**: 동의 내용 확인 (03에는 별도 서명란 없음, 동의·서명은 04 표에서)
- [ ] **04 대표자·팀원 동의 확인 및 서명**: 대표자 + **모든 팀원** 각 행에 성명 / ☑동의 / **본인 서명** / **서명일자** (참조수신으로 대체 불가)
- [ ] 04 하단 앱명·팀명 기입
- [ ] 파일 형식: 배포 양식 DOCX 유지 (PDF 허용 여부 규정 없음 → DOCX + 서명 반영본. 서명 이미지 삽입 또는 스캔본 병행, 추정)

### 이메일
- [ ] 수신: appdevchallenge@hyundai-partners.com
- [ ] 제목 정확히: `[차량용 앱 개발 공모전] 팀명(개인은 성명)_대표자명`
- [ ] 팀 참가: **CC에 팀원 전원** 이메일 (CC 주소가 서로 보인다는 점 팀원에게 사전 안내)
- [ ] 본문에 repo URL, APK 경로(또는 Release URL), 시연 영상 링크 기재 (권장, 추정)
- [ ] 마감 **2026-11-15(일) 23:59 KST** 전 발송, 발송 후 보낸편지함·첨부 열림 확인

### GitHub (선택·가산점)
- [ ] 소스 코드 + `apk/팀명_앱이름_release.apk` (실행 가능한 **release** 빌드; 용량 초과 시 GitHub Releases 등록 후 Release 페이지 URL 제출)
- [ ] README 필수 4항목: ① 앱 이름 및 한 줄 소개 ② 실행 방법 ③ 테스트 환경 및 필요한 설정 ④ APK 파일 위치 또는 GitHub Release 링크
- [ ] 비공개 repo면: **공지된 심사용 GitHub 계정을 Collaborator로 초대** + 마감 전 접근 확인 (※ 2026-11-03 현재 계정명 **미공지** → 공개 repo 권장 또는 공지 모니터링/문의)
- [ ] 비공개 개인 repo는 Collaborator에게 쓰기 권한까지 부여됨을 인지
- [ ] 저장소에 키·토큰·서명키(keystore)·로컬 경로·실명 없음

### 시연 영상 / 숏폼 (선택)
- [ ] 시연 영상: 목업·프로토타입·화면녹화 인정, 길이·형식 규정 없음. 링크 공개 범위(일부 공개) 확인, 채널명에 실명 없음
- [ ] 숏폼: 30초~1분 세로, 개인/팀 계정 Instagram 릴스 또는 YouTube Shorts, `#HMG차량용앱개발공모전` 포함 해시태그 2~3개, 링크를 02-8번에 기재

### 재제출 (마감 전)
- [ ] **처음 제출한 것과 같은 발신 이메일 주소**
- [ ] 제목: `[수정제출] [차량용 앱 개발 공모전] 팀명(개인은 성명)_대표자명`
- [ ] **전체 제출 자료 + 서명한 동의서 전부 재첨부** (변경분만 보내면 무효)
- [ ] 팀원 전원 CC
- [ ] 마감 전 마지막 발송본이 최종 심사본

### 2차 대비 (1차 합격 20팀)
- [ ] 12/3~4 강남대로사옥 UX스튜디오, 에뮬레이터 구동 화면 또는 기기 시연 준비
- [ ] 2차 PT 심사 기준 공지 모니터링 (현재 미공지)
- [ ] 최종 10팀: 전시용 최종 APK 제출, 2027-02 행사 현장 시연·시상식 참석

---

## 6. 추가 검증 (PM 변경 지시, 2026-11-03)

### 6-1. 경쟁 주장 — 기아 EV3/EV9·아이오닉 9에 "휴식/릴렉션 모드" 소프트웨어 시나리오가 있는가

| 차종 | 확인된 것 | 확인 안 된 것 | 근거 |
|---|---|---|---|
| 기아 EV3 | **"충전 중 휴식"을 공식 마케팅.** "Comfortably relax inside while charging as the infotainment screen displays the charging status" / 충전은 "automatically stops when the driver-set battery level is reached". 앞좌석 릴렉션 시트(원터치 리클라인 버튼, 트림별), 앰비언트 라이트, 슬라이딩 테이블. 스페인 매체는 "Relax Mode"를 "충전 중 쉬기 위해 시트를 평소보다 더 젖히는 모드"로 소개(2024-05-23) | 리클라인·조명·공조를 **한 번에** 바꾸는 소프트웨어 시나리오, 충전 완료 시 자동 복원·알림 | kia.com/cr/showroom/NewEV3/features.html , testcoches.es(2024-05-23), kia.com/au EV3 features |
| 기아 EV9 | 2열(트림별 1열) 릴렉션 시트, 마사지(다이내믹 바디케어), 전용 디스플레이에서 마사지 세기 조절. ccNC 탑재 | 조명·공조 일괄 제어, 충전 연동 | hyundaimotorgroup.com/ko/story/kia-ev9-three-stories… , 기아 EV9 카탈로그 |
| 아이오닉 9 | 릴렉션 시트(카탈로그), 앰비언트 무드램프, 정차 시 2열 스위블 | "릴렉션 모드" 소프트웨어 시나리오 | hyundai.com/worldwide/ko/brand-journal/ioniq/ioniq-9-global-launch , ioniq9-catalog.pdf |

판정: **△ (반증도 확증도 못 함).** 공식 자료에서 확인되는 "릴렉션"은 모두 **시트 자세(원터치 버튼) 하드웨어 기능**이고, 조명·공조를 묶어 바꾸거나 충전 완료와 연동해 복원하는 소프트웨어 시나리오는 찾지 못함. 단, **오너스 매뉴얼(webmanual.kia.com / ownersmanual.hyundai.com) 원문은 열람 못 함** → "없다"고 단정하면 안 됨. 그리고 **EV3는 '충전 중 차 안에서 휴식'이라는 사용 맥락 자체를 이미 OEM이 마케팅** 중이며, 목표 SOC 도달 시 자동 충전 중지는 순정 기능이다.

**PRD §2 3번째 불릿 수정 제안**
- 현재: "현대·기아는 릴렉션 시트·유틸리티 모드·펫 모드 하드웨어가 있지만 '충전과 연동된 하나의 시나리오'가 없다 (research/04 §3). [v0.3] 기아 EV3/EV9·아이오닉 9 ccNC의 휴식 모드 유무는 검수 세션이 확인 후 문장 확정."
- 제안: "기아 EV3는 '충전 중 차 안에서 휴식'을 공식 마케팅하고, 릴렉션 시트(원터치 리클라인)와 목표 SOC 자동 충전 중지는 이미 순정 기능이다. 그러나 공개 자료 기준으로 시트·조명·공조를 **한 번에** 휴식 상태로 바꾸고, **충전 완료 시점에 맞춰 깨우고 원래대로 되돌리는** 시나리오는 확인되지 않는다(2026-11 기준, 오너스 매뉴얼 미확인). 이 앱의 차별점은 '젖히기'가 아니라 **'끝내기'(완료 알림 + 자동 복원 + 하차 점검)의 자동화**다."
- PT 대비: 심사위원 질문 "EV3 릴렉션 모드와 뭐가 다르냐"에 대한 답을 위 문장 그대로 준비. "어디에도 없다"는 표현은 금지(반증 리스크).

### 6-2. 주행 규제 정책 — 명문 여부 (https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy)

| 주장 | 판정 | 원문 |
|---|---|---|
| "DO 미선언 앱은 주행 중 사용 불가" | **○ 명문** | "앱에서 Distraction Optimization(DO)를 선언하지 않았을 때, 주행 상황에 따라 앱 사용 가능 여부가 달라집니다." + 표: Parking(주차) "사용 가능" / Drive(주행) "**사용 불가**" |
| "시트·차량 설정 제어는 주행 중 전 지역 금지" | **△ 표로만 명시(문장 없음)** | 지역별 표(북미/유럽/그외) 열 "디지털키, 도어, 시트, 차량 설정"(UX_RESTRICTIONS_NO_SETUP(64)) — Driving 행 **⛔ 3개 지역 모두**. 단서: "플랫폼 버전과 양산 차량에 따라 주행 규제 정책이 다를 수 있습니다." |

참고: app-category 페이지에는 DO 선언 방법·차량 제어 규정이 **없음**. 주행 중 제한적 허용 카테고리로 Music/Audio, Maps/Navigation, Mobility Services만 예시("일부 카테고리는 … 제한적으로 허용될 수 있습니다").
PRD 반영: DO 미선언으로 두면 주행 중 앱 자체가 안 뜸 → PRD §4-5 "주행 중 상태 텍스트 1줄"을 유지하려면 DO 선언 + 주행 중 시트·설정 제어 완전 차단(UX_RESTRICTIONS_NO_SETUP 확인) 필요. 가장 단순한 선택: **DO 미선언**(주행 시 시스템이 앱을 가림) → §4-5 삭제.

### 6-3. CONTROL_CAR_SEATS / LIGHTS / CLIMATE 사전 승인 여부

| 출처 | 판정 | 원문 |
|---|---|---|
| 앱 심사 가이드 — 기능·성능·안정성 (/distribute/app-review-guide/review-functionality-performance-stability) | **○ 명문 (Vehicle SDK 퍼미션 전체 대상)** | "Vehicle SDK 퍼미션은 Pleos App Market과 사전 협의 및 승인된 경우에만 선언할 수 있습니다." / "승인되지 않은 퍼미션을 선언한 앱은 심사를 통과할 수 없습니다." / "차량 상태를 변경하거나 운전 안전에 영향을 줄 수 있는 SDK·API는 차량 제어 및 운전 안전을 위해 별도의 심사 기준이 적용됩니다." |
| 앱 제출 가이드 (/distribute/app-submission-guide) | △ 간접 | "SDK 추가 권한: 권한 신청이 필요한 SDK를 사용하는 앱일 경우, My Project > 프로젝트 정보 > 프로젝트 ID를 입력". CONTROL_CAR_* 개별 명시 없음. 문의처 partnership@pleos.ai |

결론: CONTROL_CAR_SEATS/LIGHTS/CLIMATE를 **이름으로 지목한 문장은 없음**. 그러나 사전 승인 규정은 "Vehicle SDK 퍼미션" **전체**(읽기 CAR_* 포함)에 걸려 있고, 제어 권한은 "별도 심사 기준" 대상이 추가로 적용 → 사전 승인 대상으로 보는 것이 맞음. 공모전(에뮬레이터)에는 영향 없음. 기획서 5번에 "출시 시 Vehicle SDK 권한 사전 협의 예정(My Project 프로젝트 ID 등록)" 1줄 권장.
