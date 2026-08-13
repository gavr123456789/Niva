# Hot reload implementation progress

## Текущий этап

Фазы 9–10: JVM `clj-reload` host и `watch` lifecycle.

Статус: JVM и Babashka lifecycle реализованы в self-hosted Clojure compiler
path; bootstrap launcher/process-level integration остаётся отдельным этапом.

### Завершены фазы 9–10: JVM/BB host и watch

- Добавлен стабильный Clojure namespace `niva.hot-reload.host`, который
  публикуется рядом с `.niva_clj/generated` и получает dependency
  `io.github.tonsky/clj-reload` через `deps.edn`.
- `reload/init` вызывается один раз с generated directory и
  `:no-reload '#{niva.hot-reload.host}`.
- Initial load использует `reload/reload {:only :all}`, затем динамически
  находит `__niva_entry!` через `find-ns`/`ns-resolve` и выполняет его только
  один раз. Обычный `reload {:only :changed}` entry hook не вызывает.
- После compile diagnostics публикация и reload не выполняются. После
  успешной publication вызывается changed reload; initial/load errors
  печатаются и не завершают watcher, а следующая успешная generation может
  восстановить runtime.
- Watcher рекурсивно регистрирует Java `WatchService`, фильтрует `.niva`,
  debounce'ит события окном 150 ms и выполняет callback последовательно;
  события, пришедшие во время build, остаются в очереди до следующего build.
  Shutdown закрывает service и снимает shutdown hook.
- CLI parser получил `watch`, `--runtime jvm|bb` и `--target jvm|bb`.
  `watch` по умолчанию выбирает JVM, а BB runtime использует BB-compatible
  generated target.
- Watch adapter использует `WorkspaceSnapshot`, `CompilerSession`,
  `CompilerCompilationResult`, `CljGenerationResult`, staging/publish и
  manifest API; Go backend не менялся.
- Добавлены тесты parser, debounce/serial queue invariants, host lifecycle
  markers и публикации host/deps. Existing state/manifest tests покрывают
  сохранение top-level `mut` и reload-safe declarations.
- `CljTarget` теперь реально выбирает JVM или Babashka source emission:
  Babashka output не добавляет `:gen-class`, а normal Clojure `run` выбирает
  процесс через `--runtime jvm|bb`.
- Для published generations добавлен `bb.edn` рядом с `deps.edn`; BB target
  запускается как loadable source и получает `clj-reload` dependency через
  свой native config format.

### Babashka compatibility

Babashka source и `clj-reload` lifecycle совместимы и проверены
отдельным long-lived BB harness:

- initial load и explicit entry execution проходят;
- changed reload применяет новую функцию без повторного entry;
- `^:clj-reload/keep` Atom сохраняет state;
- broken generation возвращает load error, следующая исправленная generation
  восстанавливается.

Babashka WatchService несовместим с `java.nio.file.StandardWatchEventKinds`,
но для BB выбран штатный pod `org.babashka/fswatcher` `0.0.7`. Host динамически
загружает `babashka.pods`, следит за recursive `.niva` events с debounce
200 ms и сериализует callback через lock. `watch --runtime bb` теперь
поддержан; `stop-watch!` завершает promise-based BB watcher в тестах и даёт
контролируемый lifecycle.

### Завершена фаза 6: declarations отдельно от entry execution

- Program Clojure output теперь определяет `__niva_entry!` как единственный
  hook для произвольных top-level expressions.
- `-main` оставлен тонким CLI/native compatibility wrapper'ом и делегирует в
  `__niva_entry!`.
- Загрузка generated namespace через `require` не выполняет entry code; это
  проверено runtime regression test.
- REPL mode сохранён отдельным load/evaluate control path.

### Начата фаза 7: namespace-level `mut` state

- Добавлен `IrStateDef`; top-level `IrLet(isMut: true)` поднимается из entry
  block и больше не выполняется внутри hook как локальный initializer.
- `IrPackage` хранит state definitions; они входят в interface hash и
  dependency evidence.
- Clojure Program backend испускает `^:clj-reload/keep` state Var с private
  object sentinel; initializer один раз вычисляется внутри entry hook.
- Reads/writes state внутри entry используют `@name` и `reset!`; local mut
  остаётся обычным локальным Atom.
- Добавлен runtime test: `require` не запускает entry, явный вызов hook читает
  сохранённый Atom.
- Generated records и union branch records помечаются
  `^:clj-reload/keep`, чтобы method-only reload сохранял class identity.
