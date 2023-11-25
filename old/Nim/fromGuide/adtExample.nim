{.push warning[ProveField]: on.}

type
    ShapeKind = enum circle, rect

    Shape = object
        case kind: ShapeKind
        of circle:
            r: float
        of rect:
            w, h: float

type 
    TransportKind = enum
      flying
      ground
    GroundKind = enum
      cargo
      passenger
    FlyingKind = enum
      helicopter
      plane
    
    Transport = ref object
      case kind: TransportKind
      of flying:
        q: int
        case flyingKind: FlyingKind
          of helicopter:
            x: int
          of plane:
            y: int
      of ground:
         case groundKind: GroundKind
          of cargo:
            x: int
          of passenger:
            y: int
