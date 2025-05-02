
//sealed class Dynamic()
//
//class DynamicStr(val value: String): Dynamic()
//class DynamicInt(val value: Int): Dynamic()
//class DynamicDouble(val value: Double): Dynamic()
//
//class DynamicList(val value: List<Dynamic>): Dynamic()
//class DynamicObject(val value: MutableMap<String, Dynamic>): Dynamic()
//
//
//class Wallet(val money : Double) {
//    companion object {
//        fun toDynamic(v: Wallet): DynamicObject {
//            return DynamicObject(
//                mutableMapOf(
//                    "money" to DynamicDouble(v.money),
//                )
//            )
//        }
//        fun fromDynamic(v: DynamicObject): Wallet {
//            return Wallet(
//                money = (v.value["money"]!! as DynamicDouble).value,
//            )
//        }
//    }
//}
//
//class Person(val name: String, val age: Int?, val wallet: Wallet, val scores: List<Int>, val wallets: List<Wallet>) {
//    companion object {
//        fun toDynamic(v: Person): DynamicObject {
//            return DynamicObject(
//                mutableMapOf(
//                    "name" to DynamicStr(v.name),
//                    //"age" to if (v.age != null) DynamicInt(v.age) else DynamicNullable(),
//                    "wallet" to Wallet.toDynamic(v.wallet),
//                    "scores" to DynamicList(v.scores.map{DynamicInt(it)}),
//                    "wallets" to DynamicList(v.wallets.map{Wallet.toDynamic(it)}),
//                ).also {
//                    if (v.age != null) { it["age"] = DynamicInt(v.age) }
//                }
//
//
//            )
//        }
//        fun fromDynamic(v: DynamicObject): Person {
//            return Person(
//                name = (v.value["name"]!! as DynamicStr).value,
//                age = (v.value["age"]!!as DynamicInt).value, // age = v.value["age"]!!.v as Int,
//                wallet = Wallet.fromDynamic(v.value["wallet"]!! as DynamicObject),
//                scores = (v.value["scores"] as DynamicList).value.map { (it as DynamicInt).value},
//                wallets = (v.value["wallets"]!! as DynamicList).value.map { Wallet.fromDynamic(it as DynamicObject) },
//            )
//        }
//    }
//}