- Для `IrTypeDef`, `IrUnionDef` и `IrEnumDef` определены deterministic shapes:
  порядок полей/branches/enum values и ABI-relevant type references входят в
  shape.
- `CljManifest` детерминированно содержит entry namespace, modules с direct
  deps, state Vars и type shapes; `manifest.edn` пишется рядом с generated
  output, а существующие `CljOutput.files` и `main.clj` shim сохранены.
- При изменении record/union/enum shape или типа уже сохранённого state Var
  compiler result получает diagnostic с текстом `hard restart required`, не
  коммитит candidate и явно сообщает, что автоматическая миграция state не
  поддерживается.

### Продолжена фаза 8: staging и публикация Clojure output

- Generated Clojure files сначала пишутся в
  `.niva_clj/staging/<generation-id>/generated`.
- Полностью записанный generated tree публикуется через directory swap;
  stale namespace'ы исчезают только при успешной публикации нового набора.
- `main.clj` остаётся legacy shim, а `manifest.edn` и `deps.edn` публикуются
  из той же staging generation после codegen.
- Добавлен `CljGenerationResult` с generation id и published status.
  `CompilerCompilationResult.publishCljOutput` возвращает непубликованный
  result при diagnostics или cancellation, поэтому старый output и manifest
  сохраняются.
- Runtime classpath теперь включает `.niva_clj/generated`, не меняя
  расположение legacy shim.

### Реализовано в фазе 4

- Добавлены immutable-by-construction `WorkspaceSnapshot` и persistent
  `CompilerSession`: подготовка candidate не меняет committed state.
- `CompilerCandidate` хранит base revision; stale/cancelled result нельзя
  закоммитить поверх более новой сессии.
- `CompilerConfigFingerprint` разделяет resolution и backend cache keys:
  compiler/IR/core ABI инвалидируют type resolution, а backend/target/ABI
  options — только output layer.
- Public interface каждого `IrPackage` получает детерминированное canonical
  representation и SHA-256 `apiHash`. Тела и source positions в hash не входят.
- Построены direct и reverse module graphs; planner различает source-,
  interface-, body- и output-invalidation, включая transitive dependants и
  удаление модулей.
- Неинвалидированные `IncrementalModuleState` переиспользуются при создании
  следующего candidate.
- Сбор generic instantiations дополнен индексом
  `owner|functionId|concreteTypeKey -> contributing modules`; в committed
  session попадают только реальные generic functions.
- Добавлен JVM binding SHA-256 без внешней зависимости.
- `ResolverInterfaceSnapshot.collectSources` теперь отдельно выполняет parse и
  declaration/interface collection; `resolveBodies` является явным следующим
  проходом. Совместимый `ResolverHelper.resolveSources` делегирует этим двум
  стадиям.
- Declaration pass замораживает lookup-таблицы в `ResolverLookupEnvironment`:
  snapshot больше не публикует и не хранит mutable `TyperDB`.
- Каждый вызов `resolveBodies` материализует отдельный working `TyperDB` с
  собственными nested map/set, body/generic caches, package imports и bind
  metadata. Один interface snapshot можно безопасно переиспользовать для
  нескольких candidates.
- AST collections в `ResolverInterfaceSnapshot` и индекс message declarations
  публикуются как read-only `Map/List`; generated declarations добавляются
  только в локальную рабочую копию body pass.
- Interface canonical representation и `apiHash` теперь вычисляются из
  declaration snapshot до body resolution; body pass принимает explicit set
  модулей и не типизирует неизменённые dependants.
- `CompilerSession` умеет собирать candidate из interface snapshot с reuse
  typed/IR states вне invalidation frontier.
- Добавлен versioned `CompilerCompilationResult`: diagnostics содержат
  request version и source path, failed/cancelled candidate не содержит commit
  entry.
- Candidate теперь публикует собранный `IrModule`: unchanged packages берутся
  из module cache, а entry block сохраняется из last-good session, если entry
  module не входит в body frontier.
- Добавлен `CompilerBackendArtifact` для Clojure; backend получает IR из
  incremental candidate, а не из отдельного clean resolver path. Go backend
  пока не входит в scope и не реализуется.
- `WorkspaceSnapshot` теперь хранит entry module и document versions; добавлен
  явный snapshot-aware `CompilerSession.compileSnapshot` для будущих watch/LSP
  клиентов.
- Добавлен transactional `CompilerSession.commitResult`: cancelled и failed
  compilation results не меняют committed session, а stale candidate по-прежнему
  отбрасывается проверкой base revision.
