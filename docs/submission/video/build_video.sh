#!/usr/bin/env bash
# 시연 영상(가로 1920x1080)과 숏폼(세로 1080x1920 45초)을 raw_fake_01.mp4에서 다시 만든다.
# 필요: ffmpeg, swift (macOS 기본). 사용: ./build_video.sh [원본.mp4]
# ponytail: ffmpeg에 drawtext/libass가 없어서 자막은 render_text.swift가 PNG로 만들고 overlay/stack으로 합성한다.
#           음성 없음(TTS는 Pleos 에뮬레이터로 녹화할 때 넣는다). 원본 구간(초)은 raw_fake_01(2026-11-03 08:25 녹화, 79초) 기준이라 재녹화하면 SEG 표의 시간을 고친다.
set -euo pipefail
cd "$(dirname "$0")"
SRC="${1:-raw_fake_01.mp4}"
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
ENC=(-c:v libx264 -preset medium -crf 20 -pix_fmt yuv420p -r 30 -an)

BG=0E0F12; W=F5F5F5; G=AAB0B6; M=43E0A0; A=FFC75A

# ---------- 1) 자막·카드 PNG ----------
cat > "$T/spec.txt" <<EOF
title.png|1920|1080|$BG|center|b150,$W,충전쉼표|52,$G,Plug & Rest|b64,$M,충전은 차가, 휴식은 당신이.|38,$A,시간 단축 데모 · Fake 차량 패널로 녹화한 화면
end.png|1920|1080|$BG|center|b90,$W,충전쉼표 (Plug & Rest)|44,$W,① 충전이 감지되면 원탭으로 시트·조명·공조를 휴식 상태로|44,$W,② 실제 충전 속도로 남은 시간 계산, 목표에 닿으면 음성으로 깨움|44,$W,③ 10초 뒤 자동 복원 + 내리기 전 창문·실내등·뒷좌석 확인|34,$M,Pleos Vehicle SDK (EvBattery · Seat · Light · HVAC) · TextToSpeech
d1.png|1140|1080|$BG|left|b32,$M,STEP 1 · 충전 감지|b64,$W,충전이 감지되면\n버튼이 켜집니다|36,$G,주차(P) 상태에서 충전 포트가 연결되고\n배터리가 오르면 '휴식 모드 시작'이 활성화됩니다.\n\n아래 Fake 차량 패널로 차량 신호를 바꾸는 모습입니다.
d2.png|1140|1080|$BG|left|b32,$M,STEP 2 · 원탭 휴식|b64,$W,시트 · 조명 · 공조를\n한 번에|36,$G,등받이 리클라인, 앰비언트 어둡게, 실내등 끔,\n공조 22°C · 풍량 1단.\n패널 아래에 실제 SDK 호출이 찍힙니다.|34,$A,음성 안내 (에뮬레이터 녹화 시)\n"휴식 모드를 시작합니다.\n목표 80%에 도달하면 깨워 드릴게요."
d3.png|1140|1080|$BG|left|b32,$M,STEP 3 · 남은 시간|b64,$W,처음엔 '계산 중…'|36,$G,충전 속도를 잠시 지켜본 뒤 계산합니다.\n(실제 3분 → 데모 빌드 20초, 화면 2배속)
d4.png|1140|1080|$BG|left|b32,$M,STEP 3 · 남은 시간|b64,$W,실제 충전 속도로\n계속 다시 계산|36,$G,약 30분 → 15분 → 10분 → 5분\n휴식을 방해하지 않게 화면은 어둡게,\n움직임 없이.
d5.png|1140|1080|$BG|left|b32,$M,STEP 4 · 목표 도달|b64,$W,목표에 닿으면 깨우고\n원래대로|36,$G,10초 카운트다운 뒤 시트·조명·공조를 복원합니다.\n'취소'하면 지금 상태 그대로 끝냅니다.|34,$A,음성 안내 (에뮬레이터 녹화 시)\n"목표 80%에 도달했습니다.\n10초 후 시트를 원래대로 되돌립니다."
d6.png|1140|1080|$BG|left|b32,$M,STEP 5 · 하차 전 확인|b64,$W,내리기 전에\n한 번 더|36,$G,복원이 끝나면 열린 창문, 켜진 실내등,\n뒷좌석 탑승자를 한 줄로 알려줍니다.
s_hook.png|1080|1920|000000|center|b130,$W,충전 47분,|b110,$M,아직도\n폰만 보세요?
s_end.png|1080|1920|000000|center|b84,$W,충전은 차가,\n휴식은 당신이.|44,$G,Pleos Connect 앱|b72,$M,충전쉼표|40,$G,Plug & Rest
s_bot.png|1080|220|$BG|center|30,$G,충전쉼표 · 시간 단축 데모 (Fake 차량 패널)
s2.png|1080|220|$BG|center|b60,$W,충전 꽂으면, 차가 먼저 알아챕니다
s3.png|1080|220|$BG|center|b72,$M,목표 80%. 원탭.
s4.png|1080|220|$BG|center|b56,$W,시트 젖히고 · 조명 낮추고 · 공조 22°C
s5.png|1080|220|$BG|center|b52,$W,차가 직접 말해줍니다|36,$A,"목표 80%에 도달하면 깨워 드릴게요."
s6.png|1080|220|$BG|center|b52,$W,몇 분 뒤, 충전 속도로 남은 시간 계산
s7.png|1080|220|$BG|center|b52,$W,목표에 닿으면 깨워주고, 원래대로|36,$A,"목표 80%에 도달했습니다."
s8.png|1080|220|$BG|center|b64,$W,내리기 전 체크까지
EOF
swift render_text.swift "$T/spec.txt" "$T"

