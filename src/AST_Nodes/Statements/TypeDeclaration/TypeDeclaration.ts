
export interface TypedProperty {
  identifier: string,
  type?: string 
  // generic?
}

export interface TypeDeclaration {
  kindStatement: "TypeDeclaration"
  typeName: string,
  typedProperties: TypedProperty[]
  ref: boolean
}


export type UnionBranch =
  | UnionBranchWithManyNames
  | UnionBranchWithOneName

export interface UnionBranchWithManyNames {
  unionKind: "ManyNames"
  names: string[]
  // if there no typedProperty then discard
  propertyTypes: TypedProperty[]
}

export interface UnionBranchWithOneName {
  unionKind: "OneNames"
  name: string
  propertyTypes: TypedProperty[]
}


export interface UnionDeclaration {
  kindStatement: "UnionDeclaration"
  defaultProperties: TypedProperty[]
  name: string,
  branches: UnionBranch[]
  ref: boolean
}



