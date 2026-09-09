# Фаза 8. Generated manifest и атомарная публикация

[← Индекс плана](clojure-repl.md)

Компиляция должна идти во staging-каталог, чтобы `clj-reload` не увидел набор
файлов, записанный наполовину:

```text
.niva_clj/
  generated/       # последний опубликованный набор
  staging/<id>/    # текущая компиляция
  manifest.edn
```

Manifest содержит как минимум:

```clojure
{:version 1
 :entry-ns niva.generated.main
 :modules {main       {:file "niva/generated/main.clj"
                       :deps #{model.user}}
           model.user {:file "niva/generated/model/user.clj"
                       :deps #{}}}
 :state-vars {...}
 :type-shapes {...}}
```

Publish выполняется только после успешных parser, resolver, cycle check и
codegen. При публикации необходимо удалить generated-файлы исчезнувших
модулей: `clj-reload` умеет учитывать удалённые namespace'ы.

Нельзя продолжать определять compile success только по exit code и наличию
старого `main.clj`. Watch pipeline должен получать структурированный результат
компиляции либо новый generation id.
