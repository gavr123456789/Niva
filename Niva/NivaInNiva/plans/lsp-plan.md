# План реализации LSP для Niva (Self-hosted Go Backend, Snapshot-based MVP)

> **Устарел (2026-10-15):** актуальный план находится в
> [plans/lsp-go-bindings-plan.md](lsp-go-bindings-plan.md). Он учитывает завершённый
> Go self-hosting, текущий синтаксис биндингов (`Bind goPkg`), манифест
> `niva.bindings` и обязательный kotlin-двойник для провайдеров.

> **Блокер на текущий момент (2026-10-10):** LSP требует завершённого Go
> self-hosting pipeline. Go codegen и выбор backend уже присутствуют, но CLI пока
> пишет только `.niva_go/main.go`, запускает `ixgo`, а Go runtime и bindings для
> NIN не генерируются как полный проект. Актуальная сверка и порядок работ находятся
> в [плане self-hosting через Go](go-self-hosting-remaining-blockers.md). После
> прохождения bootstrap-цикла нужно вернуться к сборке и процессной проверке LSP.

## 1. Цели и технические решения

- **Архитектура (1.A):** Встроенный self-hosted LSP-сервер в Niva (`lsp/` модуль), взаимодействующий с редакторами по протоколу JSON-RPC 2.0 через стандартные потоки ввода-вывода (`stdin`/`stdout`).
- **Среда выполнения и компилятор:** Исключительно **Go backend** (нативный бинарник `niva`), обеспечивающий мгновенный старт, низкое потребление памяти и простое распространение без сторонних рантаймов.
- **Модель компиляции и диагностики (3.A):** Snapshot-based подход на базе существующей инкрементальной системы `WorkspaceSnapshot` и `CompilerSession` (`compiler/incremental.niva`). Обработка полных снимков файлов в памяти без сложного механизма восстановления после синтаксических ошибок (error recovery). При наличии синтаксических или семантических ошибок сервер публикует точные позиции ошибок через `publishDiagnostics`.
- **JSON библиотека:** Использование встроенной библиотеки `libs/JSON_parser` (`JsonValue`, `toJson`, `toJsonStr`, `atKey:`, `atIndex:`, `getStr` и др.) для парсинга входящих JSON-RPC запросов и формирования исходящих сообщений.

---

## 2. Архитектура и структура модулей

```
libs/
└── JSON_parser/        # Библиотека парсинга/сериализации JSON на Niva
    ├── jsonFromStr.niva    # String.toJson -> JsonValue
    ├── jsonToStr.niva      # JsonValue.toJsonStr -> String, union JsonValue (JObject, JArray, ...)
    ├── unsafeGetters.niva  # atKey, atIndex, getStr, getNum, getBool
    └── strUtils.niva       # Экранирование и форматирование строк

lsp/
├── transport.niva      # Чтение/запись JSON-RPC фреймов (Content-Length, stdin/stdout)
├── protocol.niva       # DTO типы протокола LSP и маппинг в/из JsonValue (Position, Range, Diagnostic, ...)
├── state.niva          # Управление состоянием LSP (WorkspaceSnapshot, открытые документы, версии)
├── server.niva         # Главный диспетчер сообщений и жизненный цикл (initialize, shutdown, textDocument/*)
└── diagnostics.niva    # Маппинг CompilerDiagnostic -> LSP Diagnostic -> JsonValue
```

---

## 3. Поэтапный план реализации

### Фаза 1: Инфраструктура JSON-RPC 2.0 и CLI-команда

1. **CLI интеграция:**
   - [x] Добавить команду `lsp` в `argParse/cliArgs.niva` (поддержка `niva lsp [OPTIONS]`).
   - [x] Зафиксировать бекенд компилятора по умолчанию для режима LSP (`CompilerBackend.Go`).
   - [x] Настроить маршрутизацию команды в `compiler/compiler.niva` для запуска цикла LSP-сервера.

2. **Транспортный уровень (`lsp/transport.niva`):**
   - [x] Реализовать чтение заголовков из `stdin` до разделителя `\r\n\r\n`.
   - [x] Извлекать значение заголовка `Content-Length: <N>`.
   - [x] Читать ровно `N` UTF-8 байт полезной нагрузки JSON из `stdin`.
   - [x] Парсить payload через `payloadText toJson` в `JsonValue`.
   - [x] Обрабатывать ошибки JSON-парсинга с ответом JSON-RPC `-32700`.
   - [x] Записывать исходящие сообщения в `stdout` с `Content-Length` и прямой записью без буферизации.

3. **Базовый жизненный цикл LSP:**
   - [x] Обработка запроса `initialize`:
     - [ ] Сохранение параметров `params` (`rootUri`, `capabilities`);
     - Возврат `InitializeResult` с описанием `ServerCapabilities`:
       - `textDocumentSync: { openClose: true, change: TextDocumentSyncKind.Full, save: true }`
       - `hoverProvider: true`
       - `definitionProvider: true`
   - [x] Обработка уведомления `initialized`.
   - [x] Обработка запроса `shutdown` и уведомления `exit` с завершением процесса с кодом 0/1.

   **Примечание:** структура `initialize.params` пока принимается без сохранения `rootUri` и `capabilities`; это нужно завершить при реализации workspace state.

---

### Фаза 2: Синхронизация документов в памяти и интеграция со Snapshot

