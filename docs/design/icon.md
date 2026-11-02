# 앱 아이콘

- 원본: `icon.svg` (512 viewBox). 초승달(휴식, 앰버 `#FFC75A`) + 번개(충전, 민트 `#43E0A0`), 남색 원판 `#16233F`.
- 원판이 자체 배경이 되므로 다크/라이트 런처 둘 다 가독. 96px 축소에서도 형태 유지 확인함.
- `icon-512.png`: 이미 내보낸 512px 투명 PNG (공모전 제출용 이미지 등).

## 512px PNG 다시 내보내기
```bash
sips -s format png -z 512 512 docs/design/icon.svg --out docs/design/icon-512.png
```
(`paint-order` 등 sips가 모르는 SVG 속성은 쓰지 않음. 수정 시에도 지킬 것.)

## 앱에 넣기 (AAOS 런처)
Android Studio > res 우클릭 > New > Image Asset > Launcher Icons (Adaptive and Legacy)
- Foreground: Path = `icon.svg`, Resize 약 80% (원판이 safe zone 안에 들어오게)
- Background: Color `#16233F`
- 결과는 `mipmap-anydpi-v26/ic_launcher.xml` + 밀도별 webp. 매니페스트 `android:icon="@mipmap/ic_launcher"`.
