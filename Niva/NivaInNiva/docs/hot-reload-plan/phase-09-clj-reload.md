# Фаза 9. Интеграция `clj-reload`

[← Индекс плана](clojure-repl.md)

JVM host получает dependency:

```clojure
io.github.tonsky/clj-reload {:mvn/version "1.0.0"}
```

Инициализация выполняется один раз:

```clojure
(reload/init
  {:dirs [".niva_clj/generated"]
   :no-reload '#{niva.hot-reload.host}
   :output :quieter})
```

Initial load:

```clojure
(reload/reload {:only :all})
```

После успешной публикации:

```clojure
(reload/reload {:only :changed})
```

Сгенерированные `:require` дают `clj-reload` граф, по которому он:

1. выгружает dependants перед изменённым namespace;
2. выгружает изменённый namespace;
3. загружает изменённый namespace;
4. загружает dependants в топологическом порядке.

При load error watch-процесс остаётся жив. После следующего исправления снова
вызывается `reload`; это штатный recovery workflow библиотеки.
