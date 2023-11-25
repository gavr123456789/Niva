// switch expression(No ^_^)
let y = 42;
let x: string;
if (y > 100) {
	x = 'vala';
} else if (y === 7 || y / 2 === 8) {
	x = 'nim';
} else if (y / 7 === 6) {
	x = 'niva';
} else {
	x = 'something else';
}
console.log(x);

// switch statement
switch (x) {
	case 'nim':
	case 'pascal': 
    console.log('no'); break;
	case 'vala':
	case 'C#': 
    console.log('no'); break;
	case 'D':
	case 'C++':	
    console.log('no'); break;
	case 'ni':
	case 'va':
	case 'niva': 
    console.log('yes!'); break;
	default: console.log("I dont know"); break;
}

// if else statement
if (y < 10 || y / 5 === 4) {
  console.log("foo");
} else if (x === "niva" && y === 42) {
  console.log("yay!");
}

