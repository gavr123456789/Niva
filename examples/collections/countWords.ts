const sentence = "sas sus ses sas sas sus";
const words = sentence.split(" ");
const hashMap = new Map<string, number>();
hashMap.set("none", 0);

words.forEach(it => {
  const value = hashMap.get(it) ?? 0;
  hashMap.set(it, value + 1);
})

console.log(hashMap);
