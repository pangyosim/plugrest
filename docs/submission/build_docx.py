"""proposal.md(1~5) + section6.md(6) -> 접수양식 02 섹션 채우기. 실행: /usr/bin/python3 docs/submission/build_docx.py docs  (python-docx 필요, 홈브루 3.14는 pip 깨짐 → /usr/bin/python3 사용)
01·02·04의 앱명 칸, 01·04 팀명과 01 참가 부문(일반)을 채우고, 참가 단위·개인정보·03·04 서명은 비운다."""
import re, shutil, sys
import docx
from docx.table import Table
from docx.text.paragraph import Paragraph
from docx.shared import Pt

D = sys.argv[1]
SRC = f"{D}/contest-application-consent.docx"
OUT = f"{D}/submission/접수양식_충전쉼표_draft.docx"
APP = "충전쉼표 (Plug & Rest)"
TEAM = "충전쉼표"

def sections(md_text):
    """'## N. 제목' 단위로 본문 분리."""
    out, cur = {}, None
    for line in md_text.splitlines():
        m = re.match(r"^#{1,2} (\d)\. ", line)
        if m:
            cur = int(m.group(1)); out[cur] = []; continue
        if line.startswith("---") or line.startswith("### PM 확인용") or line.startswith("## 7번"):
            cur = None
        if cur is not None:
            out[cur].append(line)
    return out

def add_runs(par, text):
    text = text.replace("`", "")
    for i, part in enumerate(re.split(r"\*\*", text)):
        if part:
            par.add_run(part).bold = (i % 2 == 1)

def fill(cell, lines):
    cell.paragraphs[0].text = ""
    first = [True]
    def para(style=None):
        if first[0]:
            first[0] = False
            p = cell.paragraphs[0]
        else:
            p = cell.add_paragraph()
        if style:
            try: p.style = style
            except KeyError: pass
        return p
    i = 0
    while i < len(lines):
        line = lines[i].rstrip()
        if not line.strip():
            i += 1; continue
        if line.startswith("|"):
            rows = []
            while i < len(lines) and lines[i].startswith("|"):
                cells = [c.strip() for c in lines[i].strip().strip("|").split("|")]
                if not all(re.fullmatch(r":?-+:?", c) for c in cells):
                    rows.append(cells)
                i += 1
            first[0] = False
            t = cell.add_table(rows=len(rows), cols=len(rows[0]))
            t.style = "Table Grid"
            for r, row in enumerate(rows):
                for c, val in enumerate(row):
                    tc = t.cell(r, c); tc.paragraphs[0].text = ""
                    add_runs(tc.paragraphs[0], f"**{val.strip('*')}**" if r == 0 else val)
                    for run in tc.paragraphs[0].runs: run.font.size = Pt(9)
            continue
        m = re.match(r"^(\s*)- (.*)", line)
        if m:
            lvl = len(m.group(1)) // 2
            p = para("List Bullet 2" if lvl else "List Bullet")
            add_runs(p, m.group(2))
        elif re.match(r"^\d+\. ", line):
            p = para(); add_runs(p, line)
        elif line.startswith("> "):
            p = para(); add_runs(p, line[2:])
        else:
            p = para(); add_runs(p, line)
        i += 1

doc_proposal = sections(open(f"{D}/submission/proposal.md").read())
doc_s6 = sections(open(f"{D}/submission/section6.md").read())
body = {**{k: v for k, v in doc_proposal.items() if k <= 5}, 6: doc_s6[6]}

EXCLUDE = {"14", "17"}  # PM 결정: 펫 모드·GS25 기사 → 결번으로 제외(재번호 안 함)
refs = [r for r in re.findall(r"^\d+\. .*", open(f"{D}/submission/section6.md").read().split("## 7번")[1], re.M) if r.split(".")[0] not in EXCLUDE]
body[7] = ["- 시연 영상: 첨부 파일 demo_fake_v1.mp4 (76초, Fake 차량 모드·시간 단축 데모)", "- GitHub 저장소(APK 포함): https://github.com/pangyosim/plugrest (APK: apk/충전쉼표_PlugRest_release.apk)", "- 앱 화면 시안: 첨부 파일 화면시안_충전쉼표.pdf (표지 + 화면 6장)", "", "**출처 목록** (본문의 [n] 번호와 연동)"] + refs
body[8] = ["{숏폼 링크}"]

shutil.copy(SRC, OUT)
d = docx.Document(OUT)
els = list(d.element.body.iterchildren())
filled = set()
for idx, el in enumerate(els):
    if el.tag.endswith("}p"):
        p = Paragraph(el, d)
        if p.text.strip().startswith("앱명 / 프로젝트명:"):
            lead = p.text[: len(p.text) - len(p.text.lstrip())]
            p.text = f"{lead}앱명 / 프로젝트명: {APP}"; filled.add(f"앱명@{idx}")
        if p.text.startswith("참가 부문:"):
            p.runs[-2].text = " ■ "; filled.add("부문")  # ponytail: 원본 run 분할에 의존 (-2 = 일반 앞 □)
        if p.text.startswith("팀명("):
            label = p.text.split(":")[0]
            for r in p.runs[1:]: r.text = ""
            p.runs[0].text = f"{label}: {TEAM}"; filled.add(f"팀명@{idx}")
        m = re.match(r"^(\d)\. ", p.text)
        if m and int(m.group(1)) in body and idx + 1 < len(els) and els[idx + 1].tag.endswith("}tbl"):
            n = int(m.group(1))
            fill(Table(els[idx + 1], d).cell(0, 0), body[n]); filled.add(n)
d.save(OUT)
print("filled:", sorted(map(str, filled)))