- Differential test сравнивает clean и incremental Clojure output для initial
  compilation и body-only изменения leaf-модуля.
- Общий CLI parser сохраняет единый `--incremental` в `ArgsParsed`, а общий
  `CompilerSession.compileSnapshot(... incremental:)` является точкой выбора:
  с флагом используется переданная persistent session, без флага — новая clean
  session как differential/control path. Симметричный
  `commitResult(... incremental:)` не сохраняет state clean-компиляции.
- В репозитории пока нет реальных watch/LSP entry points: `watch` отсутствует в
  CLI parser, `CompilerOption.Daemon` пуст, а `CompilerBackend.Lsp` ведёт в
  `TO DO: "no lsp binary"`. Поэтому создание adapters и watch subsystem
  остаётся в соответствующих будущих фазах, а не входит в фазу 4.

### Завершена фаза 5: namespace-per-Niva-file

- Добавлен `CljOutput` с map generated relative paths и entry namespace.
- `IrPackage` получает детерминированные mappings:
  `model.user -> niva.generated.model.user` и
  `niva/generated/model/user.clj`.
- Новый `CljBackend.fullOutputFromIrModule` испускает отдельный Clojure file
  для каждого package, добавляет direct local `:require` и оставляет
  `fullFromIrModule` single-string control path.
- Compiler Clojure writer подключён к `CljOutput.files`; legacy `main.clj`
  сохраняется как небольшой forwarding shim на generated entry namespace.
- Shim строится из `CljOutput.entryNamespace`, поэтому не предполагает, что
  entry package всегда называется `main`.
- Generated non-entry packages больше не получают `:gen-class`.
- В package output квалифицируются cross-package function calls, record
  constructors, type references и type-match branches.
- Добавлен backend regression test для двух package output, direct require и
  qualified calls/constructors.

### Оставшаяся работа фазы 5

- [x] Подключить `CljOutput.files` к compiler/build writer, сохранив legacy
  `main.clj` shim для CLI/native build.
- [x] Довести qualification для union branches, enum Vars, error records и
  bind requirements; покрыть cross-package calls/constructors и direct
  external requires regression tests.
- [x] Writer публикует полный generated tree через staging и удаляет stale
  files заменой только после успешного codegen.
- [x] Добавить clean-vs-multi-file differential и ручной запуск generated
  Clojure project.

### Оставшаяся работа фазы 7

- [x] Вынести top-level `mut` в `IrStateDef` и испускать persistent Atom.
- [x] Добавить `^:clj-reload/keep` metadata для state Var.
- [x] Добавить deterministic state/type-shape manifest и hard-restart
  diagnostic для несовместимого изменения record, union и enum shape.
- [x] Покрыть unchanged shape, изменённые record fields, union branches и
  enum values; clean и incremental Clojure output сравниваются вместе с
  manifest.
- [x] Подключить state definitions к реальному initial-load/reload lifecycle.

### Частично начата фаза 8

- [x] Ввести общий `CljManifest` и запись `manifest.edn` после codegen.
- [x] Перевести compiler output на staging directory и directory-swap
  публикацию с generation result.
- [x] Сохранить старый generated output и manifest при failed compilation.
- [x] Подключить manifest к реальному watch/clj-reload lifecycle и обработать
  generation id/load errors.

### Оставшаяся работа фазы 4

- [x] Разделить resolver API на parse/interface collection и body resolution.
- [x] Сделать lookup environment интерфейсов immutable/cacheable; body pass
  получает отдельный mutable working `TyperDB`.
- [x] Перенести `apiHash` перед body resolution, чтобы body-only change не
  типизировал dependants фактически, а не только исключал их из invalidation set.
- [x] Добавить versioned diagnostics/cancellation result вместо подготовки
  candidate только из уже успешно построенного `ResolverHelper`.
- [x] Подключить module cache к реальному IR/backend pipeline и differential
  clean-vs-incremental tests.
- [x] Определить единый `--incremental` на доступной границе CLI/session и
  сохранить clean compilation без флага. Будущие watch/LSP adapters должны
  передавать это значение в готовый session API при появлении entry points.

### Изменённые файлы текущего подэтапа

- `argParse/cliArgs.niva`
- `argParse/cliArgsTest.niva`
- `compiler/compiler.niva`
- `compiler/compilerTests.niva`
- `back/clojureBackend/cljEmit.niva`
- `docs/hot-reload-plan/progress.md`

