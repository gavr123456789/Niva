package core

import (
	"fmt"
	"math/rand"
	"reflect"
	"strconv"
	"strings"
	"unicode"
)

type Unit struct{}

var UnitInstance = Unit{}

type IntRange struct{ Start, EndInclusive int }
type CharRange struct{ Start, EndInclusive rune }

type Option[T any] struct {
	Value    T
	HasValue bool
}

func ThrowWithMessage(msg string)              { panic(msg) }
func Error__throw(self any)                    { panic(fmt.Sprintf("%v", self)) }
func Error__throwWithMsg(self any, msg string) { panic(msg) }

func Int__inc(s int) int          { return s + 1 }
func Int__dec(s int) int          { return s - 1 }
func Int__toFloat(s int) float32  { return float32(s) }
func Int__toDouble(s int) float64 { return float64(s) }
func Int__toLong(s int) int64     { return int64(s) }
func Int__toChar(s int) rune      { return rune(s & 0xFF) }
func Int__toDo(s int, to int, f func(int)) {
	for i := s; i <= to; i++ {
		f(i)
	}
}
func Int__downToDo(s int, dt int, f func(int)) {
	for i := s; i >= dt; i-- {
		f(i)
	}
}
func Int__rangeTo(s int, to int) IntRange { return IntRange{s, to} }

// func Int__plus(s, o int) int              { return s + o }
// func Int__minus(s, o int) int             { return s - o }
// func Int__times(s, o int) int             { return s * o }
// func Int__div(s, o int) int               { return s / o }
// func Int__rem(s, o int) int               { return s % o }
// func Int__gt(s, o int) bool               { return s > o }
// func Int__lt(s, o int) bool               { return s < o }
// func Int__gte(s, o int) bool              { return s >= o }
// func Int__lte(s, o int) bool              { return s <= o }
// func Int__equals(s, o int) bool           { return s == o }
// func Int__notEquals(s, o int) bool        { return s != o }

func (r IntRange) ForEach(f func(int)) {
	if r.Start <= r.EndInclusive {
		for i := r.Start; i <= r.EndInclusive; i++ {
			f(i)
		}
	}
}
func IntRange__forEach(s IntRange, f func(int)) { s.ForEach(f) }
func IntRange__map[T any](s IntRange, f func(int) T) []T {
	var res []T
	if s.Start <= s.EndInclusive {
		for i := s.Start; i <= s.EndInclusive; i++ {
			res = append(res, f(i))
		}
	}
	return res
}
func IntRange__filter(s IntRange, f func(int) bool) []int {
	var res []int
	if s.Start <= s.EndInclusive {
		for i := s.Start; i <= s.EndInclusive; i++ {
			if f(i) {
				res = append(res, i)
			}
		}
	}
	return res
}
func IntRange__contains(s IntRange, v int) bool { return v >= s.Start && v <= s.EndInclusive }
func IntRange__isEmpty(s IntRange) bool         { return s.Start > s.EndInclusive }

func (r CharRange) ForEach(f func(rune)) {
	if r.Start <= r.EndInclusive {
		for i := r.Start; i <= r.EndInclusive; i++ {
			f(i)
		}
	}
}
func CharRange__forEach(s CharRange, f func(rune)) { s.ForEach(f) }
func CharRange__map[T any](s CharRange, f func(rune) T) []T {
	var res []T
	if s.Start <= s.EndInclusive {
		for i := s.Start; i <= s.EndInclusive; i++ {
			res = append(res, f(i))
		}
	}
	return res
}
func CharRange__filter(s CharRange, f func(rune) bool) []rune {
	var res []rune
	if s.Start <= s.EndInclusive {
		for i := s.Start; i <= s.EndInclusive; i++ {
			if f(i) {
				res = append(res, i)
			}
		}
	}
	return res
}
func CharRange__contains(s CharRange, v rune) bool { return v >= s.Start && v <= s.EndInclusive }
func CharRange__isEmpty(s CharRange) bool          { return s.Start > s.EndInclusive }