1. **Хранилище открытых документов (`lsp/state.niva`):**
   - Поддержка таблицы активных документов редактора: `Map(String, DocumentState)` (URI / абсолютный путь, версия, контент).
   - Нормализация путей файлов из `file://` URI в локальные пути файловой системы.

2. **Обработчики событий синхронизации (десериализация через `libs/JSON_parser`):**
   - `textDocument/didOpen`: извлечение `uri`, `text`, `version` через `params atKey: "textDocument"` и регистрация документа.
   - `textDocument/didChange`: извлечение нового полного содержимого документа (`TextDocumentSyncKind.Full`).
   - `textDocument/didClose`: удаление из списка активных оверлеев памяти.
   - `textDocument/didSave`: синхронизация состояния сохранения.

3. **Формирование `WorkspaceSnapshot`:**
   - Построение списка `NivaSourceInput` из открытых буферов в памяти + файлов проекта на диске.
   - Передача актуального снимка в сессию инкрементальной компиляции (`CompilerSession`).

---

### Фаза 3: Инкрементальные диагностики (`publishDiagnostics`)

1. **Конвейер валидации и компиляции:**
   - При получении `didOpen`, `didChange` или `didSave` инициировать инкрементальную проверку через `CompilerSession.compileCandidate`.
   - Использование `ResolverInterfaceSnapshot` и сбор ошибок из парсера/`TyperDB` (`bodyResolver.db.listOfErrors`).

2. **Трансляция диагностик (`lsp/diagnostics.niva`):**
   - Преобразование токенов ошибок (`Token` с 1-based `line`, `start`, `end`) в 0-based координаты LSP:
     - `start: { line: token.line - 1, character: token.start }`
     - `end: { line: token.line - 1, character: token.end }`
   - Формирование объекта `Diagnostic` и сериализация в `JsonValue` (`JObject`):
     - `range`: `{ start: { line, character }, end: { line, character } }`
     - `severity`: `1` (Error)
     - `source`: `"niva"`
     - `message`: текст ошибки компилятора (`error.toConsoleMsg` или форматированное сообщение)
   - Очистка диагностик: отправка `diagnostics: []`, если файл больше не содержит ошибок.

3. **Отправка уведомления `textDocument/publishDiagnostics`:**
   - Формирование JSON-RPC уведомления (`method: "textDocument/publishDiagnostics"`) и отправка через транспорт.

---

### Фаза 4: Расширение возможностей навигации (на базе валидного AST/TyperDB)

*Поскольку recovery не используется, функционал навигации и типизации активируется, когда текущий снимок успешно резолвится без фатальных синтаксических ошибок.*

1. **`textDocument/hover`:**
   - Извлечение позиции курсора `(line, character)` из параметров запроса.
   - Поиск AST-узла и соответствующего токена по позиции.
   - Извлечение типа выражения из `TyperDB`.
   - Форматирование ответа `Hover` с Markdown-контентом (`contents: { kind: "markdown", value: "..." }`).

2. **`textDocument/definition` (Переход к определению):**
   - Определение целевого символа под курсором (локальная переменная, тип, union branch, метод).
   - Возврат ответа `Location` (`uri` и `range` определения в исходном коде).

---

## 4. Критерии готовности MVP

1. Бинарник `niva`, скомпилированный через Go backend, поддерживает запуск `niva lsp`.
2. LSP-сервер успешно проходит рукопожатие `initialize`/`initialized` с редакторами по JSON-RPC 2.0 с использованием `libs/JSON_parser`.
3. При редактировании `.niva` файлов ошибки синтаксиса и типизации мгновенно подсвечиваются в редакторе через `publishDiagnostics`.
4. При исправлении ошибок маркеры ошибок своевременно исчезают.
5. Инкрементальная сессия корректно переиспользует кэш неизмененных модулей между правками.

---

## 5. Текущий прогресс

**Дата обновления:** 2026-09-25

### Выполнено

- Добавлена CLI-команда `niva lsp` с backend по умолчанию `CompilerBackend.Go`.
- Подключён `lsp/` модуль с протокольными типами, транспортом, lifecycle-диспетчером и тестами handshake.
- Добавлен Go stdio binding в `libs/binds/lsp.bind.niva`.
- Реализованы JSON-RPC фреймы, чтение `Content-Length`, чтение полного payload, UTF-8 длина ответа и запись в stdout.
- Добавлен ответ `Parse Error (-32700)` для некорректного JSON.
- Реализованы `initialize`, `initialized`, `shutdown`, `exit` и ошибка `Method not found (-32601)`.
- Исправлено включение `libs/` в source graph при запуске self-hosted компилятора из корня проекта.
- Добавлен CLI-тест проверки LSP-команды и Go backend по умолчанию.

### В работе

- Фаза 1 частично завершена: `initialize.params` ещё не сохраняет `rootUri` и `capabilities`.
- Фазы 2–4 не реализованы: нет document state, `WorkspaceSnapshot`, `publishDiagnostics`, hover и definition.
- Нужно пересобрать актуальный self-hosted Go-бинарник и выполнить процессную проверку handshake через реальные stdin/stdout фреймы.

### Статус MVP

MVP пока не считается готовым. Сейчас завершена базовая инфраструктура Фазы 1, но диагностики, синхронизация документов и инкрементальная компиляция ещё отсутствуют.
