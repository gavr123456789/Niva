# Фаза 10. `niva watch` и runtime selection

[← Индекс плана](clojure-repl.md)

Предлагаемый CLI:

```bash
niva watch main.niva                         # JVM по умолчанию
niva watch main.niva --runtime jvm
niva watch main.niva --runtime bb            # после compatibility phase

niva run main.niva --backend=clojure --runtime=jvm
niva run main.niva --backend=clojure --runtime=bb
niva build main.niva --backend=clojure --target=jvm
niva build main.niva --backend=clojure --target=bb
```

`--runtime` выбирает процесс для `run/watch`, а `--target` — формат build и
packaging. На первом этапе допустимо поддержать для `watch` только JVM и
возвращать понятную ошибку для `--runtime bb`.

Не следует создавать два расходящихся emitter'а. Вместо этого Clojure backend
получает capabilities target'а, например:

```text
CljTarget
  runtime: Jvm | Babashka
  supportsAot: Bool
  supportsCljReload: Bool
```

### Watch loop

1. Рекурсивно зарегистрировать project directories в Java `WatchService`.
2. Следить за create/modify/delete `.niva`.
3. Debounce серию editor events, например на 100–200 ms.
4. Не запускать две компиляции одновременно; если во время build пришло новое
   событие, выполнить ещё один build после текущего.
5. После успешной компиляции опубликовать generation и вызвать reload.
6. После compile error оставить текущий runtime как есть и ждать следующего
   сохранения.
7. После reload error показать Clojure exception и продолжить watch loop.

Для диагностического fallback watcher поддерживает полную перекомпиляцию, но
штатный путь использует `CompilerSession`. Инкрементальная компиляция не меняет
семантику reload: наружу публикуется только целиком успешная generation.
