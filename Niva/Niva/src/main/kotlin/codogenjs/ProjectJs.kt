package main.codogenjs

import frontend.resolver.MAIN_PKG_NAME
import frontend.resolver.Package
import frontend.resolver.Project
import kotlinx.serialization.json.JsonNull.content
import main.frontend.parser.types.ast.Statement
import java.io.File

const val content = $$"""
export function throwWithMessage(msg) {
    throw new Error(msg)
}        
        
/**
 * @param {Int} self
 */
export function Int__inc(self) {
    return self + 1;
}
/**
 * @param {Int} self
 */
export function Int__dec(self) {
    return self - 1;
}

/**
 * @param {Int} self
 */
export function Int__toFloat(self) {
    return self;
}

/**
 * @param {Int} self
 */
export function Int__toDouble(self) {
    return self;
}

/**
 * @param {Int} self
 */
export function Int__toLong(self) {
    return self;
}

/**
 * @param {Int} self
 */
export function Int__toString(self) {
    return self.toString();
}

/**
 * @param {Int} self
 */
export function Int__toChar(self) {
    return String.fromCharCode(self);
}

/**
 * @param {Int} self
 * @param {Int} to
 * @param {Fn} block
 */
export function Int__toDo(self, to, block) {
    for (let i = self; i <= to; i++) {
        block(i);
    }
}

/**
 * @param {Int} self
 * @param {Int} downTo
 * @param {Fn} block
 */
export function Int__downToDo(self, downTo, block) {
    for (let i = self; i >= downTo; i--) {
        block(i);
    }
}

export function Any__toString(value, indent = 0) {
    const space = "    ".repeat(indent);

    // primitives
    if (value === null) return "null";
    if (typeof value !== "object") {
        if (typeof value === "string") return `"${value}"`;
        return String(value);
    }



    // arrays
    if (Array.isArray(value)) {
        if (value.length === 0) return "{}";
        let result = "{\n";
        for (const item of value) {
            result += space + "    " + Any__toString(item, indent + 1) + ",\n";
        }
        result += space + "}";
        return result;
    }
    
        // Set
    if (value instanceof Set) {
        if (value.size === 0) return "Set {}";
        let result = "#(\n";
        for (const item of value) {
            result += space + "    " + Any__toString(item, indent + 1) + ",\n";
        }
        result += space + ")";
        return result;
    }

    // Map
    if (value instanceof Map) {
        if (value.size === 0) return "Map {}";
        let result = "#{\n";
        for (const [key, val] of value) {
            result +=
                space +
                "    " +
                `${Any__toString(key, indent + 1)} => ${Any__toString(val, indent + 1)},\n`;
        }
        result += space + "}";
        return result;
    }

    // regular objects
    const entries = Object.entries(value);

    let result = "";
    if (value.constructor && value.constructor.name !== "Object") {
        result += value.constructor.name + "\n";
    }

    if (entries.length === 0) return result;

    result += entries
        .map(
            ([key, val]) =>
                space +
                (value.constructor && value.constructor.name !== "Object"
                    ? "    "
                    : "") +
                `${key}: ${Any__toString(val, indent + 1)}`,
        )
        .join("\n");

    return result;
}

/**
 * @param {Any} obj
 */
export function Any__echo(obj) {
    console.log(Any__toString(obj));
}

/**
 * @param {String} str
 */
export function String__uppercase(str) {
    return String(str).toUpperCase();
}

/**
 * @param {String} str
 */
export function String__reversed(str) {
    return String(str).split('').reverse().join('');
}

/**
 * @param {String} str
 */
export function String__count(str) {
    return String(str).length;
}

/**
 * @param {String} str
 */
export function String__trim(str) {
    return String(str).trim();
}

/**
 * @param {String} str
 */
export function String__isEmpty(str) {
    return String(str).length === 0;
}

/**
 * @param {String} str
 */
export function String__isNotEmpty(str) {
    return String(str).length !== 0;
}

/**
 * @param {String} str
 */
export function String__isBlank(str) {
    return String(str).trim().length === 0;
}

/**
 * @param {String} str
 */
export function String__isNotBlank(str) {
    return String(str).trim().length !== 0;
}

/**
 * @param {String} str
 */
export function String__toInt(str) {
    return parseInt(str);
}

/**
 * @param {String} str
 */
export function String__toFloat(str) {
    return parseFloat(str);
}

/**
 * @param {String} str
 */
export function String__toDouble(str) {
    return parseFloat(str);
}

/**
 * @param {String} str
 */
export function String__lowercase(str) {
    return String(str).toLowerCase();
}

/**
 * @param {String} str
 */
export function String__first(str) {
    return String(str)[0];
}

/**
 * @param {String} str
 */
export function String__last(str) {
    const s = String(str);
    return s[s.length - 1];
}

/**
 * @param {String} str
 * @param {Int} index
 */
export function String__at(str, index) {
    return String(str)[index];
}

/**
 * @param {String} str
 * @param {Int} start
 * @param {Int} end
 */
export function String__substring(str, start, end) {
    return String(str).substring(start, end);
}

/**
 * @param {String} str
 * @param {String} substring
 */
export function String__contains(str, substring) {
    return String(str).includes(substring);
}

/**
 * @param {String} str
 * @param {String} oldVal
 * @param {String} newVal
 */
export function String__replace(str, oldVal, newVal) {
    return String(str).replaceAll(oldVal, newVal);
}

/**
 * @param {Bool} self
 */
export function Bool__not(self) {
    return !self;
}

/**
 * @param {Bool} self
 */
export function Bool__isTrue(self) {
    return self === true;
}

/**
 * @param {Bool} self
 */
export function Bool__isFalse(self) {
    return self === false;
}

/**
 * @param {Bool} self
 * @param {Bool} other
 */
export function Bool__or(self, other) {
    return self || other;
}

/**
 * @param {Bool} self
 * @param {Bool} other
 */
export function Bool__and(self, other) {
    return self && other;
}

/**
 * @param {Bool} self
 * @param {Fn} block
 */
export function Bool__ifTrue(self, block) {
    if (self) {
        block();
    }
}

/**
 * @param {Bool} self
 * @param {Fn} block
 */
export function Bool__ifFalse(self, block) {
    if (!self) {
        block();
    }
}

/**
 * @param {Bool} self
 * @param {Fn} blockTrue
 * @param {Fn} blockFalse
 */
export function Bool__ifTrueIfFalse(self, blockTrue, blockFalse) {
    if (self) {
        return blockTrue();
    } else {
        return blockFalse();
    }
}

/**
 * @param {Bool} self
 * @param {Fn} blockFalse
 * @param {Fn} blockTrue
 */
export function Bool__ifFalseIfTrue(self, blockFalse, blockTrue) {
    if (self) {
        return blockTrue();
    } else {
        return blockFalse();
    }
}

// --- List ---

/**
 * @param {List} self
 */
export function List__count(self) {
    return self.length;
}

/**
 * @param {List} self
 */
export function List__first(self) {
    if (self.length === 0) throw new Error("List is empty");
    return self[0];
}

/**
 * @param {List} self
 */
export function List__last(self) {
    if (self.length === 0) throw new Error("List is empty");
    return self[self.length - 1];
}

/**
 * @param {List} self
 */
export function List__firstOrNull(self) {
    if (self.length === 0) return null;
    return self[0];
}

/**
 * @param {List} self
 */
export function List__lastOrNull(self) {
    if (self.length === 0) return null;
    return self[self.length - 1];
}

/**
 * @param {List} self
 */
export function List__toList(self) {
    return [...self];
}

/**
 * @param {List} self
 */
export function List__toMutableList(self) {
    return [...self];
}

/**
 * @param {List} self
 */
export function List__shuffled(self) {
    const array = [...self];
    for (let i = array.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [array[i], array[j]] = [array[j], array[i]];
    }
    return array;
}

/**
 * @param {List} self
 */
export function List__asSequence(self) {
    return self; // Sequences not fully implemented, returning list
}

/**
 * @param {List} self
 */
export function List__isEmpty(self) {
    return self.length === 0;
}

/**
 * @param {List} self
 */
export function List__toSet(self) {
    return new Set(self);
}

/**
 * @param {List} self
 */
export function List__isNotEmpty(self) {
    return self.length !== 0;
}

/**
 * @param {List} self
 */
export function List__reversed(self) {
    return [...self].reverse();
}

/**
 * @param {List} self
 */
export function List__sum(self) {
    return self.reduce((a, b) => a + b, 0);
}

/**
 * @param {List} self
 * @param {List} other
 */
export function List__plus(self, other) {
    if (Array.isArray(other)) {
        return self.concat(other);
    } else {
        return self.concat([other]);
    }
}

/**
 * @param {List} self
 * @param {List} other
 */
export function List__minus(self, other) {
    if (Array.isArray(other)) {
        const otherSet = new Set(other);
        return self.filter(x => !otherSet.has(x));
    } else {
        return self.filter(x => x !== other);
    }
}

/**
 * @param {List} self
 * @param {Fn} block
 */
export function List__forEach(self, block) {
    self.forEach(block);
}

/**
 * @param {List} self
 * @param {Fn} block
 */
export function List__onEach(self, block) {
    self.forEach(block);
    return self;
}

/**
 * @param {List} self
 * @param {Fn} block
 */
export function List__forEachIndexed(self, block) {
    self.forEach((item, index) => block(index, item));
}

/**
 * @param {List} self
 * @param {Fn} block
 */
export function List__map(self, block) {
    return self.map(block);
}

/**
 * @param {List} self
 * @param {Fn} block
 */
export function List__mapIndexed(self, block) {
    return self.map((item, index) => block(index, item));
}

/**
 * @param {List} self
 * @param {Fn} block
 */
export function List__filter(self, block) {
    return self.filter(block);
}

/**
 * @param {List} self
 * @param {Int} index
 */
export function List__at(self, index) {
    if (index < 0 || index >= self.length) throw new Error("Index out of bounds");
    return self[index];
}

/**
 * @param {List} self
 * @param {Int} index
 */
export function List__getOrNull(self, index) {
    if (index < 0 || index >= self.length) return null;
    return self[index];
}

/**
 * @param {List} self
 * @param {Any} element
 */
export function List__contains(self, element) {
    return self.includes(element);
}

/**
 * @param {List} self
 * @param {Int} n
 */
export function List__drop(self, n) {
    return self.slice(n);
}

/**
 * @param {List} self
 * @param {Int} n
 */
export function List__dropLast(self, n) {
    return self.slice(0, -n);
}

/**
 * @param {List} self
 * @param {Int} size
 */
export function List__chunked(self, size) {
    const result = [];
    for (let i = 0; i < self.length; i += size) {
        result.push(self.slice(i, i + size));
    }
    return result;
}

/**
 * @param {List} self
 * @param {String|Fn} arg1
 */
export function List__joinToString(self, arg1) {
    if (typeof arg1 === "function") {
        return self.map(arg1).join(", ");
    } else {
        return self.join(arg1);
    }
}

/**
 * @param {List} self
 * @param {Fn} predicate
 */
export function List__indexOfFirst(self, predicate) {
    return self.findIndex(predicate);
}

/**
 * @param {List} self
 * @param {Fn} predicate
 */
export function List__indexOfLast(self, predicate) {
    for (let i = self.length - 1; i >= 0; i--) {
        if (predicate(self[i])) return i;
    }
    return -1;
}

/**
 * @param {List} self
 * @param {Fn} transform
 */
export function List__sortedBy(self, transform) {
    return [...self].sort((a, b) => {
        const valA = transform(a);
        const valB = transform(b);
        if (valA < valB) return -1;
        if (valA > valB) return 1;
        return 0;
    });
}

/**
 * @param {List} self
 * @param {String} separator
 * @param {Fn} transform
 */
export function List__joinWithTransform(self, separator, transform) {
    return self.map(transform).join(separator);
}

/**
 * @param {List} self
 * @param {Any} initial
 * @param {Fn} operation
 */
export function List__injectInto(self, initial, operation) {
    return self.reduce(operation, initial);
}

/**
 * @param {List} self
 * @param {Fn} operation
 */
export function List__reduce(self, operation) {
    if (self.length === 0) throw new Error("Empty list cannot be reduced");
    return self.reduce(operation);
}

/**
 * @param {List} self
 * @param {Fn} predicate
 */
export function List__partition(self, predicate) {
    const trueList = [];
    const falseList = [];
    self.forEach(item => {
        if (predicate(item)) {
            trueList.push(item);
        } else {
            falseList.push(item);
        }
    });
    return [trueList, falseList];
}

/**
 * @param {List} self
 * @param {Fn} selector
 */
export function List__sumOf(self, selector) {
    return self.reduce((acc, item) => acc + selector(item), 0);
}

/**
 * @param {List} self
 * @param {Fn} predicate
 */
export function List__find(self, predicate) {
    const res = self.find(predicate);
    return res === undefined ? null : res;
}

/**
 * @param {List} self
 * @param {Int} from
 * @param {Int} to
 */
export function List__viewFromTo(self, from, to) {
    return self.slice(from, to);
}

/**
 * @param {List} self
 */
export function List__mut__clear(self) {
    self.length = 0;
}

/**
 * @param {List} self
 * @param {Any|Int} arg1
 * @param {Any} arg2
 */
export function List__mut__add(self, arg1, arg2) {
    if (arg2 === undefined) {
        self.push(arg1);
    } else {
        self.splice(arg1, 0, arg2);
    }
}

/**
 * @param {List} self
 * @param {Any} item
 */
export function List__mut__addFirst(self, item) {
    self.unshift(item);
}

/**
 * @param {List} self
 * @param {List} other
 */
export function List__mut__addAll(self, other) {
    self.push(...other);
}

/**
 * @param {List} self
 * @param {Int} index
 */
export function List__mut__removeAt(self, index) {
    if (index >= 0 && index < self.length) {
        self.splice(index, 1);
    }
}

/**
 * @param {List} self
 * @param {Any} item
 */
export function List__mut__remove(self, item) {
    const index = self.indexOf(item);
    if (index !== -1) {
        self.splice(index, 1);
        return true;
    }
    return false;
}

/**
 * @param {List} self
 * @param {Int} index
 * @param {Any} item
 */
export function List__mut__set(self, index, item) {
    if (index < 0 || index >= self.length) throw new Error("Index out of bounds");
    self[index] = item;
}

// --- Set ---

/**
 * @param {Set} self
 */
export function Set__count(self) {
    return self.size;
}

/**
 * @param {Set} self
 */
export function Set__mut__clear(self) {
    self.clear();
}

/**
 * @param {Set} self
 */
export function Set__first(self) {
    if (self.size === 0) throw new Error("Set is empty");
    return self.values().next().value;
}

/**
 * @param {Set} self
 */
export function Set__last(self) {
    if (self.size === 0) throw new Error("Set is empty");
    return Array.from(self).pop();
}

/**
 * @param {Set} self
 */
export function Set__toList(self) {
    return Array.from(self);
}

/**
 * @param {Set} self
 */
export function Set__toMutableList(self) {
    return Array.from(self);
}

/**
 * @param {Set} self
 */
export function Set__toMutableSet(self) {
    return new Set(self);
}

/**
 * @param {Set} self
 */
export function Set__toSet(self) {
    return new Set(self);
}

/**
 * @param {Set} self
 * @param {Set} other
 */
export function Set__plus(self, other) {
    const result = new Set(self);
    for (const item of other) {
        result.add(item);
    }
    return result;
}

/**
 * @param {Set} self
 * @param {Set} other
 */
export function Set__minus(self, other) {
    const result = new Set(self);
    for (const item of other) {
        result.delete(item);
    }
    return result;
}

/**
 * @param {Set} self
 * @param {Fn} block
 */
export function Set__forEach(self, block) {
    self.forEach(block);
}

/**
 * @param {Set} self
 * @param {Fn} block
 */
export function Set__onEach(self, block) {
    self.forEach(block);
    return self;
}

/**
 * @param {Set} self
 * @param {Fn} block
 */
export function Set__map(self, block) {
    const result = [];
    for (const item of self) {
        result.push(block(item));
    }
    return result;
}

/**
 * @param {Set} self
 * @param {Fn} block
 */
export function Set__mapIndexed(self, block) {
    let index = 0;
    const result = [];
    for (const item of self) {
        result.push(block(index++, item));
    }
    return result;
}

/**
 * @param {Set} self
 * @param {Fn} block
 */
export function Set__filter(self, block) {
    const result = new Set();
    for (const item of self) {
        if (block(item)) {
            result.add(item);
        }
    }
    return result;
}

/**
 * @param {Set} self
 * @param {Any} item
 */
export function Set__intersect(self, other) {
    const result = new Set();
    for (const item of self) {
        if (other.has(item)) {
            result.add(item);
        }
    }
    return result;
}

/**
 * @param {Set} self
 * @param {Any} item
 */
export function Set__contains(self, item) {
    return self.has(item);
}

/**
 * @param {Set} self
 * @param {Set} other
 */
export function Set__containsAll(self, other) {
    for (const item of other) {
        if (!self.has(item)) return false;
    }
    return true;
}

/**
 * @param {Set} self
 * @param {Any} item
 */
export function Set__mut__add(self, item) {
    self.add(item);
}

/**
 * @param {Set} self
 * @param {Any} item
 */
export function Set__mut__remove(self, item) {
    return self.delete(item);
}

/**
 * @param {Set} self
 * @param {Set} other
 */
export function Set__mut__addAll(self, other) {
    for (const item of other) {
        self.add(item);
    }
    return true;
}

// --- Map ---

/**
 * @param {Map} self
 */
export function Map__count(self) {
    return self.size;
}

/**
 * @param {Map} self
 */
export function Map__isEmpty(self) {
    return self.size === 0;
}

/**
 * @param {Map} self
 */
export function Map__isNotEmpty(self) {
    return self.size !== 0;
}

/**
 * @param {Map} self
 */
export function Map__keys(self) {
    return new Set(self.keys());
}

/**
 * @param {Map} self
 */
export function Map__values(self) {
    return new Set(self.values());
}

/**
 * @param {Map} self
 */
export function Map__toMap(self) {
    return new Map(self);
}

/**
 * @param {Map} self
 */
export function Map__toMutableMap(self) {
    return new Map(self);
}

/**
 * @param {Map} self
 * @param {Map} other
 */
export function Map__plus(self, other) {
    const result = new Map(self);
    for (const [key, value] of other) {
        result.set(key, value);
    }
    return result;
}

/**
 * @param {Map} self
 * @param {Any} key
 */
export function Map__minus(self, key) {
    const result = new Map(self);
    result.delete(key);
    return result;
}

/**
 * @param {Map} self
 * @param {Fn} block
 */
export function Map__forEach(self, block) {
    for (const [key, value] of self) {
        block(key, value);
    }
}

/**
 * @param {Map} self
 * @param {Fn} block
 */
export function Map__map(self, block) {
    const result = [];
    for (const [key, value] of self) {
        result.push(block(key, value));
    }
    return result;
}

/**
 * @param {Map} self
 * @param {Fn} block
 */
export function Map__filter(self, block) {
    const result = new Map();
    for (const [key, value] of self) {
        if (block(key, value)) {
            result.set(key, value);
        }
    }
    return result;
}

/**
 * @param {Map} self
 * @param {Any} key
 */
export function Map__at(self, key) {
    return self.at(key);
}

/**
 * @param {Map} self
 * @param {Any} key
 */
export function Map__containsKey(self, key) {
    return self.has(key);
}

/**
 * @param {Map} self
 * @param {Any} value
 */
export function Map__containsValue(self, value) {
    for (const v of self.values()) {
        if (v === value) return true;
    }
    return false;
}

///// mutable map
/**
 * @param {Map} self
 */
export function Map__mut__clear(self) {
    self.clear();
}

/**
 * @param {Map} self
 * @param {Any} key
 */
export function Map__mut__remove(self, key) {
    return self.delete(key);
}

/**
 * @param {Map} self
 * @param {Map} other
 */
export function Map__mut__putAll(self, other) {
    for (const [key, value] of other) {
        self.set(key, value);
    }
}

/**
 * @param {Map} self
 * @param {Any} key
 * @param {Any} value
 */
export function Map__mut__atPut(self, key, value) {
    self.set(key, value);
}

/**
 * @param {Map} self
 * @param {Any} key
 * @param {Fn} block
 */
export function Map__mut__getOrPut(self, key, block) {
    if (self.has(key)) {
        return self.at(key);
    }
    const value = block();
    self.set(key, value);
    return value;
}

/**
 * @param {Map} self
 * @param {Any} key
 * @param {Any} value
 */
export function Map__mut__putIfAbsent(self, key, value) {
    if (!self.has(key)) {
        self.set(key, value);
    }
}

// immutable map functions for mutable map
export const Map__mut__count = Map__count;
export const Map__mut__isEmpty = Map__isEmpty;
export const Map__mut__isNotEmpty = Map__isNotEmpty;
export const Map__mut__keys = Map__keys;
export const Map__mut__values = Map__values;
export const Map__mut__toMap = Map__toMap;
export const Map__mut__toMutableMap = Map__toMutableMap;
export const Map__mut__plus = Map__plus;
export const Map__mut__minus = Map__minus;
export const Map__mut__forEach = Map__forEach;
export const Map__mut__map = Map__map;
export const Map__mut__filter = Map__filter;
export const Map__mut__at = Map__at;
export const Map__mut__containsKey = Map__containsKey;
export const Map__mut__containsValue = Map__containsValue;

// immutable set functions for mutable set
export const Set__mut__count = Set__count;
export const Set__mut__first = Set__first;
export const Set__mut__last = Set__last;
export const Set__mut__toList = Set__toList;
export const Set__mut__toMutableList = Set__toMutableList;
export const Set__mut__toMutableSet = Set__toMutableSet;
export const Set__mut__toSet = Set__toSet;
export const Set__mut__plus = Set__plus;
export const Set__mut__minus = Set__minus;
export const Set__mut__forEach = Set__forEach;
export const Set__mut__onEach = Set__onEach;
export const Set__mut__map = Set__map;
export const Set__mut__mapIndexed = Set__mapIndexed;
export const Set__mut__filter = Set__filter;
export const Set__mut__intersect = Set__intersect;
export const Set__mut__contains = Set__contains;
export const Set__mut__containsAll = Set__containsAll;
// list
export const List__mut__count = List__count;
export const List__mut__first = List__first;
export const List__mut__last = List__last;
export const List__mut__firstOrNull = List__firstOrNull;
export const List__mut__lastOrNull = List__lastOrNull;
export const List__mut__toList = List__toList;
export const List__mut__toMutableList = List__toMutableList;
export const List__mut__shuffled = List__shuffled;
export const List__mut__asSequence = List__asSequence;
export const List__mut__isEmpty = List__isEmpty;
export const List__mut__toSet = List__toSet;
export const List__mut__isNotEmpty = List__isNotEmpty;
export const List__mut__reversed = List__reversed;
export const List__mut__sum = List__sum;
export const List__mut__plus = List__plus;
export const List__mut__minus = List__minus;
export const List__mut__forEach = List__forEach;
export const List__mut__onEach = List__onEach;
export const List__mut__forEachIndexed = List__forEachIndexed;
export const List__mut__map = List__map;
export const List__mut__mapIndexed = List__mapIndexed;
export const List__mut__filter = List__filter;
export const List__mut__at = List__at;
export const List__mut__getOrNull = List__getOrNull;
export const List__mut__contains = List__contains;
export const List__mut__drop = List__drop;
export const List__mut__dropLast = List__dropLast;
export const List__mut__chunked = List__chunked;
export const List__mut__joinToString = List__joinToString;
export const List__mut__indexOfFirst = List__indexOfFirst;
export const List__mut__indexOfLast = List__indexOfLast;
export const List__mut__sortedBy = List__sortedBy;
export const List__mut__joinWithTransform = List__joinWithTransform;
export const List__mut__injectInto = List__injectInto;
export const List__mut__reduce = List__reduce;
export const List__mut__partition = List__partition;
export const List__mut__sumOf = List__sumOf;
export const List__mut__find = List__find;
export const List__mut__viewFromTo = List__viewFromTo;


"""



