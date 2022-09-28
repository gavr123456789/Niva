import ../lexer/token
type 
  NodeKind* = enum
    varDecl
    funDecl
  ASTNode* = ref object
    token: Token
    case kind: NodeKind
    of varDecl:
      varDeclName: 