# 화면 녹화는 가변 프레임(정지 화면 구간엔 프레임 없음) → 구간 자르기 전에 30fps 고정으로 바꿔야 시각이 밀리지 않는다.
ffmpeg -v error -y -i "$SRC" -vf fps=30 -c:v libx264 -preset veryfast -crf 16 -an "$T/cfr.mp4"
SRC="$T/cfr.mp4"

# ---------- 2) 조각 만들기 ----------
card() { # png 길이 출력
  ffmpeg -v error -y -loop 1 -framerate 30 -t "$2" -i "$T/$1" -vf "fade=in:d=0.4,fade=out:st=$(echo "$2-0.4" | bc):d=0.4" "${ENC[@]}" "$3"
}
seg() { # 시작 끝 배속 자막png 출력 모드(demo|short)
  local dur; dur=$(echo "($2-$1)/$3" | bc -l)
  if [ "$6" = demo ]; then
    # 왼쪽: 세로 원본 625x1000 (여백 포함 780x1080) | 오른쪽: 자막 패널 1140x1080
    ffmpeg -v error -y -ss "$1" -t "$(echo "$2-$1" | bc)" -i "$SRC" -loop 1 -framerate 30 -i "$T/$4" -filter_complex \
      "[0:v]setpts=(PTS-STARTPTS)/$3,fps=30,scale=-2:1000,pad=780:1080:100:40:0x$BG[v];[v][1:v]hstack=shortest=1" \
      -t "$dur" "${ENC[@]}" "$5"
  else
    # 위 자막 220 | 원본 925x1480 | 아래 고정 띠 220  = 1080x1920
    ffmpeg -v error -y -ss "$1" -t "$(echo "$2-$1" | bc)" -i "$SRC" -loop 1 -framerate 30 -i "$T/$4" -loop 1 -framerate 30 -i "$T/s_bot.png" -filter_complex \
      "[0:v]setpts=(PTS-STARTPTS)/$3,fps=30,scale=-2:1480,pad=1080:1480:(ow-iw)/2:0:0x$BG[v];[1:v][v][2:v]vstack=3:shortest=1" \
      -t "$dur" "${ENC[@]}" "$5"
  fi
}
join() { # 출력 조각...
  local out=$1; shift; : > "$T/list.txt"
  for f in "$@"; do echo "file '$f'" >> "$T/list.txt"; done
  ffmpeg -v error -y -f concat -safe 0 -i "$T/list.txt" -c copy -movflags +faststart "$out"
}

# ---------- 3) 시연 영상 (약 75초) ----------
card title.png 4 "$T/a0.mp4"
seg 0  11 1 d1.png "$T/a1.mp4" demo   # 충전 포트 ON → 버튼 활성
seg 11 20 1 d2.png "$T/a2.mp4" demo   # 원탭 시작, 패널에 SDK 호출 로그
seg 20 34 2 d3.png "$T/a3.mp4" demo   # 계산 중… (2배속)
seg 34 48 1 d4.png "$T/a4.mp4" demo   # 약 30분 → 5분
seg 48 61 1 d5.png "$T/a5.mp4" demo   # 10초 카운트다운 → 복원
seg 61 73 1 d6.png "$T/a6.mp4" demo   # 목표 80% 도달 + 하차 체크
card end.png 6 "$T/a7.mp4"
join demo_fake_v1.mp4 "$T"/a{0..7}.mp4

# ---------- 4) 숏폼 (45초, shortform.md 장면표) ----------
card s_hook.png 2 "$T/b1.mp4"
seg 4  9  1     s2.png "$T/b2.mp4" short  # 충전 감지
seg 9  12 1     s3.png "$T/b3.mp4" short  # 80% 원탭
seg 12 21 1     s4.png "$T/b4.mp4" short  # 시트·조명·공조 변경 로그
seg 21 26 1     s5.png "$T/b5.mp4" short  # 계산 중 + 시작 안내
seg 26 48 3.143 s6.png "$T/b6.mp4" short  # 남은 시간 갱신 (약 3.1배속 → 7초)
seg 48 60 2     s7.png "$T/b7.mp4" short  # 카운트다운 (2배속 → 6초)
seg 64 68 1     s8.png "$T/b8.mp4" short  # 창문 열림 체크
card s_end.png 4 "$T/b9.mp4"
join shortform_v1.mp4 "$T"/b{1..9}.mp4

for f in demo_fake_v1.mp4 shortform_v1.mp4; do
  printf '%s  ' "$f"; ffprobe -v error -select_streams v -show_entries stream=width,height:format=duration -of csv=p=0 "$f" | tr '\n' ' '; echo
done
