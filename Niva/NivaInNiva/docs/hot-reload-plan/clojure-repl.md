# Niva hot reload через Clojure namespace'ы

## Цель

Добавить режим:

```bash
niva watch main.niva
```

Он должен следить за обычными `.niva`-файлами, компилировать каждый Niva-файл
в отдельный Clojure namespace и перезагружать изменившиеся namespace'ы вместе
с их зависимостями через
[`clj-reload`](https://github.com/tonsky/clj-reload).

Первая обязательная runtime-цель — JVM Clojure. Babashka остаётся
дополнительной целью и включается после compatibility-тестов.

## Принятые решения

- Один обычный Niva-файл соответствует одному generated Clojure namespace.
- `niva watch main.niva` автоматически реагирует на сохранение файлов.
- Перезагрузка обновляет объявления, но не запускает повторно произвольные
  top-level entry expressions.
- Значения top-level `mut` считаются состоянием и сохраняются между reload'ами.
- Простота важнее транзакционного rollback: при load error используется
  стандартная модель восстановления `clj-reload`.
- Циклические зависимости в первой версии являются compile error.
- JVM и Babashka — варианты одного Clojure backend, а не два независимых
  backend'а.
- Инкрементальная компиляция имеет module-level гранулярность и включается
  единым opt-in флагом `--incremental`.
- `watch` и LSP сначала используют in-memory cache; будущий disk cache хранится
  project-local в `.niva-cache/` и сериализуется как EDN.

## Что уже есть

Текущий REPL на каждом вводе:

1. дописывает строку к накопленному Niva source;
2. заново парсит и типизирует весь source;
3. через `--repl-line` оставляет в entry IR только новые выражения;
4. генерирует один namespace `niva.repl.session`;
5. вычисляет его через `load-string` в долгоживущем Clojure-процессе.

Для редактируемого файла эта append-only модель не подходит: измениться или
исчезнуть может любая строка. Hot reload должен заново компилировать проект,
публиковать набор generated `.clj`-файлов и поручать выбор порядка загрузки
`clj-reload`.

IR уже имеет подходящую основу:

```text
IrModule
└── packages: List(IrPackage)
    └── name, imports, types, unions, enums, functions
```

Основная переделка нужна в Clojure backend и build system. IR не требуется
создавать заново, но его модель зависимостей нужно сделать явной и проверяемой.

Ожидаемые точечные расширения IR:

- явные dependencies пакета с source provenance;
- однозначный owner package у cross-package references;
- отдельное представление top-level state definitions, например `IrStateDef`,
  вместо backend-specific распознавания `IrLet` внутри entry block;
- сохранение `IrModule.entry` как отдельно вызываемого executable block.

Таким образом, существующее деление на `IrPackage` сохраняется, но dependency и
state semantics перестают быть неявными.

## Целевая архитектура

```text
*.niva
  │ Java WatchService + debounce
  ▼
Niva parser/resolver
  │
  ├── IrPackage на каждый source-файл
  ├── нормализованный dependency graph
  ├── persistent CompilerSession и module-level caches
  └── проверка циклов
  ▼
staging generation
  ├── niva/generated/main.clj
  ├── niva/generated/http.clj
  ├── niva/generated/model.clj
  └── manifest.edn
  │ atomic publish
  ▼
clj-reload/reload
  │ unload dependants → unload changed
  │ load changed → load dependants
  ▼
тот же JVM-процесс и сохранённые state Vars
```

Watch host является стабильным namespace'ом и не должен статически `:require`
generated namespace'ы. Обращение к entry hooks выполняется через
`requiring-resolve`/`resolve`, иначе host может удержать ссылку на старый Var.

## Файлы фаз

Для продолжения работы достаточно читать этот индекс, `progress.md` и файл
текущей фазы.

- [Фаза 1. Стабильные module id и пути файлов](phase-01-module-ids.md)
- [Фаза 2. Явный граф зависимостей](phase-02-dependency-graph.md)
- [Фаза 3. Детектирование циклов](phase-03-cycle-detection.md)
- [Фаза 4. Инкрементальная компиляция](phase-04-incremental-compilation.md)
- [Фаза 5. Namespace-per-Niva-file в Clojure backend](phase-05-clojure-backend.md)
- [Фаза 6. Отделить declarations от entry execution](phase-06-entry-execution.md)
- [Фаза 7. Сохранение `mut`-состояния](phase-07-state-preservation.md)
- [Фаза 8. Generated manifest и атомарная публикация](phase-08-publishing.md)
- [Фаза 9. Интеграция `clj-reload`](phase-09-clj-reload.md)
- [Фаза 10. `niva watch` и runtime selection](phase-10-watch-cli.md)

Общие этапы, тестовая матрица, критерии готовности и риски вынесены в
[implementation-and-tests.md](implementation-and-tests.md).
