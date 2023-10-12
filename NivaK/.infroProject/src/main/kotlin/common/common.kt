package common

import main.*
sealed class Shape(var area: Int) {
	override fun toString(): String {
		return "Shape area: $area"    
    }
    companion object
}
class Rectangle(var width: Int, var height: Int, area: Int) : Shape(area) {
	override fun toString(): String {
		return "Rectangle width: $width height: $height"    
    }
    companion object
}
class Circle(var radius: Int, area: Int) : Shape(area) {
	override fun toString(): String {
		return "Circle radius: $radius"    
    }
    companion object
}
