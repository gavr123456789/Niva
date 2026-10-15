# План: LSP в self-hosted Niva через Go-биндинги

Дата: 2026-10-15
Ветка: `self-host`
Статус: план, развилки помечены отдельно
Связанные документы: `plans/lsp-plan.md` (предыдущая версия, устарела), `plans/go-self-hosting-remaining-blockers.md`

## 0. Идея в одном абзаце

LSP-сервер живёт в самом Niva (`lsp/`), общается по JSON-RPC 2.0 через stdin/stdout,
диагностики берёт из уже готовой инкрементальной сессии компилятора. Go-биндинги дают
только то, чего нет в языке: доступ к stdlib (`os`, `encoding/json`, `unicode/utf16`) и,
если понадобится, к сторонним Go-библиотекам JSON-RPC. Никакого нового генератора и
никакого нового ABI для MVP не нужно.

## 1. Что уже есть (проверено по коду)

| Что | Где | Состояние |
| --- | --- | --- |
| Self-hosting на Go | `.bootstrap/`, `scripts/go-self-hosting-check.sh` | работает, bootstrap-цикл и cross-build пройдены |
| Манифест биндингов | `niva.bindings`, формат `backend|moduleId|path` | работает, выбор провайдера до парсинга |
| Go-форма биндинга | `Bind goPkg: "..." emit: "..."` и `Bind goPkg: "..." goName: "..."` | `libs/binds/okio-go.niva`, `hash-go.niva`, `simgleTime-go.niva` |
| Kotlin-форма биндинга | `Bind package: ... imports: {...} content: [...]` с `@emit:` | `libs/binds/okio-kotlin.niva`, `process-kotlin.niva`; парсит только Kotlin bootstrap |
| Генерённый Go-проект | `compiler/compiler.niva` `initializeGoProjectAt` | ровно `go.mod` + `core/core.go`; `main.go` пишется отдельно |
| go.mod | `compiler/goRuntime.niva` `GoRuntimeSources moduleSource` | только `module niva_go` и `go 1.26.1`, без `require` |
| Сборка | `compiler.niva` | `go build -o niva_go .` в каталоге проекта |
| Инкрементальная компиляция | `compiler/incremental.niva` | `CompilerSession.compileSnapshot`, `WorkspaceSnapshot`, `documentVersions`, `cancelled` |
| Диагностики | `compiler/incremental.niva` | `CompilerDiagnostic` = только `requestVersion`, `path`, `message`, без позиций |
| Позиции ошибок | `front/resolver/astVisitor.niva` | `ResolveErrors` union с общими полями `tok: Token` и `msg`; `Token` знает `file path`, `line`, `start`, `end` |
| JSON | `libs/JSON_parser` | `String toJson`, `JsonValue toJsonStr`, `JObject`/`JArray`, `atKey:`, `atIndex:`, `getStr`, `getNum`, `getBool` |
| CLI | `argParse/cliArgs.niva` | команды `run|build|watch|ir|info`; `CompilerBackend.Lsp` есть, но ведёт в `TO DO: "no lsp binary"` |
| Прототип LSP | ветка `nin-lsp`, коммит `be7a9587` | `lsp/{transport,protocol,server,lspTest}.niva` + `libs/binds/lsp.bind.niva`; синтаксис биндингов устарел |

Важные факты, которые определяют план:

1. `echo` в Go-runtime это `fmt.Println`, то есть **stdout**. В LSP-режиме в stdout можно писать только
   протокольные фреймы, значит все логи обязаны идти в stderr, а `echo` и `debug` в коде LSP запрещены.
2. `TO DO:` бросает ошибку. В обработчике сообщений редактора это недопустимо: любой сбой разбора
   должен превращаться в диагностику или в JSON-RPC `error`, а не в падение процесса.
3. `NivaBuildSystem findAllFilesFrom` печатает предупреждение через `echo` (пропущенный биндинг без
   провайдера). В LSP-пути этот вывод надо убрать или увести в stderr.
4. Kotlin bootstrap собирает те же исходники NIN и читает тот же `niva.bindings`, но по строкам
   `kotlin|...`. Поэтому у любого нового Go-биндинга обязан быть kotlin-двойник, иначе `niva test`
   и bootstrap-цикл сломаются.
