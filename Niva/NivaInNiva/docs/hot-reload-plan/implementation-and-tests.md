# Порядок реализации и проверки

[← Индекс плана](clojure-repl.md)

## стиль комментариев

- комментарии в коде писать простыми фразами
- не использовать точки в комментариях
- не использовать заглавные буквы в комментариях

## Принятые решения по инкрементальной компиляции

- Module-level cache; declaration-level invalidation откладывается до
  профилирования.
- In-memory session для `watch` и LSP.
- Invalidation через `apiHash` с conservative fallback.
- Specialization-use index входит в первую реализацию.
- Единый opt-in `--incremental` для `watch`, LSP и `build`; у одноразового
  `build` кеш между запусками появится после EDN-сериализации.
- Будущий disk cache находится в project-local `.niva-cache/`.
- В bootstrap-реализации SHA-256 для `apiHash` доступен Niva через Kotlin/JVM
  binding; канонизация интерфейса остаётся в compiler layer.
- Incremental backend scope — Clojure; Go пока не делать.

## Порядок реализации

### Этап A. Module graph foundation

1. Исправить module ids и коллизии путей.
2. Нормализовать dependency graph в IR.
3. Добавить cycle diagnostics.

### Этап B. Incremental compiler foundation

1. Ввести immutable snapshots и persistent `CompilerSession`.
2. Разделить interface collection и body resolution.
3. Добавить deterministic `apiHash`, reverse dependency graph и cache keys.
4. Реализовать transactional candidate/commit и conservative fallback.
5. Добавить specialization-use index.
6. Подключить единый opt-in `--incremental` к общей CLI/session boundary;
   concrete watch/LSP adapters передадут его туда при появлении entry points.
7. После EDN-сериализации подключить тот же флаг к `build`.
8. Проверить эквивалентность результатов clean и incremental compilation.

### Этап C. Multi-file Clojure backend без watch

1. Перевести backend с одного output string на map generated-файлов.
2. Добавить qualified cross-package calls/types.
3. Научить `niva build` писать и удалять полный generated file set.
4. Проверить ручной запуск получившегося Clojure-проекта.

### Этап D. Reload-safe emission

1. Вынести entry expressions в `__niva_entry!`.
2. Добавить отдельную top-level state emission.
3. Сохранять top-level `mut` через Atom и `^:clj-reload/keep`.
4. Сохранять record identity и детектировать несовместимое изменение shape.
5. Добавить manifest.

### Этап E. JVM watch host

1. Добавить `watch` в parser CLI.
2. Добавить `--runtime jvm|bb` и `--target jvm|bb`.
3. Реализовать WatchService, debounce и serial build queue.
4. Добавить staging/publish.
5. Подключить и инициализировать `clj-reload`.
6. Initial load вызывает entry один раз; reload entry не вызывает.
7. Добавить корректное завершение процесса по Ctrl-C.

### Этап F. Babashka

1. Прогнать тот же integration suite на актуальной Babashka. В репозитории
   `clj-reload` есть Babashka test configuration, но совместимость конкретного
   Niva output всё равно должна проверяться отдельно.
2. Проверить `Compiler/load`, class identity, keep для records и WatchService.
3. Включить `--runtime bb`, если нет несовместимостей; иначе оставить JVM
   официальной watch runtime.

### Этап G. Возможные оптимизации

- declaration-level invalidation после профилирования module-level cache;
- project-local EDN disk cache и eviction policy после self-hosting;
- SCC coalescing вместо запрета циклов;
- state migrations;
- editor/nREPL command для manual reload и restart.

## Тестовый план

### Resolver и IR

- два файла с одинаковым basename в разных директориях;
- dependency через field type, call, constructor, match и generic
  instantiation;
- отсутствие ложного dependency на текущий package;
- понятный diagnostic для двух- и трёхузлового цикла;
- external bind import не попадает в local cycle graph.

### Инкрементальная компиляция

- изменение только тела функции не типизирует dependants при прежнем API;
- изменение signature пересчитывает всех необходимых transitive dependants;
- добавление/удаление/переименование файла обновляет оба направления графа;
- clean и incremental compilation дают эквивалентные IR, output и diagnostics;
- parser/resolver error не портит last good session;
- устаревшая или отменённая LSP-компиляция не коммитит cache entries;
- смена target/compiler/core ABI корректно инвалидирует нужные слои;
- изменение generic call site обновляет specialization владельца функции;
- несвязанный модуль не парсится, не типизируется и не генерируется повторно;
- запуск без `--incremental` остаётся контрольным путём для differential tests.

### Clojure codegen

- один `.clj` на каждый `IrPackage`;
- корректные paths и namespace names;
- только direct `:require`;
- qualified cross-package functions, records, enums и union branches;
- forward declarations остаются локальными namespace'у;
- entry code находится внутри функции и не выполняется при `require`.

### Reload integration

- изменение leaf namespace перезагружает его dependants в нужном порядке;
- несвязанный namespace не перезагружается;
- удаление Niva-файла удаляет generated namespace;
- compile error не меняет работающий runtime;
- load error не завершает watcher и исправляется следующим reload;
- top-level side effect выполняется при старте и не повторяется на reload;
- значение top-level `mut` и identity Atom сохраняются;
- изменение функции, читающей Atom, применяется к старому состоянию;
- изменение record shape требует hard restart.

### CLI

- `niva watch main.niva` выбирает JVM;
- неизвестный runtime даёт usage error;
- неподдержанный `watch --runtime bb` даёт явное сообщение;
- create/modify/delete events coalesce в один build;
- Ctrl-C корректно останавливает watcher и дочерний runtime.

## Критерии готовности первой версии

Первая версия считается готовой, когда:

1. каждый Niva-файл компилируется в отдельный namespace;
2. все cross-file references квалифицированы и представлены в `:require`;
3. циклы обнаруживаются до генерации и имеют source-level diagnostic;
4. `niva watch main.niva` работает в одном долгоживущем JVM-процессе;
5. изменение файла перезагружает его и transitive dependants через
   `clj-reload`;
6. произвольный top-level entry code не запускается повторно;
7. top-level `mut` сохраняет Atom и значение;
8. compile/load error не завершает watcher;
9. integration tests подтверждают reload order и сохранение состояния.
10. повторная сборка использует module-level cache, а clean и incremental
    pipelines дают семантически одинаковый результат.

## Основные риски

- Не все cross-package dependencies сейчас надёжно представлены в
  `IrPackage.imports`; поэтому dependency normalization является обязательной,
  а не косметической фазой.
- Разделение namespace'ов затрагивает не только вызовы функций, но также class
  references generated records и type-based match.
- Сохранение Atom недостаточно без политики identity для содержащихся в нём
  record values.
- `clj-reload` удаляет namespace целиком; стабильный host не должен удерживать
  прямые ссылки на generated Vars.
- Изменение module graph во время reload сложнее обычного изменения функции;
  manifest и удаление stale generated files обязательны.
- Автоматическое SCC coalescing возможно, но заметно усложняет стабильность
  namespace identity, поэтому не должно блокировать первую реализацию.
