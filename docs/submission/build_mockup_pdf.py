"""docs/design/screenshots/*.png -> docs/submission/화면시안_충전쉼표.pdf (표지 + 화면당 1장, 설명 1줄).
실행: cd docs && /usr/bin/python3 submission/build_mockup_pdf.py "$PWD"   (Pillow 필요)
재촬영 시 파일명이 바뀌면 SHOTS만 고친다. 설명은 실제 화면을 보고 맞춘 문장이다."""
import sys
from PIL import Image, ImageDraw, ImageFont

D = sys.argv[1]
APP = "충전쉼표 (Plug & Rest)"
ONE_LINER = ("전기차 충전 중 원탭으로 시트·조명·공조를 휴식 공간으로 바꾸고, 남은 시간을 분으로 보여 주며,",
             "목표 충전량에 이르면 음성으로 깨워 원래 상태로 되돌려 주는 Pleos Connect 충전 대기 앱")
SHOTS = [
    ("01-idle.png", "대기 — 충전 중임을 확인하면 목표(80/90%)와 시트 통풍을 고르고 '휴식 모드 시작'을 누른다"),
    ("02-resting-calculating.png", "휴식 시작 — 시트·조명·공조를 한 번에 바꾸고, 남은 시간은 실제 충전 속도를 볼 때까지 '계산 중'"),
    ("03-resting-30min-rearseat.png", "휴식 중 — 남은 시간을 분으로 표시. 뒷좌석에 사람이 있어 등받이를 절반만 젖혔음을 알린다"),
    ("04-restoring.png", "목표 도달 — 음성 안내 후 10초 카운트다운, '취소'를 누르면 지금 상태 그대로 끝낸다"),
    ("05-done-restored-3warnings.png", "복원 완료 + 하차 체크 — 뒷좌석 사람·조수석 창문·실내등을 한 줄씩 안내한다"),
    ("06-done-kept.png", "취소한 경우 — 시트·조명·공조는 그대로 두고, 하차 체크만 보여 준다"),
]
FONT = "/System/Library/Fonts/AppleSDGothicNeo.ttc"  # ponytail: macOS 전용 한글 폰트 경로
W, H, M = 1240, 1754, 80  # A4 세로 150dpi
f = lambda size: ImageFont.truetype(FONT, size)

cover = Image.new("RGB", (W, H), "white")
d = ImageDraw.Draw(cover)
d.text((M, 600), APP, font=f(72), fill="black")
d.text((M, 720), "화면 시안", font=f(44), fill=(90, 90, 90))
for i, line in enumerate(ONE_LINER):
    d.text((M, 840 + i * 44), line, font=f(28), fill=(40, 40, 40))
d.text((M, H - M - 40), "화면은 일반 Android Automotive 에뮬레이터 + Fake 차량 모드(시간 단축 데모)로 촬영", font=f(24), fill=(110, 110, 110))
pages = [cover]

for n, (name, cap) in enumerate(SHOTS, 1):
    shot = Image.open(f"{D}/design/screenshots/{name}").convert("RGB")
    page = Image.new("RGB", (W, H), "white")
    d = ImageDraw.Draw(page)
    d.text((M, M - 20), f"{n}/{len(SHOTS)}", font=f(32), fill=(90, 90, 90))
    shot.thumbnail((W - 2 * M, H - 2 * M - 160))
    page.paste(shot, ((W - shot.width) // 2, M + 40))
    d.text((M, H - M - 50), cap, font=f(26), fill=(30, 30, 30))
    pages.append(page)

out = f"{D}/submission/화면시안_충전쉼표.pdf"
pages[0].save(out, save_all=True, append_images=pages[1:], resolution=150)
print("saved", out, len(pages), "pages")