5. Niva-тесты (`Test name = [...]`) запускаются Kotlin bootstrap-ом через Gradle, а не self-hosted
   Go-бинарником. Значит логику транспорта и протокола надо держать отдельно от stdio, чтобы её
   можно было тестировать юнит-тестами.

## 2. Развилки и решения

План написан под рекомендуемые варианты, альтернатива указана рядом. Развилку можно переиграть
без переписывания всего плана.

### Развилка 1: слой JSON-RPC

- **Рекомендуется: только stdlib.** Фрейминг `Content-Length` руками в Niva, JSON через
  `libs/JSON_parser`, биндинги только на `os.Stdin`/`os.Stdout`. Ноль сторонних зависимостей,
  `go.mod` остаётся stdlib-only, генератор не трогаем, офлайн-сборка сохраняется.
- Альтернатива: биндить `go.lsp.dev/jsonrpc2` + `go.lsp.dev/protocol` или
  `github.com/sourcegraph/jsonrpc2`. Даёт готовый транспорт и dispatch, но требует сторонних модулей
  (см. раздел 7) и не даёт типов LSP в языке, потому что Go-структуры нельзя назвать в сигнатурах
  Niva. Цена высокая, выигрыш для MVP маленький.

### Развилка 2: цикл сервера и конкурентность

- **Рекомендуется: однопоточный цикл в Niva.** Блокирующее чтение фрейма, обработка, ответ.
  Устаревшие запросы компиляции отменяются через `requestVersion` и флаг `cancelled`, который
  уже поддержан в `compileSnapshot`. Никаких изменений в генераторе, поведение предсказуемо.
- Альтернатива: Go-хелпер с горутинами, каналами и дебаунсом. Требует механизма дополнительных
  Go-файлов в генерённом проекте (сейчас пишутся только `main.go` и `core/core.go`) и обратного
  вызова из Go в сгенерированные функции Niva. Это отдельный проект, а не шаг MVP.

### Развилка 3: объём первого милстоуна

- **Рекомендуется: диагностики.** `initialize`/`initialized`/`shutdown`/`exit`, синхронизация
  документов, инкрементальный снимок, `publishDiagnostics` с точными позициями. Это то, ради чего
  LSP вообще нужен, и это опирается на уже готовую инкрементальную машину.
- Альтернатива: сразу добавить `hover` и `textDocument/definition` (фаза 4 ниже).
- Альтернатива: полный паритет с Kotlin `languageServer/LS.kt`, включая completion. Отдельная
  большая работа, в этот план не входит.

## 3. Целевая архитектура

```
argParse/cliArgs.niva          команда lsp и CompilerOption.LspServer
compiler/compiler.niva         маршрутизация команды lsp в LspServer run
compiler/incremental.niva      позиции в CompilerDiagnostic

lsp/
  stdio.niva                   объявления доступа к stdin/stdout и выходу
  transport.niva               Content-Length фреймы, разбор JSON-RPC сообщения
  protocol.niva                DTO протокола и сборка JSON-RPC ответов
  state.niva                   открытые документы, версии, сборка WorkspaceSnapshot
  diagnostics.niva             ResolveErrors -> LSP Diagnostic
  server.niva                  диспетчер методов и жизненный цикл
  lspTest.niva                 юнит-тесты без stdio

libs/binds/lsp-go.niva         провайдер lsp.stdio для backend go
libs/binds/lsp-kotlin.niva     провайдер lsp.stdio для backend kotlin
niva.bindings                  две новые строки провайдеров
```

Слои разделены так, чтобы stdio трогался только в `server.niva run`:

- `transport.niva` умеет `formatFrame` и `parseMessage` и ничего не знает про stdin.
- `state.niva` и `diagnostics.niva` чистые, работают со структурами.
- `server.niva handleMessage` принимает уже разобранное сообщение, поэтому тестируется без процесса.

## 4. Слой биндингов

### 4.1 Объявления (`lsp/stdio.niva`, общий файл)

```
type LspStdio
constructor LspStdio readFrame -> String
constructor LspStdio writeFrame::String -> Unit
constructor LspStdio exitWith::Int -> Unit
String lspUtf8Length -> Int
String lspUtf16Length -> Int
String lspValidJson -> Bool
String lspLogStderr -> Unit
```

