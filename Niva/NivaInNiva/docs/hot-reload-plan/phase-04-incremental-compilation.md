# Фаза 4. Инкрементальная компиляция

[← Индекс плана](clojure-repl.md)

Текущий scope фазы — Clojure backend для JVM/Babashka. Подключение Go backend к
инкрементальной компиляции пока не делать и не использовать как критерий
готовности этой фазы; его планирование будет отдельной задачей позже.

Граф модулей нужен не только hot reload, но и будущему LSP. Поэтому
инкрементальная компиляция является частью архитектуры компилятора, а не
оптимизацией внутри watcher'а.

Гранулярность первой версии — один Niva-файл (`module id`). Более мелкий кеш
отдельных деклараций заметно усложнит lookup методов, inference и generic
specialization. Его следует рассматривать только после профилирования
module-level реализации.

### Persistent compiler session

`watch` и LSP должны держать отдельный `CompilerSession` на workspace:

```text
CompilerSession
  committedSnapshot: WorkspaceSnapshot
  modules: Map(ModuleId, ModuleState)
  dependencies: Graph<ModuleId>
  reverseDependencies: Graph<ModuleId>
  specializationUses: Map<SpecializationId, Set<ModuleId>>
  compilerConfig: CompilerConfigFingerprint
```

`WorkspaceSnapshot` неизменяемый: для watcher он строится из файлов, для LSP —
из versioned in-memory документов с fallback на disk. Каждая попытка
компиляции создаёт candidate session. Ошибка parser/resolver/codegen не должна
частично изменить последний успешно committed snapshot или опубликованные
generated-файлы.

Сессию следует разделить на слои, чтобы LSP мог остановиться раньше backend:

```text
source hash
  → tokens / parsed AST
  → module interface
  → resolved bodies / typed module
  → IrPackage
  → backend output
```

Первая реализация хранит кеши в памяти процесса: это обязательный режим и для
`watch`, и для LSP. Persistent disk cache добавляется позднее, когда
self-hosted компилятор сможет сериализовать compiler state в EDN. Disk cache
будет project-local (`.niva-cache/`), чтобы очистка и диагностика stale entries
оставались прозрачными.

### Разделение resolver'а

Текущий resolver создаёт один mutable `TyperDB`, сначала глобально собирает
типы и декларации, а затем разрешает тела. Простого кеша `IrPackage` поэтому
недостаточно: старый IR не содержит всех данных, необходимых для корректного
разрешения изменившихся соседей.

Resolver нужно явно разделить на проходы:

1. parse изменившихся sources;
2. собрать public interface каждого модуля: типы, unions/enums, signatures,
   видимые методы и необходимые generic constraints;
3. построить immutable lookup environment из интерфейсов текущего snapshot;
4. разрешить тела invalidated модулей;
5. построить их IR и backend output.

Интерфейс должен иметь детерминированное представление и `apiHash`. В текущем
bootstrap-компиляторе SHA-256 нужно предоставить через Kotlin/JVM binding;
Niva-код формирует каноническое представление интерфейса и передаёт его в
binding. После self-hosting тот же формат можно сохранять и читать как EDN.
В hash входят только данные, способные изменить типизацию клиента: public names,
signatures, receiver/argument/return/error types, type shapes, union branches,
generic parameters/constraints и visibility. Тела функций, source positions и
форматирование в `apiHash` не входят.

Каждый cache key включает:

- content hash исходника;
- `apiHash` прямых dependencies;
- версию компилятора и IR schema;
- backend/target и ABI-relevant compiler options;
- hash core/runtime ABI.

Необязательные codegen options должны инвалидировать только backend layer, а
не parser и type resolution.

### Правила invalidation

Для каждой новой версии snapshot:

1. Добавленный, изменённый или удалённый файл инвалидирует собственный модуль.
2. У изменившегося модуля заново вычисляется interface.
3. Если `apiHash` не изменился, его dependants сохраняют typed/IR cache;
   пересобирается только output самого модуля.
4. Если `apiHash` изменился, по reverse graph инвалидируются transitive
   dependants; распространение можно остановить там, где пересчитанный
   `apiHash` остался прежним.
5. Изменение состава dependencies обновляет direct и reverse graph и повторно
   запускает cycle check для затронутого подграфа. Для первой версии допустим
   полный `O(V + E)` SCC-pass: он проще и обычно дешевле type checking.
6. При недостаточной информации об interface применяется корректный
   conservative fallback: пересчитать transitive dependants.

Это отличается от runtime reload graph. Compile invalidation может не трогать
клиента при неизменном API, но после публикации `clj-reload` всё равно сам
определяет reload set по изменениям generated Clojure namespace'ов.

### Generic specializations

Сейчас `IrFunction.instantiations` зависит от concrete call sites, которые
могут находиться в других модулях. Поэтому обычного dependency graph
недостаточно: добавление или удаление вызова может изменить output модуля,
владеющего generic function, даже если его source и API не менялись.

Нужен отдельный индекс:

```text
(functionId, concreteTypeKey) → contributing call-site modules
```

При изменении contributor пересчитывается его набор specialization uses. Если
набор изменился, инвалидируется IR/codegen owner-модуля. Это codegen dependency,
но не основание добавлять обратный type dependency или создавать ложный цикл
между Niva-модулями. Specialization-use index входит в первую реализацию
инкрементального компилятора. Conservative regeneration owner-модулей остаётся
только защитным fallback для неполных данных, а не отдельным этапом rollout.

### LSP-ограничения

Compiler API не должен быть привязан к filesystem watcher или записи `.clj`:

- вход — immutable snapshot и список document versions;
- результат — diagnostics и новые cache entries;
- промежуточная компиляция отменяема при поступлении новой версии документа;
- cancelled/failed candidate не коммитится;
- LSP может выполнить parse/interface/body resolution без codegen;
- diagnostics всегда относятся к версии документа, для которой вычислены.

### Рекомендуемый rollout

1. Ввести `CompilerSession`, snapshot и module-level cache API без disk cache.
2. Разделить declaration/interface collection и body resolution.
3. Добавить `apiHash`, reverse graph и conservative invalidation.
4. Добавить specialization-use index.
5. Подключить единый opt-in флаг `--incremental` к `watch` и LSP; сохранить
   clean compilation без флага как differential/control path.
6. После появления project-local EDN-кеша поддержать тот же флаг у `build`.

### Принятые решения

- Гранулярность кеша — Niva-модуль.
- `watch` и LSP используют in-memory cache.
- Invalidation dependants определяется через `apiHash`, с conservative fallback.
- Specialization-use index реализуется сразу.
- Incremental mode включается единым opt-in флагом `--incremental` для
  `watch`, LSP и, после появления disk cache, `build`.
- Будущий persistent cache — project-local `.niva-cache/` в EDN.
- Go backend пока исключён из incremental scope и не реализуется в этой фазе.
