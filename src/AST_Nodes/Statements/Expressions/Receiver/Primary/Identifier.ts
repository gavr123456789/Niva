export interface Identifier {
  kindPrimary: "Identifier"
  readonly value: string
  readonly type?: string
}