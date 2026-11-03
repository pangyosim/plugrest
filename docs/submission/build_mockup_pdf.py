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
    ("07-idle-learned.png", "충전 속도 학습 — 두 번째 충전부터 대기 화면에 '지난 충전 n회 기준, 80%까지 보통 약 N분'"),
    ("08-calculating-learned.png", "학습값 보조 — '계산 중' 동안에도 지난 충전 기준 예상 시간을 함께 보여 준다"),
    # 09-resting-measured-tag.png는 제외(학습 25분 → 실측 20분이 '학습값 오류'로 읽힐 수 있음). PT 대체 자원으로만 보관
]
FONT = "/System/Library/Fonts/AppleSDGothicNeo.ttc"  # ponytail: macOS 전용 한글 폰트 경로
W, H, M = 1240, 1754, 80  # A4 세로 150dpi
f = lambda size: ImageFont.truetype(FONT, size)

# 표지: keyvisual을 하단에 깔고, 그 위 단색 남색 영역에 글자를 올린다(이미지 위에 글자를 겹치지 않아 가독성 확보)
kv = Image.open(f"{D}/design/brand/keyvisual.png").convert("RGB")
kv = kv.resize((W, round(kv.height * W / kv.width)))
cover = Image.new("RGB", (W, H), kv.getpixel((10, 10)))
cover.paste(kv, (0, H - kv.height - 140))
d = ImageDraw.Draw(cover)
d.text((M, 260), APP, font=f(72), fill="white")
d.text((M, 380), "화면 시안", font=f(44), fill=(255, 199, 90))  # 앰버 #FFC75A
for i, line in enumerate(ONE_LINER):
    d.text((M, 500 + i * 44), line, font=f(28), fill=(220, 226, 240))
d.text((M, H - M - 40), "화면은 일반 Android Automotive 에뮬레이터 + Fake 차량 모드(시간 단축 데모)로 촬영", font=f(24), fill=(170, 180, 205))
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