- `compiler/incremental.niva`
- `compiler/compiler.niva`
- `compiler/compilerTests.niva`
- `main.niva`
- `argParse/cliArgs.niva`
- `argParse/cliArgsTest.niva`
- `back/clojureBackend/cljEmit.niva`
- `back/clojureBackend/cljBack.niva`
- `back/clojureBackend/cljManifest.niva`
- `back/tests/cljBackTests.niva`
- `ir/fromTypedAst.niva`
- `ir/irTypes.niva`
- `ir/irDependencies.niva`
- `front/resolver/typeDB.niva`
- `front/resolver/astVisitor.niva`
- `libs/binds/hash.bind.niva`
- `docs/hot-reload-plan/phase-04-incremental-compilation.md`
- `docs/hot-reload-plan/implementation-and-tests.md`
- `docs/hot-reload-plan/progress.md`

### Проверки текущего подэтапа

- `niva build` — успешно после добавления JVM host, parser и watch adapter.
- `niva test cliArgsTest` — успешно, включая `watch` и runtime/target
  selection.
- `niva test compilerTests` — успешно, включая debounce/serial queue
  invariants, host lifecycle markers и публикацию host/deps.
- `niva run` — успешно.
- `niva test` — полный suite успешно.

- `niva test cljBackTests` — успешно, включая deterministic
  manifest, shape compatibility и hard-restart coverage.
- `niva test compilerTests` — успешно, включая staging publication,
  stale generated deletion и сохранение старого output при failed compilation.
- `niva test compilerTests` — предыдущие 19/19 тестов успешно, включая
  snapshot-aware compile entry point, повторное независимое body resolution
  из одного interface snapshot, transactional result commit, shape diagnostic
  и routing persistent/clean session по единому incremental flag.
- `niva test cliArgsTest` — успешно, включая opt-in/default
  семантику `--incremental` и сохранение флага для LSP backend selection.
- `niva test resolverTests` — весь suite успешно.
- `niva test irTests` — 8/8 тестов успешно.
- `niva test genericTests` — 3/3 теста успешно.
- `niva test` — полный suite предыдущего подэтапа также успешно.
- `niva build` — успешно.
- `niva run main.niva` — успешно.

## Предыдущий этап: фаза 3

Статус: завершена.

### План фазы 3

- [x] Реализовать детерминированный SCC-анализ за `O(V + E)`.
- [x] Игнорировать self-reference внутри одного модуля.
- [x] Восстанавливать конкретный цикл внутри SCC.
- [x] Привязать каждое ребро diagnostic к source token и symbol.
- [x] Запретить циклы до передачи `IrModule` backend'у.
- [x] Покрыть type/call cycles из двух и трёх модулей тестами.
- [x] Прогнать targeted и полный test suite, build и run.

### Реализовано в фазе 3

- Добавлен Tarjan SCC-pass с детерминированным порядком модулей и рёбер.
- `IrDependencyCycle` хранит concrete edge path, а не только множество
  участников SCC.
- Diagnostic содержит реальный путь, строку, направление ребра, dependency
  kind и symbol.
- Обычные `ResolverHelper.toIrModule` и `toIrModuleFromEntryLine` вызывают
  `validateNoDependencyCycles` до codegen. Для тестирования анализа существует
  явно названный `toIrModuleUncheckedFromEntryLine`.
- Recursive type declarations больше не вызывают `StackOverflowError` во время
  поиска inferred generics: traversal использует cycle-safe recursion stack.
- Self dependencies по-прежнему отсекаются normalizer'ом и не считаются
  module cycle.

### Изменённые файлы фазы 3

- `front/resolver/nivaTypes.niva`
- `ir/irTypes.niva`
- `ir/irDependencies.niva`
- `ir/fromTypedAst.niva`
- `ir/irTests.niva`
- `docs/hot-reload-plan/progress.md`

### Проверки фазы 3

- `niva test irTests` — 8/8 тестов успешно, включая 3 cycle tests.
- `niva test` — весь test suite успешно.
- `niva build main.niva` — успешно.
- `niva run main.niva` — успешно (`sus`).

## Предыдущий этап: фаза 2

### План фазы 2

- [x] Добавить exact owner package в `IrTypeRef`.
- [x] Добавить `IrDependency` с kind, token и symbol provenance.
- [x] Обойти definitions, signatures, function bodies, entry и explicit imports.
- [x] Исключить self/core/external bind dependencies.
- [x] Детерминированно дедуплицировать и сортировать dependency evidence.
- [x] Добавить API direct dependency targets для cycle detector/backend.
- [x] Покрыть type/call/constructor/match/generic dependencies тестами.
- [x] Прогнать targeted и полный test suite.

