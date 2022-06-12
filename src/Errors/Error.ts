export type NivaError = RedefinitionOfVariable | TypeAlreadyDefined

export interface ErrorInfo {
  lineAndColMessage: string
}

export interface TypeAlreadyDefined {
  errorKind: "TypeAlreadyDefined"

  lineAndColMessage: string
  typeName: string
}

export interface RedefinitionOfVariable {
  errorKind: "RedefinitionOfVariable"

  file: string
  lineAndColMessage: string
  previousLineAndColMessage: string
  variableName: string
  // Redefinition from  
	// sourceCode: string
	// line: number
  // of 
	// previousSourceCode: string
	// previousLine2: number
}

