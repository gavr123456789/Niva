
export interface TypedProperty {
  identifier: string,
  type?: string 
  // generic?
}
export function getTypedPropertiesNames(typedProperties: TypedProperty[]): string[] {
  return typedProperties.map(x => x.identifier)
}

// TODO add getter setter booleans for readonly
export interface TypeDeclaration {
  kindStatement: "TypeDeclaration"
  name: string,
  typedProperties: TypedProperty[]
  ref: boolean
}


export type UnionBranch =
  | UnionBranchWithManyNames
  | UnionBranchWithOneName

export interface UnionBranchWithManyNames {
  unionKind: "ManyNames"
  names: string[]
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



