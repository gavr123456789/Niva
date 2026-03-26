package main

import (
	"fmt"
	"strings"
	"unicode/utf8"
)

func reverseString(s string) string {
	runes := []rune(s)
	for i, j := 0, len(runes)-1; i < j; i, j = i+1, j-1 {
		runes[i], runes[j] = runes[j], runes[i]
	}
	return string(runes)
}

func main() {
	// "foo-bar" count
	// В Go у строки есть длина в байтах через len(...)
	// Для ASCII это совпадает с количеством символов.
	fmt.Println(`"foo-b" count ->`, len("foo-bar"))
	x := len("sas-sus")
	_ = x
	_ = len("qwf")

	// Если нужен именно count символов Unicode:
	fmt.Println(`"foo-bar" rune count ->`, utf8.RuneCountInString("foo-bar"))

	// "drawer" reversed
	fmt.Println(`"drawer" reversed ->`, reverseString("drawer"))

	// "BAOBAB" lowercase
	fmt.Println(`"BAOBAB" lowercase ->`, strings.ToLower("BAOBAB"))

	// // binary
	// "ee" == "ee"
	fmt.Println(`"ee" == "ee" ->`, "ee" == "ee")

	// "foo" + "bar"
	fmt.Println(`"foo" + "bar" ->`, "foo"+"bar")

	// // keyword
	// "foo-bar" split: "-"
	fmt.Println(`"foo-bar" split: "-" ->`, strings.Split("foo-bar", "-"))

	// "abcdef" forEach: [char -> char echo]
	fmt.Print(`"abcdef" forEach: [char -> char echo] -> `)
	for _, char := range "abcdef" {
		fmt.Printf("%c ", char)
	}
	fmt.Println()

	// "baobab" filter: [it == 'b']
	fmt.Print(`"baobab" filter: [it == 'b'] -> `)
	for _, char := range "baobab" {
		if char == 'b' {
			fmt.Printf("%c", char)
		}
	}
	fmt.Println()

	// "foo-bar-baz" replace: "-" with: " "
	fmt.Println(`"foo-bar-baz" replace: "-" with: " " ->`, strings.ReplaceAll("foo-bar-baz", "-", " "))

	// "chocolate" contains: "late"
	fmt.Println(`"chocolate" contains: "late" ->`, strings.Contains("chocolate", "late"))
}
