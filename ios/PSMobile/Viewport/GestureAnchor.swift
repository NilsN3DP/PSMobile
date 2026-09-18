import Foundation

struct TouchGestureAnchor {
    let point: CGPoint
    let span: CGFloat
}

func touchGestureAnchor(points: [CGPoint]) -> TouchGestureAnchor? {
    guard !points.isEmpty else { return nil }
    let x = points.reduce(CGFloat.zero) { $0 + $1.x } / CGFloat(points.count)
    let y = points.reduce(CGFloat.zero) { $0 + $1.y } / CGFloat(points.count)
    let span = points.count >= 2
        ? hypot(points[0].x - points[1].x, points[0].y - points[1].y)
        : 0
    return TouchGestureAnchor(point: CGPoint(x: x, y: y), span: span)
}
