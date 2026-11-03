#!/usr/bin/env bash
# 시연 영상(가로 1920x1080)과 숏폼(세로 1080x1920 45초)을 raw_fake_04.mp4에서 다시 만든다.
# 필요: ffmpeg, swift (macOS 기본). 사용: ./build_video.sh [원본.mp4]
# ponytail: ffmpeg에 drawtext/libass가 없어서 자막은 render_text.swift가 PNG로 만들고 overlay/stack으로 합성한다.
#           음성 없음(TTS는 Pleos 에뮬레이터로 녹화할 때 넣는다). 원본 구간(초)은 raw_fake_04(출발 시각 모드 "2분 후" 데모 칩, 167초, 도달 시 배터리 52%) 기준이라 재녹화하면 SEG 표의 시간을 고친다.
set -euo pipefail
cd "$(dirname "$0")"
SRC="${1:-raw_fake_04.mp4}"
BRAND="$PWD/../../design/brand"
# 도달 TTS 자막의 배터리 %. 녹화본에서 출발 시각 도달 순간의 SOC를 보고 넣는다(가정 값 금지). 사용: N_END=52 ./build_video.sh (raw_fake_04 = 52)
: "${N_END:?raw 녹화의 도달 시 배터리 %를 N_END로 지정하세요}"
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
ENC=(-c:v libx264 -preset medium -crf 20 -pix_fmt yuv420p -r 30 -an)

BG=0E0F12; W=F5F5F5; G=AAB0B6; M=43E0A0; A=FFC75A

# ---------- 1) 자막·카드 PNG ----------
cat > "$T/spec.txt" <<EOF
title_txt.png|1000|540|none|left|b130,$W,충전쉼표|44,$G,Plug & Rest|b54,$M,충전은 차가, 휴식은 당신이.|34,$A,시간 단축 데모 · Fake 차량 패널
end.png|1920|1080|$BG|center|b90,$W,충전쉼표 (Plug & Rest)|b48,$A,차가 알람이 됩니다.|44,$W,① 충전 중 원탭으로 시트·조명·공조를 휴식 상태로|44,$W,② 출발 시각이나 목표 배터리에 맞춰 조명을 천천히 밝히고 음성으로 깨움|44,$W,③ 10초 뒤 자동 복원 + 내리기 전 확인|44,$W,④ 두 번째 충전부터 지난 충전 기준으로 바로 예측|34,$M,Pleos Vehicle SDK (EvBattery · Seat · Light · HVAC) · TextToSpeech
d1.png|1140|1080|$BG|left|b32,$M,STEP 1 · 언제 깨울지 고르기|b64,$W,배터리 목표 또는\n출발 시각|36,$G,충전 중(주차 P)일 때 '휴식 모드 시작'이 켜집니다.\n출발 시각은 15 · 30 · 45분 후 중에서 고릅니다.|30,$A,시간 단축 데모 · 출발 2분 후로 설정 (데모 전용 칩)
d2.png|1140|1080|$BG|left|b32,$M,STEP 2 · 원탭 휴식|b64,$W,시트 · 조명 · 공조를\n한 번에|36,$G,등받이 리클라인, 앰비언트 어둡게, 실내등 끔,\n공조 22°C · 풍량 1단.\n아래 Fake 차량 패널에 실제 SDK 호출이 찍힙니다.|34,$A,음성 안내 (에뮬레이터 녹화 시)\n"출발까지 2분, 편히 쉬세요.\n시간에 맞춰 깨워 드릴게요."
d3.png|1140|1080|$BG|left|b32,$M,STEP 3 · 출발 시 배터리|b64,$W,'계산 중…' 뒤에\n'출발 시 약 55%'|36,$G,실제 충전 속도를 잠시 지켜본 뒤 예측합니다.\n추측 값은 쓰지 않습니다.\n(실제 3분 → 데모 빌드 20초, 화면 2배속)
d4.png|1140|1080|$BG|left|b32,$M,STEP 4 · 선라이즈 기상|b64,$W,조명을 천천히\n밝혀 깨웁니다|36,$G,종료 전부터 앰비언트 밝기를 조금씩 올리고\n풍량을 한 단계 올립니다.\n패널: 앰비언트 밝기 10% → 33% → 56% → 80%|30,$A,시간 단축 데모 · 실제 3분 → 데모 30초, 화면 2배속
d5.png|1140|1080|$BG|left|b32,$M,STEP 5 · 출발 시각|b64,$W,출발 시각에 깨우고\n원래대로|36,$G,10초 카운트다운 뒤 시트·조명·공조를 복원합니다.\n'취소'하면 지금 상태 그대로 끝냅니다.|34,$A,음성 안내 (에뮬레이터 녹화 시)\n"출발 시각입니다. 배터리 ${N_END}%.\n10초 후 시트를 원래대로 되돌립니다."
d6.png|1140|1080|$BG|left|b32,$M,STEP 6 · 하차 전 확인 + 학습|b64,$W,다음 충전부터는\n바로 예측|36,$G,복원 뒤 창문 · 실내등 · 뒷좌석을 확인합니다.\n두 번째 세션은 지난 충전 기준으로\n시작하자마자 '출발 시 약 55%'를 보여줍니다.
s_hook.png|1080|1920|000000|center|b120,$W,알람\n맞추지 마세요.|b96,$M,출발 시각엔\n차가 깨워요.
s_ring.png|760|760|none|center|b130,$W,충전쉼표|44,$M,Plug & Rest
s_slogan.png|1080|460|none|center|b80,$W,충전은 차가,\n휴식은 당신이.|b48,$A,차가 알람이 됩니다.|40,$G,Pleos Connect 앱
s_tags.png|1080|120|none|center|34,$M,#HMG차량용앱개발공모전 #PleosConnect #전기차충전
s_bot.png|1080|220|$BG|center|30,$G,충전쉼표 · 시간 단축 데모 (Fake 차량 패널)
s3.png|1080|220|$BG|center|b64,$M,출발 시각 고르고, 원탭.|32,$A,시간 단축 데모 · 출발 2분 후로 설정
s4.png|1080|220|$BG|center|b56,$W,시트 젖히고 · 조명 낮추고 · 공조 22°C
s5.png|1080|220|$BG|center|b52,$W,차가 직접 말해줍니다|34,$A,"출발까지 2분, 편히 쉬세요. 시간에 맞춰 깨워 드릴게요."
s6.png|1080|220|$BG|center|b52,$W,충전 속도로 '출발 시 배터리' 예측
s_sun.png|1080|220|$BG|center|b52,$W,깨울 때가 되면, 조명을 천천히|32,$A,패널: 앰비언트 밝기 10% → 80% · 데모 30초 (실제 3분)
s7.png|1080|220|$BG|center|b52,$W,출발 시각에 깨워주고, 원래대로|36,$A,"출발 시각입니다. 배터리 ${N_END}%."
s8.png|1080|220|$BG|center|b56,$W,다음 충전부터는 바로 예측
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
bgcard() { # 배경이미지 길이 출력 필터 오버레이png...  (필터에서 [0:v]=배경, [1:v]~=오버레이)
  local bg=$1 d=$2 out=$3 f=$4; shift 4; local in=(); for p in "$@"; do in+=(-i "$T/$p"); done
  ffmpeg -v error -y -loop 1 -framerate 30 -t "$d" -i "$bg" "${in[@]}" -filter_complex \
    "$f,fade=in:d=0.4,fade=out:st=$(echo "$d-0.4" | bc):d=0.4" -t "$d" "${ENC[@]}" "$out"
}
join() { # 출력 조각...
  local out=$1; shift; : > "$T/list.txt"
  for f in "$@"; do echo "file '$f'" >> "$T/list.txt"; done
  ffmpeg -v error -y -f concat -safe 0 -i "$T/list.txt" -c copy -movflags +faststart "$out"
}

