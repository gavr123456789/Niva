
import {readFileSync, writeFileSync} from "fs";
import { exec } from "child_process";
import { generateNimCode } from "./niva";

// const EXPORTED_NIM_CODE = "./niva.nim"
//
// const filePath = process.argv.at(2)
//
// if (!filePath){
// 	throw new Error("Missing a file argument.")
// }
// const nivaCode = readFileSync(filePath, "utf8");

const [ast, nimCode] = generateNimCode("1 + (2 + 3)", true, true )
console.log("ast = ", JSON.stringify(ast, undefined, 2));


// writeFileSync(EXPORTED_NIM_CODE, nimCode)


// exec("nim r --hints=off " + EXPORTED_NIM_CODE, (err, stdout,) => {
//   console.log(stdout);
// })