/**
 * Generates a JS project similarly to generateKtProject.
 *
 * It’s important to understand how Niva projects are structured:
 *  - all user-defined types and functions live in “regular” packages
 *    (often the ones corresponding to a main.niva file);
 *  - the special package MAIN_PKG_NAME ("mainNiva") contains only
 *    top-level expressions (resolver.topLevelStatements) and imports
 *    of user packages.
 *
 * Because of that, JS generation follows two rules:
 *  1) for every “regular” package that contains declarations, we produce a <pkg>.js file;
 *  2) for MAIN_PKG_NAME we always generate main.js, and it contains
 *     only the top-level expressions, not any type declarations.
 */
fun generateJsProject(outputDir: File, mainProject: Project, topLevelStatements: List<Statement>) {
    if (!outputDir.exists()) {
        outputDir.mkdirs()
    }

    // 1 generate declarations from pkgs
    mainProject.packages.values
        .filter { it.packageName != MAIN_PKG_NAME && it.declarations.isNotEmpty() }
        .forEach { pkg ->
            generateJsFileForPackage(outputDir, pkg, emptyList())
        }


    // 2 generate mainNiva.js from MAIN_PKG_NAME with topLevelStatements.
    val mainPkg = mainProject.packages[MAIN_PKG_NAME]
    if (topLevelStatements.isNotEmpty() && mainPkg != null) {
        generateJsFileForPackage(outputDir, mainPkg, topLevelStatements)
    }

    // 3 generate niva std for js

    val commonFile = File(outputDir, "common.js")
    if (!commonFile.exists()) {
        commonFile.writeText(content)
    }
}