# ---------- 3) 시연 영상 (약 75초) ----------
# 키비주얼 1672x941 → 1920 폭으로 키우고 1080으로 자름. 좌상단 빈 하늘에 반투명 패널 + 글자
bgcard "$BRAND/keyvisual.png" 4 "$T/a0.mp4" \
  "[0:v]scale=1920:-2,crop=1920:1080,format=yuv420p,drawbox=x=60:y=60:w=740:h=520:color=0x0E0F12@0.55:t=fill[b];[b][1:v]overlay=60:60" title_txt.png
seg 0   6   1   d1.png "$T/a1.mp4" demo   # Idle: 출발 시각 → "2분 후" 칩 → 시작
seg 6   16  1   d2.png "$T/a2.mp4" demo   # Resting 시작, 패널 펼침 + SDK 로그
seg 60  80  2   d3.png "$T/a3.mp4" demo   # 계산 중… → "출발 시 약 55%"(약 75초) (2배속 → 10초)
seg 94  122 2   d4.png "$T/a4.mp4" demo   # 선라이즈: 배경 전환, 패널 밝기 10→80%, 풍량 2단 (2배속 → 14초)
seg 124 138 1   d5.png "$T/a5.mp4" demo   # 출발 시각 TTS + 10초 카운트다운 → 복원
seg 138 150 1   d6.png "$T/a6.mp4" demo   # Done·하차 확인 → 2회째 Idle(학습) → 2회째 Resting "출발 시 약 55%"
card end.png 6 "$T/a7.mp4"
join demo_fake_v1.mp4 "$T"/a{0..7}.mp4

# ---------- 4) 숏폼 (45초, shortform.md 장면표) ----------
card s_hook.png 2 "$T/b1.mp4"
seg 0   5   1   s3.png    "$T/b2.mp4" short  # 출발 시각 "2분 후" 칩 → 시작
seg 6   12  1   s4.png    "$T/b3.mp4" short  # 패널 펼침, 시트·조명·공조 set 로그
seg 13  17  1   s5.png    "$T/b4.mp4" short  # 시작 TTS
seg 60  80  4   s6.png    "$T/b5.mp4" short  # 계산 중 → 출발 시 약 55% (4배속 → 5초)
seg 94  122 3.5 s_sun.png "$T/b6.mp4" short  # 선라이즈 (3.5배속 → 8초)
seg 124 136 2   s7.png    "$T/b7.mp4" short  # 출발 시각 TTS + 카운트다운 (2배속 → 6초)
seg 138 150 2.4 s8.png    "$T/b8.mp4" short  # Done → 2회째 학습 예측 (2.4배속 → 5초)
# 세로 배경 941x1672 → 1080x1920. 민트 링 중심 (540,551) 반지름 약 430 → 링 안에 앱명, 아래 슬로건, 맨 아래 해시태그
bgcard "$BRAND/vertical-bg.png" 4 "$T/b9.mp4" \
  "[0:v]scale=1080:1920[b];[b][1:v]overlay=160:171[c];[c][2:v]overlay=0:1020[d];[d][3:v]overlay=0:1770" s_ring.png s_slogan.png s_tags.png
join shortform_v1.mp4 "$T"/b{1..9}.mp4

for f in demo_fake_v1.mp4 shortform_v1.mp4; do
  printf '%s  ' "$f"; ffprobe -v error -select_streams v -show_entries stream=width,height:format=duration -of csv=p=0 "$f" | tr '\n' ' '; echo
done