### 4.2 Провайдер для Go (`libs/binds/lsp-go.niva`, строка `go|lsp.stdio|libs/binds/lsp-go.niva`)

Форма `emit` с несколькими пакетами через запятую, как в `okio-go.niva`. Читаем заголовок
побайтово, чтобы не терять байты в скрытом буфере между фреймами, тело читаем циклом до
`Content-Length`.

```
constructor LspStdio readFrame -> String =
  Bind goPkg: "os,strings,strconv" emit: """(func() string { ... })()"""

constructor LspStdio writeFrame::String -> Unit =
  Bind goPkg: "os" emit: "(func() core.Unit { if _, err := os.Stdout.Write([]byte($0)); err != nil { panic(err) }; return core.UnitInstance })()"

constructor LspStdio exitWith::Int -> Unit =
  Bind goPkg: "os" emit: "(func() core.Unit { os.Exit($0); return core.UnitInstance })()"

String lspUtf8Length -> Int =
  Bind goPkg: "unicode/utf8" emit: "utf8.RuneCountInString($0)"

String lspUtf16Length -> Int =
  Bind goPkg: "unicode/utf16" emit: "len(utf16.Encode([]rune($0)))"

String lspValidJson -> Bool =
  Bind goPkg: "encoding/json" emit: "json.Valid([]byte($0))"

String lspLogStderr -> Unit =
  Bind goPkg: "fmt,os" emit: "(func() core.Unit { fmt.Fprintln(os.Stderr, $0); return core.UnitInstance })()"
```

Замечания по форме:

- `emit` подставляет `$0` как receiver и `$1..$n` как аргументы, шаблон обязан возвращать значение
  ABI: `core.UnitInstance` для `Unit`.
- `goName` подходит только когда сигнатура Go-функции совпадает с Niva-типами и пакет один;
  `json.Valid` требует `[]byte`, `os.Stdout.Write` требует обёртки, поэтому здесь везде `emit`.
- Квалификатор пакета это последний сегмент пути: `encoding/json` даёт `json`.

### 4.3 Провайдер для Kotlin (`libs/binds/lsp-kotlin.niva`, строка `kotlin|lsp.stdio|libs/binds/lsp-kotlin.niva`)

Обязателен, иначе Kotlin bootstrap не соберёт NIN. Форма блочная, как в `process-kotlin.niva`:

```
Bind package: "java.lang,java.io" content: [
  @emit: "..."
  constructor LspStdio readFrame -> String

  @emit: "..."
  constructor LspStdio writeFrame::String -> Unit

  @emit: "System.exit($1)"
  constructor LspStdio exitWith::Int -> Unit

  ...
]
```

Kotlin-версия нужна только для тестов и bootstrap, читать побайтово из `System.in` достаточно.

## 5. Фазы

### Фаза 0. Подготовка

- [ ] Пересобрать NIN и убедиться, что `scripts/go-self-hosting-check.sh` зелёный.
- [ ] Проверить, что текущий `main.jar` (Kotlin bootstrap) собирает дерево с новыми файлами.

### Фаза 1. Транспорт, JSON-RPC, жизненный цикл

Порт прототипа `be7a9587` на текущий синтаксис биндингов.

- [ ] `lsp/stdio.niva` и два провайдера, строки в `niva.bindings`.
- [ ] `lsp/transport.niva`: `formatFrame`, `readFrame`, `parseMessage`.
- [ ] `lsp/protocol.niva`: `JsonRpcResponse success`, `JsonRpcResponse error`,
      `JsonRpcNotification`, `LspCapabilities`, `LspPosition`, `LspRange`, `LspDiagnosticItem`.
- [ ] `lsp/server.niva`: `initialize`, `initialized`, `shutdown`, `exit`,
      `Method not found (-32601)`, `Parse error (-32700)`, цикл `run`.
- [ ] `argParse/cliArgs.niva`: команда `lsp`, `CompilerOption.LspServer`, для неё backend по
      умолчанию `Go`, строка в `--help`.
- [ ] `compiler/compiler.niva`: маршрут `LspServer` в `LspServer run`; `CompilerBackend.Lsp`
      отображать в `"go"` вместо `TO DO: "no lsp binary"`.
- [ ] `lsp/lspTest.niva`: фрейминг, разбор `initialize`, ответ с `capabilities`, `shutdown`/`exit`,
      неизвестный метод.
