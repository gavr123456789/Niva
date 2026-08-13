# Фаза 7. Сохранение `mut`-состояния

[← Индекс плана](clojure-repl.md)

Top-level `mut` в watch mode должен компилироваться в Atom и помечаться для
сохранения `clj-reload`:

```niva
mut counter = 4
```

```clojure
^:clj-reload/keep
(def counter (atom 4))
```

Чтение и assignment продолжают генерировать `@counter` и `reset!`/`swap!` в
соответствии с семантикой Niva. При unload `clj-reload` переносит старый Var и
тот же Atom в загруженный заново namespace, поэтому initializer не сбрасывает
значение.

Нужно явно разделить:

- local `mut` внутри функции — обычный локальный Atom, не persistent state;
- top-level `mut` — persistent state Var;
- immutable definitions и функции — обновляются при reload;
- произвольный entry code — не выполняется повторно.

Текущий REPL emitter использует для top-level mutation повторный `def`, поэтому
его нельзя просто переиспользовать: watch mode требует отдельной emission
семантики.

Предпочтительно поднять top-level `IrLet(isMut: true)` из entry block в
`IrStateDef` ещё при построении IR. Его initializer относится к первичной
инициализации состояния, а оставшиеся entry statements формируют тело
`__niva_entry!`. Это не даст Clojure backend случайно выполнить один и тот же
statement и как namespace definition, и как часть entry.

### Типы и старые значения

Сохранённый Atom может содержать экземпляр `defrecord`. Если class identity
record'а меняется на reload, старое значение может перестать проходить type
checks.

Предлагаемая политика:

1. Помечать generated `defrecord` как `^:clj-reload/keep`, чтобы method-only
   reload сохранял class identity.
2. Писать в manifest shape каждого type/union: поля, branches и ABI-relevant
   параметры.
3. При изменении shape выдавать сообщение `hard restart required`, а не
   обещать безопасную миграцию старого состояния.
4. Миграцию state schema рассматривать как отдельную будущую возможность.
