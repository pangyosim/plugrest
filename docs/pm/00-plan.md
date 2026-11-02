# PM 플랜 — HMG 차량용 앱 공모전 (작성 2026-11-03)

## 마일스톤
| 날짜 | 산출물 | 상태 |
|---|---|---|
| 11-03 (D-12) | 리서치, 아이디어 확정 | 진행 중 |
| 11-04 | PRD 확정, 화면 플로우, 에뮬레이터 설치(사용자) | |
| 11-05~08 | MVP 코드: SDK 연동 + 핵심 플로우 1개 동작 | |
| 11-09~11 | UI 다듬기, TTS, 자동 복원, 시연 스크립트 | |
| 11-12 | 시연 영상 녹화, release APK, GitHub README | |
| 11-13 | 기획서 docx 완성, 리뷰 | |
| 11-14 | 제출 (하루 버퍼) | |
| 11-23 | 1차 결과 | |
| 12-03~04 | 2차 PT: 동작 앱 + 발표자료 | |

## 역할 분담 (에이전트 세션)
| 역할 | 담당 | 입력 | 산출물 |
|---|---|---|---|
| PM (이 세션) | Claude | 전체 | 의사결정, 통합, 리뷰, 보고 |
| 리서치 | general-purpose x2 | docs/research | 02-ideation-independent, 04-market |
| 기획 | spg:planner / product-lens | 01-ideation + research | docs/pm/10-prd.md |
| 개발 | general-purpose (Kotlin/Compose) + spg:kotlin-reviewer | PRD, 샘플앱 패턴 | app/ 소스, APK |
| 디자인 | design:design-critique, spg:frontend-design-direction | PRD | 화면 시안(HTML 목업), 토큰 |
| 제안서 | 작성 에이전트 + spg:agent-evaluator | PRD, research | docs/submission/proposal.md → docx 채움 |
| 영상 | Runway MCP / 화면 녹화 | 앱 | 시연 영상, 숏폼(선택) |
| QA | spg:code-reviewer, spg:security-reviewer | 소스 | 리뷰 리포트 |