private fun generateJsFileForPackage(baseDir: File, pkg: Package, extraStatements: List<Statement>) {
	// generate mainNiva.js for MAIN_PKG_NAME with top-level expressions only
	// use package name for regular packages
	val fileName = if (pkg.packageName == MAIN_PKG_NAME) "$MAIN_PKG_NAME.js" else "${pkg.packageName}.js"
    val file = File(baseDir, fileName)

    // initialize source map builder
    val sourceMapBuilder = SourceMapBuilder(fileName)
    JsCodegenContext.sourceMapBuilder = sourceMapBuilder

    val code = buildString {
        val imports = generateJsImports(pkg)
        append(imports)
        // update position after imports
        sourceMapBuilder.advancePosition(imports)

        // use declarations for regular packages
        // for MAIN_PKG_NAME use top-level statements (extraStatements)
        // since pkg.declarations is usually empty
        val bodyStatements: List<Statement> = when {
            pkg.packageName == MAIN_PKG_NAME -> extraStatements
            extraStatements.isNotEmpty() -> pkg.declarations + extraStatements
            else -> pkg.declarations
        }

        val body = codegenJs(bodyStatements, pkg = pkg)
        if (body.isNotBlank()) {
            if (isNotEmpty()) {
                append('\n')
                sourceMapBuilder.advancePosition("\n")
            }
            append(body)
            sourceMapBuilder.advancePosition(body)
            append('\n')
            sourceMapBuilder.advancePosition("\n")
        }
        
        // add reference to source map
        append("//# sourceMappingURL=")
        append(fileName)
        append(".map")
        // do not update position for sourceMappingURL comment
    }

    file.writeText(code)
    
    // write source map file
    val sourceMapFile = File(baseDir, "$fileName.map")
    sourceMapFile.writeText(sourceMapBuilder.toJson())
    
    // clear context
    JsCodegenContext.sourceMapBuilder = null
}

private fun generateJsImports(pkg: Package): String = buildString {
    val allImports = (pkg.imports + pkg.importsFromUse)
        .filter { it.isNotBlank() && it != pkg.packageName && it != "core" && it != "common"}
        .toSortedSet()

   	allImports.forEach { imp ->
		val alias = imp.replace('.', '_')
		val fileName = if (imp == MAIN_PKG_NAME) "mainNiva" else imp
		append("import * as ")
        append(alias)
        append(" from \"./")
        append(fileName)
        append(".js\";\n")
    }

    // for kotlin we can always import main since empty file generated
    // but in js no empty files are generated, so we need to remove it

    append("import * as common from \"./common.js\";")
}
