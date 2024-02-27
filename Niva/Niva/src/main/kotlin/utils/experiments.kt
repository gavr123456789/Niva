@file:Suppress("unused")

package main.utils

//simplest
fun buildString2(x: StringBuilder.((String) -> Unit) -> Unit): StringBuilder {
    val b = StringBuilder()
    val toCallSite: (String) -> Unit = { default: String ->
        b.append(default)
        b.append("\n")
    }
    b.x(toCallSite)
    return b
}

fun test() {
    buildString2 {
        it("sas")
    }
}

fun StringBuilder.builderWithReceiver(x: StringBuilder.((String) -> Unit) -> Unit) {
//    val q = StringBuilder()
    val toCallSite: (String) -> Unit = { default: String -> this.append(default) }
    this.x(toCallSite)
}

//    val builder = StringBuilder()
//    builder.builderWithReceiver { x ->
//        x("sas")
//        this.append(123)
//    }


// OPTION

//
//sealed class Option<out T>
//class Some<T>(var value: T) : Option<T>()
//data object None : Option<Nothing>()
//
//class Node<T>(
//    val data: T,
//    var prev: Option<Node<T>>
//)
//
//fun <T> Node<T>.toList(): List<T> {
//    val result = mutableListOf<T>(data)
//    var q = prev
//    while (q != None) {
//        when (q) {
//            is None -> {}
//            is Some -> {
//                result.add(q.value.data)
//                q = q.value.prev
//            }
//        }
//    }
//    return result
//}
//
//class MyList<T>(
//    val initialVal: T,
//    var head: Node<T> = Node(initialVal, None)
//)
//
//// 1 next: []
//// 1 next: [2 next: []]
//
//fun <T> MyList<T>.add(data: T) {
//    val result = Node(data = data, prev = Some(head))
//    head = result
//}
