# Оставшиеся блокеры self-hosting через Clojure

## Цель

После устранения нелокальных `^` обеспечить реальный запуск self-hosted-компилятора, сгенерированного Clojure backend.

## Критичные блокеры

### 1. Backend-specific `@emit`

Bindings в `libs/binds/*.bind.niva`, `main.niva` и `compiler/compiler.niva` содержат Kotlin-шаблоны:

- Okio `FileSystem`;
- `ProcessBuilder`;
- `System`;
- файловые и process API.

Clojure backend не должен вставлять Kotlin-шаблон в Clojure output. Нужно добавить backend-specific bind templates либо отдельные Clojure runtime bindings.

### 2. Java static/instance interop

Обычный `IrBindByName` сейчас формирует вызовы в стиле namespace/function. Для Java API нужны разные формы:

- static method: `(System/currentTimeMillis)`;
- instance method: `(.start process)`;
- constructor: `(ProcessBuilder. args)`;
- готовый Clojure template.

IR или bind metadata должны сохранять эту информацию, а Clojure emitter — генерировать корректный interop.

### 3. Runtime dependencies

Проверить, что Clojure self-hosting project получает все зависимости, нужные `main.niva` и compiler runtime:

- Okio, если он остаётся в runtime;
- Clojure/Babashka support;
- hot-reload host;
- зависимости для JVM и Babashka targets.

### 4. Политика пользовательского `^`

После удаления early return из compiler source нужно явно выбрать поведение для пользовательского Niva-кода:

- разрешать `^` только как trailing return и выдавать диагностику для нелокального варианта;
- либо реализовать отдельное lowering нелокального возврата в Clojure.

Для первого self-hosting этапа достаточно запретить нелокальный `^` в Clojure backend с понятной source-level диагностикой.

## Вторичные блокеры и риски

### `IrSetField`

`back/clojureBackend/cljEmit.niva` пока отвергает `IrSetField`, потому что Clojure records immutable. Нужно выбрать lowering через `assoc`, atom-based mutable fields или явный запрет конструкции.

### Mutable values и closures

Проверить Clojure atom-модель для:

- mutable locals, захваченных lambda;
- mutable collections в `Any`;
- mut receiver;
- state variables и reload;
- сравнений atom-обёрток с обычными значениями.

### Callback-блоки

Проверить семантику блоков в `forEach:`, `map:`, `unpack:`, `whileTrue:`, `ifTrue:` и `ifFalse:`:

- корректный последний результат;
- корректный `Unit` (`:unit`);
- отсутствие нелокальных возвратов;
- корректное захватывание внешних переменных.

### Ошибки Niva и Java exceptions

Niva errors представлены через `ExceptionInfo` с `:value`, а внешние Java exceptions пробрасываются отдельно. Нужно либо оборачивать внешние ошибки в Niva error, либо явно зафиксировать различие в runtime semantics.

### JVM Clojure и Babashka

Проверять отдельно:

- JVM Clojure — основная цель self-hosting;
- Babashka — отдельная compatibility-цель.

Особое внимание уделить class loading, reflection, `requiring-resolve`, generated namespaces, hot reload и native-image preparation.

## Приоритет реализации

1. Устранить early returns из compiler source.
2. Реализовать backend-specific bindings.
3. Исправить Java static/instance interop.
4. Подключить runtime dependencies и проверить `main.niva`.
5. Добавить диагностику нелокального `^` в пользовательском Clojure output.
6. Проверить mutable values, callbacks и error propagation.
7. Отдельно проверить Babashka.

## Критерии готовности

- Clojure output для `main.niva` не содержит Kotlin syntax.
- Вызовы `System`, `ProcessBuilder`, `Process` и `FileSystem` генерируются валидным Clojure interop.
- Self-hosted compiler может читать исходники, генерировать output и запускать subprocess.
- JVM Clojure self-hosting pipeline проходит end-to-end.
- Нелокальный пользовательский `^` либо корректно lowerится, либо получает диагностируемую ошибку.
- `IrSetField` и mutable/closure semantics покрыты тестами или явно запрещены.
