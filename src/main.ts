
import {readFileSync, writeFileSync} from "fs";
import { exec } from "child_process";
import { generateNimCode } from "./niva";

const EXPORTED_NIM_CODE = "./niva.nim"

const filePath = process.argv.at(2)

if (!filePath){
	throw new Error("Missing a file argument.")
}
// console.time("readFileSync in ")
const nivaCode = readFileSync(filePath, "utf8");
// console.timeEnd("readFileSync in ")

// console.time("generated NimCode in ")
const [ast, nimCode] = generateNimCode(nivaCode, true, true )
// console.log("ast = ", JSON.stringify(ast, undefined, 2));
// console.timeEnd("generated NimCode in ")


writeFileSync(EXPORTED_NIM_CODE, nimCode)

// console.time("nim compile and run in ")

exec("nim r --hints=off " + EXPORTED_NIM_CODE, (err, stdout,) => {
  console.log(stdout);
})
// console.timeEnd("nim compile and run in ")

