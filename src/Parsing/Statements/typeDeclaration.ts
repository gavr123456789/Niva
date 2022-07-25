import { TerminalNode, NonterminalNode, IterationNode } from "ohm-js";
import {
  TypeDeclaration,
  TypedProperty, UnionBranch,
  UnionDeclaration
} from "../../AST_Nodes/Statements/TypeDeclaration/TypeDeclaration";
import { state, TypeField, codeDB } from "../../niva";
import {Identifier} from "../../AST_Nodes/Statements/Expressions/Receiver/Primary/Identifier";


export function unionDeclaration(
  union: TerminalNode,
  _s: NonterminalNode,
  untypedIdentifier_UnionName: NonterminalNode,
  _s2: NonterminalNode,
  maybe_defaultTypedProperties: IterationNode,
  _s3: NonterminalNode,
  _eq: TerminalNode,
  _s4: NonterminalNode,
  unionBranchesNode: NonterminalNode
): UnionDeclaration{

  const branches: UnionBranch[] = unionBranchesNode.toAst() // unionBranchs
  const hasTypeProperties = maybe_defaultTypedProperties.children.at(0)
  const defaultProperties: TypedProperty[] = hasTypeProperties?.toAst() ?? [];

  const result: UnionDeclaration = {
    kindStatement: "UnionDeclaration",
    name: untypedIdentifier_UnionName.sourceString,
    branches,
    ref: false,
    defaultProperties: defaultProperties
  }

  return  result
}

export function unionBranchs(arg0: NonterminalNode, arg1: IterationNode, arg2: IterationNode): UnionBranch[] {
  const branch: UnionBranch = arg0.toAst()
  const more: UnionBranch[] = arg2.children.map(x => x.toAst())

  const result: UnionBranch[] = [branch]

  if (more.length > 0){
    result.push(...more)
  }

  return result
}

export function unionBranch(
  _maybePipe: IterationNode,
  _s: NonterminalNode,
  untypedIdentifier_NameOfBranch: NonterminalNode,
  _ws: IterationNode,
  _comma: IterationNode,
  _ws2: IterationNode,
  many_UntypedIdentifier: IterationNode,
  _s2: NonterminalNode,
  _jjj: IterationNode,
  _s3: IterationNode,
  maybeTypedProps: IterationNode
): UnionBranch{


  const hasManyNamesInBranch = many_UntypedIdentifier.children.length > 0

  const names: string[] = many_UntypedIdentifier.children.map(x => x.sourceString)
  names.push(untypedIdentifier_NameOfBranch.sourceString)

  const hasProps = maybeTypedProps.children.at(0)
  const prop: TypedProperty[]  = hasProps?.toAst() ?? []

  if (hasManyNamesInBranch){

    const result: UnionBranch = {
      unionKind: "ManyNames",
      names,
      propertyTypes: prop
    }
    return result

  } else {
    const firstName = names.at(0)
    if (!firstName){
      throw new Error("Union branch must have tage name")
    }
    const result: UnionBranch = {
      unionKind: "OneNames",
      name: firstName,
      propertyTypes: prop
    }
    return result
  }



}




///


export function typeDeclaration(_type: TerminalNode, _s: NonterminalNode, untypedIdentifier: NonterminalNode, _s2: NonterminalNode, typedProperties: IterationNode): TypeDeclaration {
  const hasTypeProperties = typedProperties.children.at(0)
  const typedPropertiesAst: TypedProperty[] = hasTypeProperties?.toAst() || [];
  const typeName = untypedIdentifier.sourceString
  const result: TypeDeclaration = {
    kindStatement: 'TypeDeclaration',
    typeName,
    typedProperties: typedPropertiesAst,
    ref: false // TODO
  };


  // Add to BD
  if (codeDB.hasType(typeName)){
    state.errors.push({
      errorKind: "TypeAlreadyDefined",
      lineAndColMessage: _type.source.getLineAndColumnMessage(),
      typeName
    })

  } else {
    const fields = new Map<string, TypeField>()
    typedPropertiesAst.forEach(x => {
      fields.set(x.identifier, {type: x.type ?? "auto"})
    })
  
    codeDB.addNewType(typeName, fields)
  }

  return result;
}

export function typedProperties(typedProperty: NonterminalNode, _spaces: IterationNode, other_typedProperty: IterationNode): TypedProperty[] {
  const result: TypedProperty[] = [typedProperty.toAst()];
  other_typedProperty.children.forEach((x) => result.push(x.toAst()));
  return result;
}

export function typedProperty(untypedIdentifier: NonterminalNode, _colon: TerminalNode, _s: NonterminalNode, untypedIdentifierOrNestedType: NonterminalNode): TypedProperty {
  const typeNameSourceString = untypedIdentifierOrNestedType.sourceString;
  if (typeNameSourceString.startsWith('type')) {
    throw new Error('Nested types not supported yet');
  }

  const result: TypedProperty = {
    identifier: untypedIdentifier.sourceString,
    type: typeNameSourceString
  };

  return result;
}