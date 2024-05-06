package frontend.resolver

import main.frontend.meta.CompilerError
import main.frontend.meta.Token
import main.frontend.meta.compileError
import main.frontend.parser.types.ast.InternalTypes
import main.utils.CYAN
import main.utils.RESET
import main.utils.WHITE
import main.utils.YEL
import java.lang.Exception
import kotlin.collections.count
import kotlin.collections.drop
import kotlin.collections.forEachIndexed
import kotlin.collections.isNotEmpty

// if this is compare for assign, then type1 = type2, so if t1 is nullable, and t2 is null, it's true
// type from db must be always first, because Ins -> arg::Number, but not the other way around
fun compare2Types(
    type1: Type,
    type2: Type,
    token: Token? = null,
    unpackNull: Boolean = false,
    isOut: Boolean = false, // checking for return type
    unpackNullForFirst: Boolean = false, // x::Int? <- y::Int
    compareParentsOfBothTypes: Boolean = false
): Boolean {
    if (type1 === type2) return true


    if (type1 is Type.Lambda && type2 is Type.Lambda) {

        // and type 2
        val type1IsExt = type1.extensionOfType != null
        val type2IsExt = type2.extensionOfType != null

        val argsOf1 = if (type1IsExt && (!type2IsExt && type1.args.count() - 1 == type2.args.count()))
            type1.args.drop(1)
        else type1.args

        val argsOf2 = if (type2IsExt && (!type1IsExt && type1.args.count() == type2.args.count() - 1))
            type2.args.drop(1)
        else type2.args

        if (type1.extensionOfType != null && type2.extensionOfType != null) {
            if (!compare2Types(type1.extensionOfType, type2.extensionOfType, compareParentsOfBothTypes = compareParentsOfBothTypes)) {
                val text =
                    "extension types of codeblocs are not the same: ${type1.extensionOfType} != ${type2.extensionOfType}"
                token?.compileError(text)
                throw CompilerError(text)
            }
        }

        if (argsOf1.count() != argsOf2.count()) {
            token?.compileError("Codeblock `${YEL}${type1.name}${RESET}` has ${CYAN}${argsOf1.count()}${RESET} arguments but `${YEL}${type2.name}${RESET}` has ${CYAN}${argsOf2.count()}")
            return false
        }

        // temp for adding "(k,v)" for map, filter for hash maps
        if (type2.specialFlagForLambdaWithDestruct) {
            type1.specialFlagForLambdaWithDestruct = true
        }
        if (type1.specialFlagForLambdaWithDestruct) {
            type2.specialFlagForLambdaWithDestruct = true
        }
        //

        argsOf1.forEachIndexed { i, it ->
            val it2 = argsOf2[i]
            val isEqual = compare2Types(it.type, it2.type, compareParentsOfBothTypes = compareParentsOfBothTypes)
            if (!isEqual) {
                token?.compileError("argument ${WHITE}${it.name}${RESET} has type ${YEL}${it.type}${RESET} but ${WHITE}${it2.name}${RESET} has type ${YEL}${it2.type}")
                return false
            }
        }

        // check return types
        val return1 = type1.returnType
        val return2 = type2.returnType
        val isReturnTypesEqual =
            (return2.name == InternalTypes.Unit.name || return1.name == InternalTypes.Unit.name) || compare2Types(
                return1,
                return2,
                token,
                unpackNull,
                compareParentsOfBothTypes = compareParentsOfBothTypes
            )
        if (!isReturnTypesEqual) {
            token?.compileError("return types are not equal: ${YEL}$type1 ${RESET}!= ${YEL}$type2")
        }

        return true
    }

    // one of the types is top type Any
    if (type1.name == InternalTypes.Any.name || type2.name == InternalTypes.Any.name) {
        return true
    }

    val typeIsNull = { type: Type ->
        type is Type.InternalType && type.name == "Null"
    }
    // if one of them null and second is nullable
    if ((typeIsNull(type1) && type2 is Type.NullableType ||
                typeIsNull(type2) && type1 is Type.NullableType)
    ) {
        return true
    }

    // if one of them is generic
    if ((type1 is Type.UnknownGenericType && type2 !is Type.UnknownGenericType ||
                type2 is Type.UnknownGenericType && type1 !is Type.UnknownGenericType)
    ) {
        return if (!isOut)
            true // getting T but got Int, is OK
        else
            false // -> T, but Int returned
    }

    // if both are generics of the different letters
    if (type1 is Type.UnknownGenericType && type2 is Type.UnknownGenericType && type1.name != type2.name) {
        // if we return than its wrong
        // -> G, but T is returned
        // if we get than it's ok
        return if (isOut)
            false // returning T, but -> G declarated
        else
            true // getting T, but got G is OK
    }


    val pkg1 = type1.pkg
    val pkg2 = type2.pkg
    val isDifferentPkgs = pkg1 != pkg2 && pkg1 != "core" && pkg2 != "core"
    if (type1 is Type.UserLike && type2 is Type.UserLike) {
        val bothTypesAreBindings = type1.isBinding && type2.isBinding
        // if types from different packages, and it's not core
        // bindings can be still compatible, because there is inheritance in Java
        if (!bothTypesAreBindings && isDifferentPkgs) {
            token?.compileError("${YEL}$type1${RESET} is from ${WHITE}$pkg1${RESET} pkg, and ${YEL}$type2${RESET} from ${WHITE}$pkg2")
            return false
        }

        // if both types has generic params
        if (type1.typeArgumentList.isNotEmpty() && type2.typeArgumentList.isNotEmpty()) {
            val args1 = type1.typeArgumentList
            val args2 = type2.typeArgumentList
            if (args1.count() != args2.count()) {
                token?.compileError("Types: ${YEL}$type1${RESET} and ${YEL}$type2${RESET} have a different number of generic parameters")
                return false
            }

            val isSameNames = type1.toString() == type2.toString()
            args1.forEachIndexed { index, arg1 ->
                val arg2 = args2[index]
                if (isSameNames) {
                    if (arg1 is Type.UnknownGenericType) {
                        type1.typeArgumentList = type2.typeArgumentList
                        return true
                    }
                    if (arg2 is Type.UnknownGenericType) {
                        type2.typeArgumentList = type1.typeArgumentList
                        return true
                    }
                }

                val sameArgs = compare2Types(arg1, arg2, token, compareParentsOfBothTypes = compareParentsOfBothTypes)
                if (!sameArgs) {
                    token?.compileError("Generic argument of type: ${YEL}${type1.name} ${WHITE}$arg1${RESET} != ${WHITE}$arg2${RESET} from type ${YEL}${type2.name}")
                    throw Exception("Generic argument of type: ${YEL}${type1.name} ${WHITE}$arg1${RESET} != ${WHITE}$arg2${RESET} from type ${YEL}${type2.name}")
                } else
                    return sameArgs // List::Int and List::T are the same
            }
        }


        // first is parent of the second
        if (compareParentsOfBothTypes) {
            var parent1: Type? = type1.parent
            while (parent1 != null) {
                if (compare2Types(type2, parent1, compareParentsOfBothTypes = compareParentsOfBothTypes)) {
                    return true
                }
                parent1 = parent1.parent
            }
        }
        // second is parent of the first
        // actually, we can't compare parents of second
        // this will lead to Number -> arg::Widget

        var parent2: Type? = type2.parent
        while (parent2 != null) {
            if (compare2Types(type1, parent2, compareParentsOfBothTypes = compareParentsOfBothTypes)) {
                return true
            }
            parent2 = parent2.parent
        }
    }


    // x::Int? = null
    if (type1 is Type.NullableType && type2 is Type.InternalType && type2.name == "Null") {
        return true
    }

    // Ins sas -> Int? = ^42
    if (unpackNull) {
        if ((type1 is Type.NullableType && type2 !is Type.NullableType)) {
            val win = compare2Types(type1.realType, type2, token)
            if (win) return true
        }
        if ((type2 is Type.NullableType && type1 !is Type.NullableType)) {
            val win = compare2Types(type1, type2.realType, token)
            if (win) return true
        }
    } else if (unpackNullForFirst) {
        if ((type1 is Type.NullableType && type2 !is Type.NullableType)) {
            val win = compare2Types(type1.realType, type2, token)
            if (win) return true
        }
    }


    if (type1.toString() == type2.toString() && !isDifferentPkgs) {
        return true
    }

    // comparing with nothing is always true, its bottom type, subtype of all types,
    // so we can return nothing from switch expr branches, beside u cant do it with different types

    return type1.name == InternalTypes.Nothing.name || type2.name == InternalTypes.Nothing.name
}