func Any__toString(v any, ind int) string {
	if v == nil {
		return "null"
	}
	val := reflect.ValueOf(v)
	sp := strings.Repeat("    ", ind)

	switch val.Kind() {
	case reflect.Slice, reflect.Array:
		if val.Len() == 0 {
			return "{}"
		}
		var b strings.Builder
		b.WriteString("{\n")
		for i := 0; i < val.Len(); i++ {
			b.WriteString(sp)
			b.WriteString("    ")
			b.WriteString(Any__toString(val.Index(i).Interface(), ind+1))
			b.WriteString("\n")
		}
		b.WriteString(sp)
		b.WriteString("}")
		return b.String()

	case reflect.Map:
		if val.Len() == 0 {
			return "Map {}"
		}
		var b strings.Builder
		b.WriteString("#{\n")
		ks := val.MapKeys()
		for _, k := range ks {
			b.WriteString(sp)
			b.WriteString("    ")
			b.WriteString(Any__toString(k.Interface(), ind+1))
			b.WriteString(" => ")
			b.WriteString(Any__toString(val.MapIndex(k).Interface(), ind+1))
			b.WriteString(",\n")
		}
		b.WriteString(sp)
		b.WriteString("}")
		return b.String()

	case reflect.Struct:
		if _, ok := v.(Unit); ok {
			return "()"
		}
		return fmt.Sprintf("%v", v)

	case reflect.String:
		return v.(string) // "\"" + v.(string) + "\""

	case reflect.Bool:
		return strconv.FormatBool(v.(bool))

	default:
		return fmt.Sprintf("%v", v)
	}
}
func Any__echo(o any) { fmt.Println(Any__toString(o, 0)) }

func String__uppercase(s string) string { return strings.ToUpper(s) }
func String__reversed(s string) string {
	rs := []rune(s)
	for i, j := 0, len(rs)-1; i < j; i, j = i+1, j-1 {
		rs[i], rs[j] = rs[j], rs[i]
	}
	return string(rs)
}
func String__count(s string) int        { return len([]rune(s)) }
func String__trim(s string) string      { return strings.TrimSpace(s) }
func String__isEmpty(s string) bool     { return len(s) == 0 }
func String__isNotEmpty(s string) bool  { return len(s) != 0 }
func String__isBlank(s string) bool     { return len(strings.TrimSpace(s)) == 0 }
func String__isNotBlank(s string) bool  { return len(strings.TrimSpace(s)) != 0 }
func String__toFloat(s string) float32  { f, _ := strconv.ParseFloat(s, 32); return float32(f) }
func String__toDouble(s string) float64 { f, _ := strconv.ParseFloat(s, 64); return f }
func String__lowercase(s string) string { return strings.ToLower(s) }
func String__first(s string) rune {
	rs := []rune(s)
	if len(rs) == 0 {
		panic("S empty")
	}
	return rs[0]
}
func String__last(s string) rune {
	rs := []rune(s)
	if len(rs) == 0 {
		panic("S empty")
	}
	return rs[len(rs)-1]
}
func String__at(s string, i int) rune {
	rs := []rune(s)
	if i < 0 || i >= len(rs) {
		panic("Idx out")
	}
	return rs[i]
}
func String__get(s string, i int) rune   { return String__at(s, i) }
func String__split(s, d string) []string { return strings.Split(s, d) }
func String__substring(s string, start, end int) string {
	rs := []rune(s)
	l := len(rs)
	if start < 0 {
		start = 0
	}
	if start > l {
		start = l
	}
	if end < 0 {
		end = 0
	}
	if end > l {
		end = l
	}
	if start <= end {
		return string(rs[start:end])
	}
	return string(rs[end:start])
}
func String__drop(s string, c int) string {
	rs := []rune(s)
	if c <= 0 {
		return s
	}
	if c >= len(rs) {
		return ""
	}
	return string(rs[c:])
}
func String__dropLast(s string, c int) string {
	rs := []rune(s)
	if c <= 0 {
		return s
	}
	if c >= len(rs) {
		return ""
	}
	return string(rs[:len(rs)-c])
}
func String__contains(s, sub string) bool        { return strings.Contains(s, sub) }
func String__replace_with(s, o, n string) string { return strings.ReplaceAll(s, o, n) }
func String__map(s string, f func(rune) rune) string {
	rs := []rune(s)
	r := make([]rune, len(rs))
	for i, ch := range rs {
		r[i] = f(ch)
	}
	return string(r)
}
func String__forEach(s string, f func(rune)) {
	for _, r := range []rune(s) {
		f(r)
	}
}
func String__filter(s string, p func(rune) bool) string {
	var r []rune
	for _, ch := range []rune(s) {
		if p(ch) {
			r = append(r, ch)
		}
	}
	return string(r)
}
func String__toInt(s string, r int) int { i, _ := strconv.ParseInt(s, r, 0); return int(i) }

