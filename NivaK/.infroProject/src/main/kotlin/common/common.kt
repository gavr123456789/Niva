class Box<T, G>(var x: T, var y: G) {
	override fun toString(): String {
		return "Box x: $x y: $y"    
    }
    companion object
}
class Xob<T, G, Q, W>(var box: Box<T, G>, var box2: Box<Q, W>) {
	override fun toString(): String {
		return "Xob box: $box box2: $box2"    
    }
    companion object
}
