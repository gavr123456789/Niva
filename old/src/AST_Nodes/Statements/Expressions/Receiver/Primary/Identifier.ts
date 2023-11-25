export interface Identifier {
  kindPrimary: "Identifier"
  readonly value: string
  moduleName?: string

  type?: string
}