func Bool__toString(s bool) string { return strconv.FormatBool(s) }
func Bool__not(s bool) bool        { return !s }
func Bool__isTrue(s bool) bool     { return s }
func Bool__isFalse(s bool) bool    { return !s }
func Bool__or(s, o bool) bool      { return s || o }
func Bool__and(s, o bool) bool     { return s && o }
func Bool__ifTrue(s bool, f func()) {
	if s {
		f()
	}
}
func Bool__ifFalse(s bool, f func()) {
	if !s {
		f()
	}
}
func Bool__ifTrue__ifFalse[T any](s bool, t, fa func() T) T {
	if s {
		return t()
	}
	return fa()
}
func Bool__ifFalse_ifTrue[T any](s bool, fa, t func() T) T {
	if s {
		return t()
	}
	return fa()
}

func Char__toInt(ch rune) int { return int(ch) }
func Char__toInt__base(ch rune, b int) int {
	v := -1
	if ch >= 48 && ch <= 57 {
		v = int(ch - 48)
	} else if ch >= 97 && ch <= 122 {
		v = int(ch - 97 + 10)
	} else if ch >= 65 && ch <= 90 {
		v = int(ch - 65 + 10)
	}
	if v < 0 || v >= b {
		panic("Idx out")
	}
	return v
}
func Char__code(s rune) int                  { return int(s) }
func Char__inc(s rune) rune                  { return rune((int(s) + 1) & 0xFF) }
func Char__dec(s rune) rune                  { return rune((int(s) - 1) & 0xFF) }
func Char__isDigit(s rune) bool              { return unicode.IsDigit(s) }
func Char__isLetter(s rune) bool             { return unicode.IsLetter(s) }
func Char__isLetterOrDigit(s rune) bool      { return unicode.IsLetter(s) || unicode.IsDigit(s) }
func Char__isLowerCase(s rune) bool          { return unicode.IsLower(s) }
func Char__isUpperCase(s rune) bool          { return unicode.IsUpper(s) }
func Char__isWhitespace(s rune) bool         { return unicode.IsSpace(s) }
func Char__lowercaseChar(s rune) rune        { return unicode.ToLower(s) }
func Char__uppercaseChar(s rune) rune        { return unicode.ToUpper(s) }
func Char__rangeTo(s rune, t rune) CharRange { return CharRange{s, t} }
func Char__equals(s, o rune) bool            { return s == o }
func Char__notEquals(s, o rune) bool         { return s != o }
func WhileTrue(c func() bool, b func()) {
	for c() {
		b()
	}
}