### Реализовано в фазе 2

- `IrTypeRef.ownerPkg` хранит точного владельца типа без разбора mangled id.
- `IrDependency` хранит `fromPkg`, `toPkg`, `kind`, `token` и `symbol`.
- `IrPackage.dependencies` содержит отсортированный dependency evidence, а
  `dependencyPackageNames` возвращает дедуплицированные direct graph edges.
- Новый `IrModule.withNormalizedDependencies` выполняется в конце построения
  IR и обходит:
  - поля types/unions;
  - receiver, args, return/error types и тела функций;
  - generic instantiations;
  - entry block;
  - constructors, calls и type matches;
  - local imports resolver'а как fallback evidence.
- Self dependencies и targets, которых нет среди local `IrPackage`, не
  добавляются. Благодаря этому исключены `core` и external bind packages.
- Type/union/enum definitions и union branches получили source token для
  будущих cycle diagnostics.
- Dependency evidence дедуплицируется по target/kind/symbol/source position и
  сортируется детерминированно.

### Изменённые файлы фазы 2

- `ir/irTypes.niva`
- `ir/fromTypedAst.niva`
- `ir/irDependencies.niva`
- `ir/irTests.niva`
- `docs/hot-reload-plan/progress.md`

### Проверки фазы 2

- `niva test irTests` — 5/5 тестов успешно, включая 3 новых dependency tests.
- `niva test` — весь test suite успешно.
- `niva build main.niva` — успешно.
- `niva run main.niva` — успешно.

## Исходное состояние

- `NivaBuildSystem.findAllFilesFrom` индексирует source-файлы только по
  `nameWithoutExt`.
- Вложенные директории теряются; одинаковые basename молча перезаписывают друг
  друга в `Map`.
- `ResolverHelper.resolve` получает только `Map(String, String)` и восстанавливает
  diagnostic path как `<module-id>.niva`, поэтому не знает реальный путь файла.
- Entry module вычисляется только из basename переданного entry-файла.

## План текущего изменения

- [x] Ввести source descriptor с module id, реальным и относительным путём.
- [x] Строить module id из относительного пути от project root.
- [x] Сохранить совместимый resolver entry point для unit-тестов с in-memory map.
- [x] Передавать реальный путь в lexer/parser `SourceFile`.
- [x] Диагностировать коллизии module id вместо silent overwrite.
- [x] Добавить тесты вложенных модулей, одинаковых basename и source paths.
- [x] Проверить проект через `niva build`, `niva run` и `niva test`.

## Реализовано

- `NivaSourceFile` теперь хранит `relativePath`, вычисленный относительно
  project root через нормализованные `okio.Path.segments`.
- Добавлен `NivaSourceInput`: `moduleId`, `relativePath`, реальный `path` и
  `content` передаются из build system в resolver вместе.
- Module id строится из относительного пути:
  `model/user.niva -> model.user`.
- Source-файлы сортируются по относительному пути для детерминированной сборки.
- Коллизии проверяются после нормализации регистра и `-`/`_`; также ловится
  конфликт `model/user.niva` с `model.user.niva`.
- `ResolverHelper.resolveSources` передаёт реальный путь в `SourceFile`, поэтому
  token diagnostics и последующие source maps больше не восстанавливают путь
  из module id.
- Старый `ResolverHelper.resolve(Map(String, String), ...)` сохранён как
  compatibility adapter для существующих unit-тестов.
- Исправлен synthetic path bind-файлов в compatibility adapter:
  `strings.bind.niva` вместо `strings.bind.bind.niva`.

## Изменённые файлы

- `main.niva`
- `compiler/compiler.niva`
- `compiler/compilerTests.niva`
- `front/resolver/typeDB.niva`
- `docs/hot-reload-plan/progress.md`

## Проверки

- `niva build main.niva` — успешно.
- `niva run main.niva` — успешно.
- `niva test compilerTests` — 4/4 новых теста успешно.
- `niva test` — весь test suite успешно.

## Журнал

... записи стерты для экономии токенов
- 2026-08-13: начата фаза 4; добавлены immutable snapshots, persistent
  compiler session, transactional candidate/commit, config fingerprints,
  canonical package interface и JVM SHA-256 `apiHash`.
