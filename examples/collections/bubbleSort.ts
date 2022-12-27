const x = [3, 2, 1, 0];
console.log(x[1])
console.log(x.toString())

for(let it = 0; it < x.length - 1; it++) {
  const i = it
  for(let it = 0; it < x.length - i - 1; it++) {
    const j = it
    const curr = x[j]
    const next = x[j + 1]
    if(curr > next) {
      let temp = curr;
      x[j] = next;
      x[j + 1] = temp;
    }
  }
}

console.log(x.toString())