func List__count[T any](s []T) int { return len(s) }
func List__first[T any](s []T) T {
	if len(s) == 0 {
		panic("L e")
	}
	return s[0]
}
func List__last[T any](s []T) T {
	if len(s) == 0 {
		panic("L e")
	}
	return s[len(s)-1]
}
func List__firstOrNull[T any](s []T) Option[T] {
	if len(s) == 0 {
		var zero T
		return Option[T]{zero, false}
	}
	return Option[T]{s[0], true}
}
func List__lastOrNull[T any](s []T) Option[T] {
	if len(s) == 0 {
		var zero T
		return Option[T]{zero, false}
	}
	return Option[T]{s[len(s)-1], true}
}
func List__toList[T any](s []T) []T {
	r := make([]T, len(s))
	copy(r, s)
	return r
}
func List__shuffled[T any](s []T) []T {
	r := List__toList(s)
	rand.Shuffle(len(r), func(i, j int) { r[i], r[j] = r[j], r[i] })
	return r
}
func List__isEmpty[T any](s []T) bool    { return len(s) == 0 }
func List__isNotEmpty[T any](s []T) bool { return len(s) != 0 }
func List__reversed[T any](s []T) []T {
	r := make([]T, len(s))
	for i, j := 0, len(s)-1; j >= 0; i, j = i+1, j-1 {
		r[i] = s[j]
	}
	return r
}
func List__plus[T any](s []T, o []T) []T {
	return append(append([]T{}, s...), o...)
}
func List__plus_single[T any](s []T, o T) []T {
	return append(append([]T{}, s...), o)
}
func List__forEach[T any](s []T, f func(T)) {
	for _, it := range s {
		f(it)
	}
}
func List__onEach[T any](s []T) []T { return s }
func List__forEachIndexed[T any](s []T, f func(int, T)) {
	for i, it := range s {
		f(i, it)
	}
}
func List__map[T any, R any](s []T, f func(T) R) []R {
	r := make([]R, len(s))
	for i, it := range s {
		r[i] = f(it)
	}
	return r
}
func List__mapIndexed[T any, R any](s []T, f func(int, T) R) []R {
	r := make([]R, len(s))
	for i, it := range s {
		r[i] = f(i, it)
	}
	return r
}
func List__filter[T any](s []T, p func(T) bool) []T {
	var r []T
	for _, it := range s {
		if p(it) {
			r = append(r, it)
		}
	}
	return r
}
func List__at[T any](s []T, i int) T {
	if i < 0 || i >= len(s) {
		panic("Idx out")
	}
	return s[i]
}
func List__drop[T any](s []T, n int) []T {
	if n <= 0 {
		return s
	}
	if n >= len(s) {
		return []T{}
	}
	return s[n:]
}
func List__chunked[T any](s []T, sz int) [][]T {
	if sz <= 0 {
		panic("sz < 0")
	}
	var r [][]T
	for i := 0; i < len(s); i += sz {
		end := i + sz
		if end > len(s) {
			end = len(s)
		}
		r = append(r, s[i:end])
	}
	return r
}
func List__joinToString[T any](s []T, sep string) string {
	var p []string
	for _, it := range s {
		p = append(p, fmt.Sprintf("%v", it))
	}
	return strings.Join(p, sep)
}
func List__indexOfFirst[T any](s []T, p func(T) bool) int {
	for i, it := range s {
		if p(it) {
			return i
		}
	}
	return -1
}
func List__indexOfLast[T any](s []T) int { return -1 }
func List__injectInto[T any, R any](s []T, ini R, op func(R, T) R) R {
	r := ini
	for _, it := range s {
		r = op(r, it)
	}
	return r
}
func List__reduce[T any](s []T, op func(T, T) T) T {
	if len(s) == 0 {
		panic("e L red")
	}
	r := s[0]
	for i := 1; i < len(s); i++ {
		r = op(r, s[i])
	}
	return r
}
func List__partition[T any](s []T, p func(T) bool) [][]T {
	var t, f []T
	for _, it := range s {
		if p(it) {
			t = append(t, it)
		} else {
			f = append(f, it)
		}
	}
	return [][]T{t, f}
}
func List__find[T any](s []T, p func(T) bool) Option[T] {
	for _, it := range s {
		if p(it) {
			return Option[T]{it, true}
		}
	}
	var zero T
	return Option[T]{zero, false}
}
func List__viewFromTo[T any](s []T, f, t int) []T {
	l := len(s)
	if f < 0 {
		f = 0
	}
	if f > l {
		f = l
	}
	if t < 0 {
		t = 0
	}
	if t > l {
		t = l
	}
	if f <= t {
		return s[f:t]
	}
	return []T{}
}