- 2026-08-13: interface snapshot переведён с mutable `TyperDB` на cacheable
  `ResolverLookupEnvironment`; каждый body pass получает независимые lookup и
  specialization caches. Targeted compiler/resolver/generic/IR tests прошли.
- 2026-08-13: реализованы direct/reverse graphs, source/API/body/output
  invalidation, удаление модулей, reuse неинвалидированных module states и
  contributor index generic specializations.
- 2026-08-13: foundation фазы 4 покрыт 7 новыми compiler tests; targeted IR и
  generic suites, полный `niva test`, `niva build` и `niva run main.niva`
  прошли.
- 2026-08-13: apiHash перенесён в declaration/interface pass, добавлен
  selective body resolution и versioned diagnostics/cancellation result;
  compiler tests покрывают body frontier и last-good session.
- 2026-08-13: resolver pipeline получил явную границу
  `ResolverInterfaceSnapshot.collectSources -> resolveBodies`; compatibility
  entry point сохранён, interface-pass не типизирует тела. Добавлен regression
  test, compiler и resolver suites прошли.
- 2026-08-13: candidate подключён к полному IR/backend pipeline, entry block
  сохраняется при leaf body change, clean и incremental Clojure output
  сравниваются differential test.
- 2026-08-13: общий CLI parser получил флаг `--incremental`; его подключение к
  watch/LSP остаётся до появления соответствующих entry points.
- 2026-08-13: добавлен entrypoint-neutral `CompilerSession.compileSnapshot` с
  сохранением entry module/document versions и `commitResult` для безопасного
  подключения watch/LSP.
- 2026-08-13: подтверждено отсутствие реальных watch/LSP entry points; единый
  `--incremental` подключён к доступной session boundary, а clean routing и
  эквивалентность output закреплены targeted tests.
- 2026-08-13: начата фаза 5; Clojure backend получил `CljOutput`, deterministic
  package paths/namespaces, direct local requires и qualified cross-package
  calls/constructors/type references.
- 2026-08-13: `niva` compiler writer начал писать multi-file output, удалять
  stale generated files по `niva/generated/` и сохранять CLI/native entry через
  dynamic namespace shim.
- 2026-08-13: добавлен runtime differential для single-string и multi-file
  Clojure output; проверены cross-package enum Vars, error branches и bind
  requirements.
- 2026-08-13: продолжена фаза 8; Clojure writer переведён на staging
  generation и directory swap, добавлен `CljGenerationResult`, stale output
  удаляется только после успешной публикации, а failed result сохраняет старый
  output и manifest.
- 2026-08-13: завершены JVM фазы 9–10; добавлены стабильный
  `niva.hot-reload.host`, одноразовый initial load с entry invocation,
  changed reload без повторного entry, load-error recovery, WatchService с
  debounce/serial queue/shutdown и `watch` runtime selection.
- 2026-08-13: CLI parsing для `--runtime` и `--target` очищен через общие
  String-конвертеры и единый разбор separated values; добавлены проверки
  missing/flag/invalid values, targeted и полный test suite прошли.
- 2026-08-13: Babashka compatibility проверена по текущему плану как blocker:
  добавлены `CljTarget`, `bb.edn`, BB target smoke и long-lived BB harness для
  clj-reload/state/recovery. Source lifecycle проходит, но WatchService
  блокируется отсутствующим в BB `java.nio.file.StandardWatchEventKinds`; BB
  watch не включён.
- 2026-08-13: после проверки документации подтверждено, что JVM lifecycle
  покрывает initial/changed reload, dependants, state preservation, entry
  semantics, load-error recovery, debounce, serial queue и shutdown. Для BB
  остаётся только нерешённый механизм file watching; launcher/process-level
  end-to-end integration остаётся отдельной работой и `main.jar` не запускался.
- 2026-08-13: финально прошли `niva test`, `niva run` и `niva build`;
  полный suite подтвердил BB lifecycle regression и JVM lifecycle tests.
- 2026-08-13: по предоставленному BB примеру подключён pod
  `org.babashka/fswatcher` `0.0.7`; BB watcher теперь фильтрует `.niva`,
  debounce'ит events и поддерживает controlled stop. Реальный
  `babashkaHostLoadsRecoversAndWatchesSources` прошёл.

## Следующий этап

Следующий этап — интеграция launcher'а, который будет запускать этот
self-hosted path как публичный `niva watch main.niva`, и process-level tests
на долгоживущем JVM/BB процессе, включая Ctrl-C и полный lifecycle packaging.
Запуск `main.jar` для этого не требуется и не выполнялся.
