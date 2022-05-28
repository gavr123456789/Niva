
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