func List__mut_clear[T any](s *[]T)    { *s = (*s)[:0] }
func List__mut_add[T any](s *[]T, i T) { *s = append(*s, i) }
func List__mut_addAt[T any](s *[]T, i int, it T) {
	if i < 0 || i > len(*s) {
		panic("Idx out")
	}
	*s = append((*s)[:i], append([]T{it}, (*s)[i:]...)...)
}
func List__mut_addFirst[T any](s *[]T, i T) { List__mut_addAt(s, 0, i) }
func List__mut_addAll[T any](s *[]T, o []T) { *s = append(*s, o...) }
func List__mut_removeAt[T any](s *[]T, i int) {
	if i < 0 || i >= len(*s) {
		panic("Idx out")
	}
	*s = append((*s)[:i], (*s)[i+1:]...)
}
func List__mut_set[T any](s *[]T, i int, it T) {
	if i < 0 || i >= len(*s) {
		panic("Idx out")
	}
	(*s)[i] = it
}

func Set__count[T comparable](s map[T]struct{}) int { return len(s) }
func Set__mut_clear[T comparable](s map[T]struct{}) {
	for k := range s {
		delete(s, k)
	}
}
func Set__first[T comparable](s map[T]struct{}) T {
	for k := range s {
		return k
	}
	panic("S e")
}
func Set__toList[T comparable](s map[T]struct{}) []T {
	r := make([]T, 0, len(s))
	for k := range s {
		r = append(r, k)
	}
	return r
}
func Set__plus[T comparable](s, o map[T]struct{}) map[T]struct{} {
	r := make(map[T]struct{})
	for k := range s {
		r[k] = struct{}{}
	}
	for k := range o {
		r[k] = struct{}{}
	}
	return r
}
func Set__minus[T comparable](s, o map[T]struct{}) map[T]struct{} {
	r := make(map[T]struct{})
	for k := range s {
		if _, ok := o[k]; !ok {
			r[k] = struct{}{}
		}
	}
	return r
}
func Set__forEach[T comparable](s map[T]struct{}, f func(T)) {
	for k := range s {
		f(k)
	}
}
func Set__filter[T comparable](s map[T]struct{}, p func(T) bool) map[T]struct{} {
	r := make(map[T]struct{})
	for k := range s {
		if p(k) {
			r[k] = struct{}{}
		}
	}
	return r
}
func Set__contains[T comparable](s map[T]struct{}, i T) bool {
	_, ok := s[i]
	return ok
}
func Set__mut_add[T comparable](s map[T]struct{}, i T) { s[i] = struct{}{} }
func Set__mut_remove[T comparable](s map[T]struct{}, i T) bool {
	_, ok := s[i]
	delete(s, i)
	return ok
}

func Map__count[K comparable, V any](s map[K]V) int    { return len(s) }
func Map__isEmpty[K comparable, V any](s map[K]V) bool { return len(s) == 0 }
func Map__keys[K comparable, V any](s map[K]V) map[K]struct{} {
	r := make(map[K]struct{})
	for k := range s {
		r[k] = struct{}{}
	}
	return r
}
func Map__plus[K comparable, V any](s, o map[K]V) map[K]V {
	r := make(map[K]V)
	for k, v := range s {
		r[k] = v
	}
	for k, v := range o {
		r[k] = v
	}
	return r
}
func Map__minus[K comparable, V any](s map[K]V, k K) map[K]V {
	r := make(map[K]V)
	for k2, v := range s {
		if k2 != k {
			r[k2] = v
		}
	}
	return r
}
func Map__forEach[K comparable, V any](s map[K]V, f func(K, V)) {
	for k, v := range s {
		f(k, v)
	}
}
func Map__at[K comparable, V any](s map[K]V, k K) V { return s[k] }
func Map__containsKey[K comparable, V any](s map[K]V, k K) bool {
	_, ok := s[k]
	return ok
}
func Map__mut_putAt[K comparable, V any](s map[K]V, k K, v V) {
	s[k] = v
}
