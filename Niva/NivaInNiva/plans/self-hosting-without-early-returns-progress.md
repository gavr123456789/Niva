# Прогресс self-hosting без нелокальных `^`

Последнее обновление: 2026-08-19.

Основной план: `plans/self-hosting-without-early-returns.md`.
Инвентарь: `plans/self-hosting-without-early-returns-inventory.md`.

## Уже сделано

### Инвентарь

- Разделены финальные `^`, guard-return, возвраты из callback/циклов и
  конструкции, которые нельзя менять.
- Зафиксированы исключения: пользовательский `^`, `ReturnStatement`, union-
  конструкторы, строки с пользовательским Niva-кодом, embedded Clojure/Kotlin
  и type hints.

### Финальные выражения

Убраны финальные `^` в self-hosting-исходниках:

- `compiler/compiler.niva`;
- `compiler/incremental.niva`;
- `argParse/cliArgs.niva`;
- `front/parser/parse.niva`;
- `front/parser/parseAstType.niva`;
- `front/resolver/resolveExpr2.niva`;
- `ir/fromTypedAst.niva`.

Два финальных возврата в `parse.niva` и `resolveExpr2.niva` были убраны после
исправления bootstrap-компилятора:

- `front/parser/parse.niva:716` — `maybePrimary`;
- `front/resolver/resolveExpr2.niva:1067` — `result`.

### Guards и callback

- `WatchQueueState event/buildFinished` переписаны в `match true`.
- CLI-конвертеры runtime/target переписаны в `match true`.
- `CompilerSession commit/commitResult` переписаны в `match true`.
- В `front/parser/parseAstType.niva` убраны ранние возвраты из
  `parseTypeErrors` и `parseGenericType` через промежуточные значения и
  ветвление.
- В `ir/fromTypedAst.niva` `bindEmitTemplate` больше не делает нелокальные
  возвраты.
- `ownerFromDecl unpack: [^ ...]` заменён на `unpack: [...] or: ...`.
- В `front/parser/parse.niva` guards в `tryParseDestructingAssign` переписаны
  через `match true` с сохранением восстановления позиции.
- В `front/resolver/resolveExpr2.niva` переписаны guards в
  `firstNonGetterInCopyUpdate` и `errorListEquals`.
- В `front/parser/parse.niva` callback-return из попытки деструктурирующего
  присваивания заменён на nullable-значение и `match`.
- В `ir/fromTypedAst.niva` возврат из `whileStmt unpack` заменён на разбор
  nullable `IrStmt` через `match`.
- В `front/resolver/resolveExpr2.niva` guard для пустого списка ошибок в
  `checkErrorMatchExhaustiveness` заменён на `match true`.
- В `compiler/incremental.niva` cancellation guards в `compileCandidate` и
  `compileSnapshot` заменены на `match true`.

## Проверки, которые прошли

- `niva build main.niva --backend=clj --target=bb`;
- `niva test parseTest`;
- `niva test compilerTests`;
- `niva test irTests`;
- `git diff --check`.

После продолжения также прошли:

- `niva build main.niva --backend=clj --target=bb` (после каждого кластера);
- `niva test parseTest`;
- `niva test irTests`.

После resolver-кластера также прошли:

- `niva build main.niva --backend=clj --target=bb`;
- `niva test resolverTests`.

После incremental-кластера также прошли:

- `niva build main.niva --backend=clj --target=bb`;
- `niva test compilerTests`.

После callback-кластера также прошли:

- `niva build main.niva --backend=clj --target=bb`;
- `niva test parseTest`;
- `niva test irTests`.

Параллельный запуск `resolverTests` вместе с `irTests` столкнулся с общей
временной Kotlin-директорией генерации (`File already exists`); это требует
повторного последовательного запуска.

`niva test cliArgsTest` ранее завершался с одним несвязанным падением:
тест ожидает default `Jvm`, а текущий исходник `ArgParser` задаёт default
`Bb`. Изменения конвертеров runtime/target это не затрагивают.

## Следующие кандидаты

Остались очевидные возвраты в приоритетных файлах:

- `front/parser/parse.niva:63,98` — guards в `tryParseDestructingAssign`;
- `front/parser/parse.niva:521` — `tryParseDestructingAssign: ... unpack: [^ it]`;
- `front/resolver/resolveExpr2.niva:48` — guard в
  `firstNonGetterInCopyUpdate`;
- `front/resolver/resolveExpr2.niva:179,208` — guards в проверках типов;
- `front/resolver/resolveExpr2.niva:270` — пустой список ошибок;
- `ir/fromTypedAst.niva:61,111` — guards в bind helper-ах;
- `ir/fromTypedAst.niva:307` — `whileStmt unpack: [^ it]`;
- `compiler/incremental.niva:385,469,518` — publish/cancellation guards;
- `argParse/cliArgs.niva:185` — финальный guard invalid input.

Работать по одному кластеру и после каждого запускать bootstrap-сборку.
Callback- и loop-возвраты не заменять механически: для них сначала вынести
результат в локальную переменную или сделать явное ветвление.

## Важные ограничения

- Не менять пользовательские тестовые строки с `^`.
- Не менять `ReturnStatement` в AST/typed AST/IR.
- Не считать Clojure metadata (`^:private`, `^String`, `^Runnable`) возвратами.
- В рабочем дереве есть существующие изменения в документации и generated/
  временных файлах; не откатывать их.