- [ ] `argParse/cliArgsTest.niva`: `niva lsp` даёт `CompilerOption.LspServer` и backend `Go`.

Выход фазы: редактор проходит рукопожатие, сервер отвечает и корректно завершается.

### Фаза 2. Документы, снимок, диагностики

- [ ] `lsp/state.niva`: таблица открытых документов (uri, путь, версия, содержимое),
      `file://` uri в локальный путь, версии документов для `documentVersions`.
- [ ] Обработчики `textDocument/didOpen|didChange|didClose|didSave`, синхронизация `Full`.
- [ ] Сборка `WorkspaceSnapshot` из файлов рабочей области плюс оверлеи открытых документов;
      `entryPoint` это `main` корневого `main.niva`.
- [ ] `CompilerConfigFingerprint` с `backend: "go"`, `irSchema: "nir/v0"`, `hotReload: false`.
- [ ] Один `CompilerSession` на рабочую область, вызов `compileSnapshot` с растущим
      `requestVersion` и флагом `cancelled` для устаревших версий.
- [ ] Расширить `CompilerDiagnostic` в `compiler/incremental.niva` позициями:
      `line`, `start`, `end`, плюс удобный конструктор из `ResolveErrors` через `tok`.
      Обновить вызовы в `compiler.niva` и ожидания в `compiler/compilerTests.niva`.
- [ ] `lsp/diagnostics.niva`: `ResolveErrors` в `LspDiagnosticItem`, перевод 1-based `line` в
      0-based, `severity: 1`, `source: "niva"`, пустой список для очистки маркеров.
- [ ] Отправка `textDocument/publishDiagnostics` после каждого изменения документа.

### Фаза 3. Устойчивость

- [ ] Никаких `TO DO:` в пути обработки сообщений: оборачивать в `handling` и отвечать
      JSON-RPC `error` или диагностикой.
- [ ] Убрать `echo` из путей, которые проходит LSP: предупреждение в `NivaBuildSystem` и
      отладочные печати увести в `lspLogStderr`.
- [ ] Ограничение по размеру входящего фрейма и корректное завершение на закрытом stdin.
- [ ] `$/cancelRequest` как минимум не ломает цикл.
- [ ] Логи в stderr за флагом `--verbose`.

### Фаза 4. Навигация (после MVP, по желанию)

- [ ] `textDocument/hover`: поиск токена по позиции, тип из `TyperDB`.
- [ ] `textDocument/definition`: `typeDeclarationTokens` и `methodDeclarationTokens` из
      `front/resolver/typeDB.niva` уже хранят токены объявлений, этого достаточно для перехода.
- [ ] Расширение `ServerCapabilities` под новые провайдеры.

## 6. Позиции диагностик и UTF-16

LSP считает `character` в UTF-16 код-юнитах, а `Token.start`/`Token.end` в Niva это смещения по
символам строки. Для ASCII и BMP расхождений нет, для эмодзи и астральных символов есть.
Для MVP берём прямое смещение и фиксируем ограничение, а `lspUtf16Length` из раздела 4.1 держим
готовым, чтобы позже пересчитывать колонку по префиксу строки.

Диагностики вида `hard restart required` приходят без токена: для них ставим диапазон
`0:0 .. 0:0` и оставляем `path` в `message` либо в `relatedInformation`.

## 7. Альтернатива: сторонние Go-библиотеки

Если развилка 1 будет переиграна в пользу готового JSON-RPC, понадобится:

1. Модификация `GoRuntimeSources moduleSource`, чтобы генерировать `require` для модулей.
2. Доставка зависимостей: либо `vendor/` в репозитории (офлайн, большой дифф), либо
   `go mod download` при сборке (нужна сеть, нужен `go.sum` в репозитории, `GOFLAGS=-mod=mod`).
3. Мост типов: `protocol.Diagnostic` и подобные Go-структуры нельзя назвать в сигнатурах Niva.
   Остаётся держать их непрозрачным значением и писать `emit`-аксессоры на каждое поле, либо
   оставить модель LSP на стороне Niva и брать из библиотеки только транспорт.
4. Для `sourcegraph/jsonrpc2` и `go.lsp.dev/jsonrpc2` обработчик это функция с контекстом и
   конкурентностью: чтобы позвать из неё код Niva, нужны сгенерированные функции и замыкания,
   а это уже переход к развилке 2 с Go-хелпером.

