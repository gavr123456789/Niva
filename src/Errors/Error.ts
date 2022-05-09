export type NivaError = RedefinitionOfVariableError


export interface RedefinitionOfVariableError {
  errorKind: "RedefinitionOfVariableError"

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

