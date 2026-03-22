package frontend.resolver

import main.frontend.meta.Token
import main.frontend.meta.compileError
import main.frontend.parser.types.ast.InternalTypes
import main.utils.WHITE
import main.utils.YEL
import kotlin.collections.count
import kotlin.collections.drop
import kotlin.collections.forEachIndexed
import kotlin.collections.isNotEmpty

val typeIsNull = { type: Type ->
    type is Type.InternalType && type.name == "Null"
}

// if this is compare for assign, then type1 = type2, so if t1 is nullable, and t2 is null, it's true
// type from db must be always first, because Int -> arg::Number, but not the other way around
fun compare2Types(
    expectedT: Type, // expected type
    actualT: Type, // real type
    tokenForErrors: Token,
    unpackNull: Boolean = false,
    isOut: Boolean = false, // checking for return type, where type1OrChildOf2 - declared return type, type2 - real
    unpackNullForSecond: Boolean = false, // x::Int? <- y::Int
    compareParentsOfBothTypes: Boolean = false,
    nullIsFirstOrSecond: Boolean = false, // any branch of switch can return null
    compareMutability: Boolean = true,
    unpackNullForFirst: Boolean = false,
): Boolean {
    if (expectedT === actualT) return true


    if (compareMutability && expectedT.isMutable && (actualT !is Type.UnknownGenericType && !actualT.isMutable)) {
        // allow assigning mutable union root when branch is returned
        if (expectedT is Type.UnionRootType && actualT is Type.UnionBranchType) {
            // mark expected as satisfied, but keep going for other checks
        } else {
            tokenForErrors.compileError("mutable type expected, create it like $YEL${expectedT.name.lowercase()} $YEL$expectedT$WHITE = ...")
        }
    }

    if (actualT.errors != expectedT.errors && expectedT.errors?.isNotEmpty() == true){ // if declared return type errors are empty and not null - its just any error inferring
        val expectedTypeErrors = expectedT.errors
        val realTypeReturnErrors = actualT.errors
        if (expectedTypeErrors != null && realTypeReturnErrors != null) {
            if (realTypeReturnErrors.count() > expectedTypeErrors.count()) {
                return false
            }
        }
    }

    if (expectedT is Type.Lambda && actualT is Type.Lambda) {

        fun stripUnitArgForComparison(type: Type.Lambda): List<KeywordArg> {
            val args = type.args
            if (args.isEmpty()) return args
            val isUnitType = { t: Type ->
                t is Type.InternalType && t.name == InternalTypes.Unit.name
            }
            val ext = type.extensionOfType
            if (ext != null) {
                if (isUnitType(ext) && args.first().name == "this") {
                    return args.drop(1)
                }
                return args
            }
            return if (args.size == 1 && isUnitType(args.first().type)) emptyList() else args
        }

        val argsOf1 = stripUnitArgForComparison(expectedT)
        val argsOf2 = stripUnitArgForComparison(actualT)

        if (expectedT.extensionOfType != null && actualT.extensionOfType != null) {
            if (!compare2Types(expectedT.extensionOfType, actualT.extensionOfType, tokenForErrors, compareParentsOfBothTypes = compareParentsOfBothTypes)) {
                val text =
                    "extension types of codeblocs are not the same: ${expectedT.extensionOfType} != ${actualT.extensionOfType}"
                tokenForErrors.compileError(text)
            }
        }

        if (argsOf1.count() != argsOf2.count()) {
//            tokenForErrors.compileError("Codeblock `${YEL}${type1OrChildOf2.name}${RESET}` has ${CYAN}${argsOf1.count()}${RESET} arguments but `${YEL}${type2.name}${RESET}` has ${CYAN}${argsOf2.count()}")
            return false
        }

        // temp for adding "(k,v)" for map, filter for hash maps
        if (actualT.specialFlagForLambdaWithDestruct) {
            expectedT.specialFlagForLambdaWithDestruct = true
        }
        if (expectedT.specialFlagForLambdaWithDestruct) {
            actualT.specialFlagForLambdaWithDestruct = true
        }
        //

        argsOf1.forEachIndexed { i, it ->
            val it2 = argsOf2[i]
            val isEqual = compare2Types(
                it.type,
                it2.type,
                tokenForErrors,
                unpackNullForSecond = unpackNullForSecond,
                unpackNull = unpackNull,
                isOut = isOut,
                compareParentsOfBothTypes = compareParentsOfBothTypes,
                nullIsFirstOrSecond = nullIsFirstOrSecond,
                compareMutability = compareMutability,
                unpackNullForFirst = unpackNullForFirst
            )
            if (!isEqual) {
//                tokenForErrors.compileError("argument ${WHITE}${it.name}${RESET} has type ${YEL}${it.type}${RESET} but ${WHITE}${it2.name}${RESET} has type ${YEL}${it2.type}")
                return false
            }
        }

        // check return types
        val return1 = expectedT.returnType
        val return2 = actualT.returnType
        val isReturnTypesEqual =
            (return2.name == InternalTypes.Unit.name || return1.name == InternalTypes.Unit.name) || compare2Types(
                return1,
                return2,
                tokenForErrors,
                unpackNull,
                compareParentsOfBothTypes = compareParentsOfBothTypes
            )


//            tokenForErrors.compileError("return types are not equal: ${YEL}$type1OrChildOf2 ${RESET}!= ${YEL}$type2")

        return isReturnTypesEqual
    }

    // one of the types is top type Any
    val type1IsAny = expectedT.name == InternalTypes.Any.name
    val type2IsAny = actualT.name == InternalTypes.Any.name
    if (type1IsAny || type2IsAny) {
        val expectedIsGeneric =
            expectedT is Type.UnknownGenericType ||
                (expectedT is Type.NullableType && expectedT.realType is Type.UnknownGenericType)
        val actualIsGeneric =
            actualT is Type.UnknownGenericType ||
                (actualT is Type.NullableType && actualT.realType is Type.UnknownGenericType)
        val expectedIsNonNullableAny = type1IsAny && expectedT !is Type.NullableType
        val actualIsNullableAny = type2IsAny && actualT is Type.NullableType
        if (type2IsAny && expectedIsGeneric) {
            return false
        }
        if (expectedIsNonNullableAny && actualIsGeneric) {
            return false
        }
        // expected Any (non-nullable), but actual is Any? (nullable) - only fail for return (isOut)
        if (expectedIsNonNullableAny && actualIsNullableAny && isOut) {
            return false
        }
        return true
    }
    if (expectedT.name == InternalTypes.Nothing.name || actualT.name == InternalTypes.Nothing.name) {
        return true
    }

    // if one of them null and second is nullable
    if ((typeIsNull(expectedT) && actualT is Type.NullableType ||
                typeIsNull(actualT) && expectedT is Type.NullableType)
    ) {
        return true
    }

    // in switch one branch can return Int and the other null
    if (nullIsFirstOrSecond && (typeIsNull(expectedT) || typeIsNull(actualT))) {
        return true
    }

    // expected nullable generic can accept non-nullable generic with the same name
    if (expectedT is Type.NullableType &&
        expectedT.realType is Type.UnknownGenericType &&
        actualT is Type.UnknownGenericType &&
        expectedT.realType.name == actualT.name
    ) {
        return true
    }
    // non-nullable generic cannot accept nullable value unless explicitly allowed by unpackNull flags

    if (!unpackNull && !unpackNullForFirst && !unpackNullForSecond &&
        expectedT is Type.UnknownGenericType &&
        actualT is Type.NullableType && actualT.realType is Type.UnknownGenericType
    ) {
        return false
    }
//    if (!unpackNull && !unpackNullForFirst && !unpackNullForSecond &&
//        type2 is Type.UnknownGenericType &&
//        type1OrChildOf2 is Type.NullableType && type1OrChildOf2.realType is Type.UnknownGenericType
//    ) {
//        return false
//    }


    // if one of them is generic
    if ((expectedT is Type.UnknownGenericType && actualT !is Type.UnknownGenericType  || //&& type2 !is Type.NullableType
                actualT is Type.UnknownGenericType && expectedT !is Type.UnknownGenericType ) //&& type1OrChildOf2 !is Type.NullableType // NOW comparing generics with Nullable types is OK
    ) {
        if (expectedT is Type.UnknownGenericType) {
            val actual = actualT.unpackNull()
            if (actual is Type.InternalType && actual.name == InternalTypes.Any.name) {
                return false
            }
        }
        return if (!isOut)
            true // getting T but got Int, is OK
        else
            false // -> T, but Int returned is OK in switch, but not OK in message decl
        // so this check has no point
    }

    // if both are generics of the different letters
    if (expectedT is Type.UnknownGenericType && actualT is Type.UnknownGenericType && expectedT.name != actualT.name) {
        // if we return than its wrong
        // -> G, but T is returned
        // if we get than it's ok
        return if (isOut)
            false // returning T, but -> G declarated
        else
            true // getting T, but got G is OK
    }


    val pkg1 = expectedT.pkg
    val pkg2 = actualT.pkg
    val isDifferentPkgs = pkg1 != pkg2 && pkg1 != "core" && pkg2 != "core"
    if (expectedT is Type.UserLike && actualT is Type.UserLike) {
        val bothTypesAreBindings = expectedT.isBinding && actualT.isBinding
        // if types from different packages, and it's not core
        // bindings can be still compatible, because there is inheritance in Java
        if (!bothTypesAreBindings && isDifferentPkgs) {
//            tokenForErrors.compileError("${YEL}$type1${RESET} is from ${WHITE}$pkg1${RESET} pkg, and ${YEL}$type2${RESET} from ${WHITE}$pkg2")
            return false
        }

        // if both types has generic params
        if (expectedT.typeArgumentList.isNotEmpty() && actualT.typeArgumentList.isNotEmpty()) {
            val args1 = expectedT.typeArgumentList
            val args2 = actualT.typeArgumentList
            if (args1.count() != args2.count()) {
//                tokenForErrors.compileError("Types: ${YEL}$type1OrChildOf2${RESET} and ${YEL}$type2${RESET} have a different number of generic parameters")
                return false
            }

            val isSameNames = expectedT.toString() == actualT.toString()
            args1.forEachIndexed { index, arg1 ->
                val arg2 = args2[index]
                if (isSameNames) {
                    if (arg1 is Type.UnknownGenericType) {
                        expectedT.replaceTypeArguments(actualT.typeArgumentList)
                        return true
                    }
                    if (arg2 is Type.UnknownGenericType) {
                        actualT.replaceTypeArguments(expectedT.typeArgumentList)
                        return true
                    }
                }

                val sameArgs = compare2Types(arg1, arg2, tokenForErrors, compareParentsOfBothTypes = compareParentsOfBothTypes, compareMutability = true) // we dont need to compare mutability of generic args
                if (!sameArgs) {
                    return false
//                    tokenForErrors.compileError("Generic argument of type: ${YEL}${type1OrChildOf2.name} ${WHITE}$arg1${RESET} != ${WHITE}$arg2${RESET} from type ${YEL}${type2.name}")
                }
//                if (sameArgs && )

                //else {
//                    val hasGeneralRoot_Or_itsListTAndListInt = type1OrChildOf2.name == type2.name && type2.pkg == type1OrChildOf2.pkg || findGeneralRoot(type1OrChildOf2, type2) != null
//                    return hasGeneralRoot_Or_itsListTAndListInt
                //} //type1OrChildOf2.name == type2.name && type2.pkg == type1OrChildOf2.pkg // List::Int and List::T are the same
            }

            val hasGeneralRoot_Or_itsListTAndListInt = expectedT.name == actualT.name && actualT.pkg == expectedT.pkg || findGeneralRoot(expectedT, actualT) != null
            return hasGeneralRoot_Or_itsListTAndListInt
        }


        // first is parent of the second
        if (compareParentsOfBothTypes) {
            var parent1: Type? = expectedT.parent
            while (parent1 != null) {
                if (compare2Types(actualT, parent1, tokenForErrors,compareParentsOfBothTypes = compareParentsOfBothTypes, compareMutability = false)) {
                    return true
                }
                parent1 = parent1.parent
            }
        }
        // second is parent of the first
        // actually, we can't compare parents of second
        // this will lead to Number -> arg::Widget

        var parent2: Type? = actualT.parent
        while (parent2 != null) {
            if (compare2Types(expectedT, parent2, tokenForErrors, compareParentsOfBothTypes = compareParentsOfBothTypes, compareMutability = false, isOut = isOut)) {
                return true
            }
            parent2 = parent2.parent
        }


//        var parent1: Type? = type1OrChildOf2.parent
//        while (parent1 != null) {
//            if (compare2Types(type2, parent1, tokenForErrors, compareParentsOfBothTypes = compareParentsOfBothTypes)) {
//                return true
//            }
//            parent1 = parent1.parent
//        }
    }


    // x::Int? = null
    if (expectedT is Type.NullableType && typeIsNull(actualT)) {
        return true
    }

    // Ins sas -> Int? = ^42
    if (unpackNull) {
        if ((expectedT is Type.NullableType && actualT !is Type.NullableType)) {
            val win = compare2Types(expectedT.realType, actualT, tokenForErrors)
            if (win) return true
        }
        if ((actualT is Type.NullableType && expectedT !is Type.NullableType)) {
            val win = compare2Types(expectedT, actualT.realType, tokenForErrors)
            if (win) return true
        }
        if (expectedT is Type.NullableType && actualT is Type.NullableType) {
            val win = compare2Types(
                expectedT.realType,
                actualT.realType,
                tokenForErrors,
                unpackNull = true,
                isOut = isOut,
                compareParentsOfBothTypes = compareParentsOfBothTypes,
                compareMutability = compareMutability
            )
            if (win) return true
        }
        // both are nullable: Int? vs Int?
    } else if (expectedT is Type.NullableType && actualT is Type.NullableType) {
        return compare2Types(expectedT.realType, actualT.realType, tokenForErrors)
    }
    else if (unpackNullForSecond) {
        if ((actualT is Type.NullableType )) { //&& type1OrChildOf2 !is Type.NullableType
            val win = compare2Types(actualT.realType, expectedT, tokenForErrors, isOut = isOut, compareParentsOfBothTypes = compareParentsOfBothTypes, compareMutability = compareMutability)
            if (win) return true
        }
    } else if (unpackNullForFirst) {
        if ((expectedT is Type.NullableType )) { //&& type2 !is Type.NullableType
            val win = compare2Types(expectedT.realType, actualT, tokenForErrors, isOut = isOut, compareParentsOfBothTypes = compareParentsOfBothTypes, compareMutability = compareMutability)
            if (win) return true
        }
    }


    if (expectedT.toStringWithoutErrors() == actualT.toStringWithoutErrors() && !isDifferentPkgs) {
        return true
    }

    // comparing with nothing is always true, its bottom type, subtype of all types,
    // so we can return nothing from switch expr branches, beside u cant do it with different types

    return false
}