Вывод: сторонние библиотеки дают выигрыш только если согласиться на Go-хелпер. При однопоточном
цикле в Niva они не окупаются.

## 8. Риски и ограничения

| Риск | Как снижаем |
| --- | --- |
| Любая печать в stdout ломает протокол | запрет `echo`/`debug` в `lsp/`, логи только через `lspLogStderr` |
| `TO DO:` падает на некорректном вводе | `handling` вокруг разбора и диспетчера |
| Долгая компиляция блокирует цикл | отмена устаревших версий через `requestVersion`/`cancelled`, дебаунс на стороне клиента |
| Kotlin bootstrap ломается из-за Go-only биндинга | обязательный kotlin-провайдер `lsp.stdio` |
| Разбор с ошибкой не даёт дерева | snapshot-based подход: публикуем позиции ошибок, навигация только на валидном снимке |
| Компиляция на каждое нажатие дорогая | переиспользование `CompilerSession` и кэша неизменённых модулей |
| `main.go` это один файл | для MVP не мешает, размер файла не влияет на работу сервера |

## 9. Критерии готовности MVP

1. `niva lsp` запускает сервер, `initialize` возвращает `capabilities` с `textDocumentSync`,
   `hoverProvider`, `definitionProvider`.
2. Ошибки в открытом `.niva` файле появляются в редакторе с точной строкой и колонкой.
3. После исправления ошибок маркеры исчезают, пустой список уходит в `publishDiagnostics`.
4. Правка одного файла не перекомпилирует неизменённые модули.
5. `niva lsp` не пишет в stdout ничего кроме фреймов.
6. `niva test` и `scripts/go-self-hosting-check.sh` остаются зелёными.

## 10. Проверки

- Юнит-тесты `lsp/lspTest.niva` через Kotlin bootstrap: фрейминг, разбор, диспетчер, маппинг
  диагностик. Никакого stdio.
- Процессный smoke-тест `scripts/lsp-smoke.sh`: собрать NIN, запустить `niva lsp`, подать на stdin
  фреймы `initialize`, `initialized`, `didOpen` с заведомо ошибочным файлом, `shutdown`, `exit`,
  проверить в stdout наличие `capabilities` и `publishDiagnostics` с ожидаемой строкой, проверить
  код выхода `0`.
- Ручная проверка в редакторе: открыть проект NIN, поправить файл, убедиться, что маркеры
  обновляются и процесс не падает на битом JSON и на пустом вводе.

## 11. Порядок работ

1. Фаза 0 и 1 целиком, включая kotlin-провайдер и тесты. Это уже полезный результат.
2. Расширение `CompilerDiagnostic` позициями и фаза 2. Самый содержательный шаг.
3. Фаза 3. Без неё сервер не годится для повседневной работы.
4. Фаза 4 по остаточному принципу.

## 12. Что меняется в существующих файлах

| Файл | Изменение |
| --- | --- |
| `argParse/cliArgs.niva` | команда `lsp`, `CompilerOption.LspServer`, backend по умолчанию `Go`, строка в `--help` |
| `argParse/cliArgsTest.niva` | тест на команду `lsp` |
| `compiler/compiler.niva` | маршрут `LspServer` в `LspServer run`, `CompilerBackend.Lsp` в `"go"`, тихий режим для `echo` |
| `compiler/incremental.niva` | позиции в `CompilerDiagnostic`, конструктор из `ResolveErrors` |
| `compiler/compilerTests.niva` | обновить ожидания по `CompilerDiagnostic` |
| `niva.bindings` | строки `go|lsp.stdio|libs/binds/lsp-go.niva` и `kotlin|lsp.stdio|libs/binds/lsp-kotlin.niva` |
| `plans/lsp-plan.md` | пометить как устаревший, ссылаться на этот файл |

Новые файлы: `lsp/stdio.niva`, `lsp/transport.niva`, `lsp/protocol.niva`, `lsp/state.niva`,
`lsp/diagnostics.niva`, `lsp/server.niva`, `lsp/lspTest.niva`, `libs/binds/lsp-go.niva`,
`libs/binds/lsp-kotlin.niva`, `scripts/lsp-smoke.sh`.
