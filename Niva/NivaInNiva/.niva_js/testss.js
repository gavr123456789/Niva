import * as core from "./core.js"


export class Person {
    constructor(name,age) {
        this.name = name;
        this.age = age;
    }
}


export function Person__hello(_this) {
    let name = _this.name
    let age = _this.age
    core.Any__echo(((("hello my name is 3212 ") + (name))))
}


const p = new Person("Alice", 24);
Person__hello(p);
const list = [ 1, 2, 3 ];
const hashSet = new Set([1, 2, 3]);
const hashMap = new Map([[1, 2], [3, 4]]);
core.Any__echo(list);
core.Any__echo(hashSet);
core.Any__echo(hashMap);
core.List__forEach((core.List__filter((core.List__map(list, (it) => {return ((it) * (2))})), (it) => {return ((((it) % (2))) === (0))})), (it) => {return core.Any__echo(it)});