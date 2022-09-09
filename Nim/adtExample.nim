{.push warning[ProveField]: on.}

type
    ShapeKind = enum circle, rect
    Shape = object
        case kind: ShapeKind
        of circle:
            r: float
        of rect:
            w, h: float
