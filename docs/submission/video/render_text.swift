// 자막·카드 PNG 렌더러. ffmpeg에 drawtext/libass가 없어서 macOS 기본 폰트(Apple SD Gothic Neo)로 PNG를 만든다.
// 사용: swift render_text.swift spec.tsv outdir
// spec 한 줄 = 파일명|가로|세로|배경(RRGGBB 또는 none)|정렬(center 또는 left)|항목|항목...  (텍스트에 | 금지)
// 항목 = 크기,색,텍스트   (크기 앞에 b = 굵게, 텍스트 안의 \n = 줄바꿈)
import AppKit

let args = CommandLine.arguments
let spec = try String(contentsOfFile: args[1], encoding: .utf8)
let outDir = args[2]

func color(_ hex: String) -> NSColor {
    var v: UInt64 = 0
    Scanner(string: hex).scanHexInt64(&v)
    return NSColor(srgbRed: CGFloat((v >> 16) & 0xff) / 255, green: CGFloat((v >> 8) & 0xff) / 255,
                   blue: CGFloat(v & 0xff) / 255, alpha: 1)
}

for line in spec.split(separator: "\n") where !line.hasPrefix("#") && !line.trimmingCharacters(in: .whitespaces).isEmpty {
    let f = line.components(separatedBy: "|")
    let (name, w, h, bg, align) = (f[0], Int(f[1])!, Int(f[2])!, f[3], f[4])

    let para = NSMutableParagraphStyle()
    para.alignment = align == "center" ? .center : .left
    para.lineSpacing = 6
    para.paragraphSpacing = 22

    let text = NSMutableAttributedString()
    for (i, item) in f[5...].enumerated() {
        let p = item.split(separator: ",", maxSplits: 2).map(String.init)
        let bold = p[0].hasPrefix("b")
        let size = CGFloat(Double(p[0].dropFirst(bold ? 1 : 0))!)
        let font = NSFont(name: bold ? "AppleSDGothicNeo-Bold" : "AppleSDGothicNeo-Regular", size: size)!
        let body = p[2].replacingOccurrences(of: "\\n", with: "\n") + (i < f.count - 6 ? "\n" : "")
        text.append(NSAttributedString(string: body, attributes: [.font: font, .foregroundColor: color(p[1]), .paragraphStyle: para]))
    }

    let rep = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: w, pixelsHigh: h, bitsPerSample: 8, samplesPerPixel: 4,
                               hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)
    if bg != "none" { color(bg).setFill(); NSRect(x: 0, y: 0, width: w, height: h).fill() }
    let pad: CGFloat = 60, cw = CGFloat(w), ch = CGFloat(h)
    let box = text.boundingRect(with: NSSize(width: cw - 2 * pad, height: .greatestFiniteMagnitude),
                                options: [.usesLineFragmentOrigin])
    text.draw(with: NSRect(x: pad, y: (ch - box.height) / 2, width: cw - 2 * pad, height: box.height),
              options: [.usesLineFragmentOrigin])
    NSGraphicsContext.restoreGraphicsState()
    try rep.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: "\(outDir)/\(name)"))
}
