// Draws an original album cover: gradient, geometric shapes and the album title.
// usage: swift cover.swift OUT.jpg "Album" "Artist" HEX1 HEX2 SEED
import AppKit
import CoreGraphics

let a = CommandLine.arguments
let (out, album, artist) = (a[1], a[2], a[3])
func color(_ hex: String, _ alpha: CGFloat = 1) -> CGColor {
    let v = UInt32(hex, radix: 16)!
    return CGColor(red: CGFloat((v >> 16) & 0xff) / 255, green: CGFloat((v >> 8) & 0xff) / 255,
                   blue: CGFloat(v & 0xff) / 255, alpha: alpha)
}
let (c1, c2) = (color(a[4]), color(a[5]))
var seed = UInt64(a[6])!
func rand() -> CGFloat {
    seed = seed &* 6364136223846793005 &+ 1442695040888963407
    return CGFloat(seed >> 33) / CGFloat(UInt64(1) << 31)
}

let size = 1000
let ctx = CGContext(data: nil, width: size, height: size, bitsPerComponent: 8, bytesPerRow: 0,
                    space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: [c1, c2] as CFArray, locations: [0, 1])!
ctx.drawLinearGradient(gradient, start: .zero, end: CGPoint(x: size, y: size), options: [])

for _ in 0..<7 {
    let r = 80 + rand() * 320
    let rect = CGRect(x: rand() * CGFloat(size) - r / 2, y: rand() * CGFloat(size) - r / 2, width: r, height: r)
    ctx.setFillColor(color(rand() > 0.5 ? "FFFFFF" : "000000", 0.06 + rand() * 0.12))
    ctx.fillEllipse(in: rect)
}
ctx.setStrokeColor(color("FFFFFF", 0.35))
for i in 0..<5 {
    ctx.setLineWidth(2 + CGFloat(i))
    let y = 120 + rand() * 760
    ctx.move(to: CGPoint(x: 0, y: y))
    ctx.addCurve(to: CGPoint(x: CGFloat(size), y: y + (rand() - 0.5) * 300),
                 control1: CGPoint(x: 330, y: y + (rand() - 0.5) * 500),
                 control2: CGPoint(x: 660, y: y + (rand() - 0.5) * 500))
    ctx.strokePath()
}

let nsctx = NSGraphicsContext(cgContext: ctx, flipped: false)
NSGraphicsContext.current = nsctx
func draw(_ text: String, _ font: NSFont, _ y: CGFloat) {
    let attrs: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: NSColor.white]
    NSAttributedString(string: text, attributes: attrs).draw(at: NSPoint(x: 70, y: y))
}
draw(album.uppercased(), NSFont.systemFont(ofSize: 72, weight: .heavy), 150)
draw(artist, NSFont.systemFont(ofSize: 40, weight: .medium), 90)

let rep = NSBitmapImageRep(cgImage: ctx.makeImage()!)
try! rep.representation(using: .jpeg, properties: [.compressionFactor: 0.9])!.write(to: URL(fileURLWithPath: out))