## 사용자(사람)만 할 수 있는 일
- 참가 부문(대학/일반), 팀 구성·이름, 서명 → docx 01/03/04
- Android Studio에서 Pleos 에뮬레이터 설치 (docs/research/03 참고)
- ~~GitHub repo 생성~~ (완료: https://github.com/pangyosim/plugrest, 공개) · 이메일 제출
- 외부 코드 클론/빌드 권한 허용 (auto mode에서 에이전트 차단됨)
- 앱명 최종 확인(약 10분): KIPRIS 9류·42류 "충전쉼표", "Plug & Rest" 검색, Instagram 핸들 plugrest, .app 도메인 — 절차는 docs/submission/name-check.md
- 숏폼 게시용 팀/앱 계정 생성(실명 미노출)

## 결정 로그
- 2026-11-03: 추천 아이디어 B(충전 대기 휴식 모드). 사용자 확정 대기.
- 2026-11-03: 독립 검토(02-ideation-independent)는 A(뒷좌석 펫·키즈 케어) 추천. 시장조사(04)에서 현대 ccNC에 이미 펫 모드가 있고 테슬라 Dog Mode도 있어 A는 창의성 감점 위험, 반면 "SOC 연동 시트·조명·공조·완료알림" 조합은 어디에도 없음이 확인됨. **결정: B를 코어로, A의 '동승자 케어'를 두 번째 씬(Phase 2)으로 흡수.** 사용자 이의 시 변경.

## 세션 배정 (2026-11-03, 재배정 후 — 이 프로젝트 세션만)
| 세션 | 과제 | 산출물 |
|---|---|---|
| 개발 [b463f3] | SDK 시그니처 조사 → 앱 MVP 전체 구현 | docs/research/05-sdk-signatures.md |
| 기획 [513d81] | 앱 이름, PT 구성, 시연 스크립트, Q&A 20 + 제안서 섹션 1~5 소유 | docs/pm/20-pt-scenario.md |
| 디자인 [951c7f] | 3화면 목업·토큰 + 아이콘 + 스토리보드 | docs/design/icon.svg, storyboard.md |
| 마케팅 [c0b8f8] | 섹션 6 사업성 + 숏폼 기획 + 앱마켓 소개문 | docs/submission/shortform.md |
| 검수 [a6f6a2] | 팩트체크 + 제출 체크리스트 + PRD 레드팀 | docs/pm/31-factcheck.md |

- 2026-11-03: 역할명 중복 세션(타 프로젝트 Artella 팀)에 지시가 잘못 나가 취소. 앱명 확정: 충전쉼표 / Plug & Rest. 기획B Q&A 반영분(충전한도 스냅샷, TTS 예고 후 지연 복원, 브레이크 시 선제 복원) PRD v0.2에 반영.
- 2026-11-03: 레드팀 리뷰(30-review-prd.md, 타 팀 검수 세션 산출물이나 품질 높아 채택) R1~R10 전부 PRD v0.3 반영. 동승자 케어(펫/키즈) 씬은 PRD에서 제외하고 여기 확장 아이디어로만 보관. NaviHelper·FusedLocation 미사용. 비 EV 차량용 수동 휴식 모드를 Phase 2로.
- 2026-11-03: SDK 바이너리 조사 결과 setEvChargePercentLimit 원래값 조회 API 없음 → 차량 충전 한도는 변경하지 않기로 결정(목표 SOC는 앱 내부 값). 기어 API는 2.3.2 바이너리에 존재(Gear.registerGearSelection). PRD v0.4.
- 2026-11-03: 팩트체크(31) 반영 PRD v0.5. 주요 정정: 차내 체류 "절반"→25%(Gilbarco), 47.2분은 서울 하루치 실측, "기아 2027" 근거 없음→추정 표기, Vehicle SDK 권한 전체 사전 승인 필요, 차속은 AAOS PERF_VEHICLE_SPEED로 가능. 30-review-prd.md는 검수B가 덮어씀(원본은 타 팀 세션 산출물, 핵심은 v0.3에 이미 반영됨 — 재요청 안 함).
- 사용자 TODO 추가: 에뮬레이터가 Apple Silicon에서 부팅되는지 1일차 검증(GPU 요구사항 문서화됨).
- 2026-11-03: 제출 이메일 초안(docs/submission/email-draft.md) 완료. 개인 참가 시 제목은 규정 문자 그대로 "{성명}_{성명}" — 애매하면 주최 측 문의(사용자).
- 2026-11-03: 앱 첫 빌드 통과(assembleDebug/test/assembleRelease, 단위테스트 5/5). 데모 모드 기본 ON(-Pdemo=false로 해제), Fake 자동 전환(-Pfake=true 강제). 코드 리뷰(검수)·화면 대조(디자인)·스크린샷/Fake 녹화(개발) 병렬 진행.
- 2026-11-03: 코드 리뷰(32-code-review.md) 차단 2(서비스 조기 stopSelf, 제한 진입 시 시트 제어 미중단)·높음 4 → 개발 선수정 지시, 스크린샷·녹화는 그 뒤로. 개인정보처리방침은 마케팅이 작성.
- 2026-11-03: 개인정보처리방침 초안 완료(docs/submission/privacy-policy.md, 코드 대조 완료). 문의 이메일·시행일 플레이스홀더는 사용자 기입.
- 2026-11-03: 코드 리뷰 차단·높음 수정 완료(테스트 8/8, B1은 AVD 실측 확인). 스크린샷 6장·Fake 녹화 81초·apk/·.gitignore 준비. 결정: 충전 판정 90초 히스테리시스, Done 10분 타임아웃. 다음: 검수 재검, 디자인 2차 대조, 기획 시안 PDF·docx, 마케팅 영상 편집.
- 2026-11-03: git init + 첫 커밋 + 공개 저장소 https://github.com/pangyosim/plugrest 푸시(사용자 지시). 커밋 author는 GitHub noreply 이메일로 설정(개인 이메일 비노출). 개발 세션 사용 한도 도달 → 재촬영은 서브에이전트가 수행. 영상 v1(demo_fake_v1.mp4 75초, shortform_v1.mp4 45초, 무음) 완료.
- 사용자 확인 필요: GitHub 계정명 pangyosim이 실명 기반이면 "공유 링크에 개발자 실명 노출 금지" 규정 저촉 가능 → 팀/앱 이름 조직 계정(예: plugrest-app) 생성 후 repo 이전 권장.
