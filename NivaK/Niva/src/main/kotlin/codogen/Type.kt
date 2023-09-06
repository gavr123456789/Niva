package codogen

import frontend.typer.Type

fun Type.generateType(): String {
    return when (this) {
        is Type.InternalType -> this.name
        is Type.NullableInternalType -> this.name
        is Type.Lambda -> {
            this.name
        }

        is Type.GenericType -> TODO()
        is Type.NullableUserType -> TODO()
        is Type.UserType -> TODO()
    }
}
