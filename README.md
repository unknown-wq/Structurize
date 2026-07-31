# Structurize → Fabric / Minecraft 26.2

Рабочий репозиторий порта. В **`1.21.1/`** лежит исходный код из ветки апстрима
`ldtteam/Structurize@version/1.21` — **только код и ресурсы, для чтения, не редактируют**.
Сборочная обвязка апстрима удалена: собирать будем своим Gradle 9.6.1 под Java 25
в отдельной папке `26.2/`.

```
/
├── 1.21.1/         # исходник: MC 1.21.1 / NeoForge 21.1.84 / Java 21, 194 java
├── porting-26.2/   # инструкции для оркестратора и агентов + скрипты массовых ренеймов
├── gradle-dist/    # вендоренный Gradle 9.6.1 (в контейнере его не скачать)
└── 26.2/           # (появится) сам порт на Fabric 26.2
```

Окружение ставится один раз:

```sh
./gradle-dist/install.sh                                  # → /opt/gradle-9.6.1
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64
/opt/gradle-9.6.1/bin/gradle --version                    # должно быть 9.6.1
```

`./gradlew` в этом окружении не работает: прокси отдаёт 403 на ассеты GitHub-релизов.

Structurize — библиотека, на которой стоит MineColonies: она читает и размещает
`.blueprint`-постройки (в форке MineColonies их 9374 штуки). По §2 плана порта
библиотека портируется **раньше** мода, который её потребляет.

## Версии на момент снапшота

Взято из удалённого `gradle.properties` — больше их взять негде.

| | |
|---|---|
| Ветка | `version/1.21` @ `11982e0` (09.07.2026) — самая свежая линия 1.21 |
| Minecraft | **1.21.1**, `minecraftRange=[1.21, 1.22)` |
| NeoForge | **21.1.84** (минимум `21.0.143`) |
| Java | **21** |
| modId / group / version | `structurize` / `com.ldtteam` / `0.0.1` |
| BlockUI | `1.0.191-1.21.1-snapshot` |
| Domum Ornamentum | `1.0.203-1.21.1-snapshot` |
| Parchment | `1.21 / 2024.07.28` — после 26.1 не нужен, игра необфусцирована |

`version/main` апстрима (и то, на что смотрел форк до этого коммита) — это линия
**1.20.1 / Forge 47.1.3 / Java 17**, то есть на версию старше. Мы на ней не работаем.

## Зависимости: можно ли работать только с этим репозиторием?

**Почти.** Из 194 java-файлов внешние библиотеки ldtteam трогают ~50, и ни одна из них
не является структурной — в отличие от MineColonies, где GUI целиком чужой.

| Библиотека | Файлов | Импортов | Что реально используется |
|---|---|---:|---|
| `com.ldtteam.common` | 36 | 65 | **49 из 65 — сетевой слой** (`PlayMessageType`, `AbstractServerPlayMessage`, `AbstractClientPlayMessage`). Остальное мелочь: `BlockToItemHelper`, `LanguageHandler`, `AbstractConfiguration`, `fakelevel/SingleBlockFakeLevel` |
| `com.ldtteam.blockui` | 14 | 64 | GUI: `BOWindow`, `Pane`, `View`, `ScrollingList`, `Button`, `TextField`, `Text`, `ItemIcon` |
| `com.ldtteam.domumornamentum` | 4 | 15 | Интеграция с их декоративными блоками: `IMateriallyTexturedBlock`, `MaterialTextureData`, `BlockUtils` |

Что это значит для порта:

- **`com.ldtteam.common` почти не проблема.** Три четверти — это их обёртка над
  NeoForge-пакетами, а сетевой слой при переходе на Fabric переписывается в любом
  случае (`CustomPacketPayload` + `StreamCodec` + `PayloadTypeRegistry`, контракт C3).
  Оставшиеся ~8 файлов утилит — их собственный код под GPL-3.0, копируется внутрь.
- **BlockUI — 14 файлов, и это единственное настоящее решение.** Либо портировать
  BlockUI отдельно (свой репозиторий `ldtteam/BlockUI`, тогда он же закроет 146 файлов
  GUI в MineColonies), либо переписать эти 14 экранов на ванильный `Screen`.
  Для самого Structurize второе дешевле; для связки с MineColonies — первое.
- **Domum Ornamentum — 4 файла, режется по §10.** В `neoforge.mods.toml` он объявлен
  обязательным, но по коду это опциональная интеграция с их блоками; без неё
  Structurize работает.

Всё остальное в импортах — то, что и так приезжает с игрой: `com.mojang.brigadier`
(39), `com.mojang.blaze3d` (19), `com.mojang.serialization` (15), `io.netty` (12),
`com.google.gson` (12), Guava, `org.joml`, fastutil, Apache Commons, slf4j, LWJGL,
`org.jetbrains.annotations` (74 файла — аннотации, при желании меняются на jspecify).
Отдельных сторонних библиотек, которых нет в 26.2, **нет**.

**Вывод:** Structurize можно портировать в одиночку, отложив BlockUI на решение
оркестратора, а Domum Ornamentum — отрезав. Это на порядок легче MineColonies:
194 файла против 2060, 3.5 МБ ресурсов против 150 МБ, и своих миксинов тоже нет.

## Что вычищено из `1.21.1/` относительно апстрима

`build.gradle` состоял из одной строки
`apply from: 'https://raw.githubusercontent.com/ldtteam/OperaPublicaCreator/ng7/gradle/mod.gradle'`,
то есть весь build-скрипт качался из сети, а зависимости шли с maven ldtteam.

- **Сборка:** `build.gradle`, `settings.gradle`, `gradle.properties`, `gradlew`,
  `gradlew.bat`, `gradle/` (wrapper + скрипты конфигурации).
- **CI и сервисы организации:** `.github/` (воркфлоу релизов, шаблоны issue и PR
  чужого трекера), `.travis.yml` + `travis/`, `crowdin.yml`, `.phraseapp.yml`,
  `.pullapprove.yml`, `sonar.json`.
- **Стиль и dev-окружение:** `config/` (checkstyle, PMD), `documentation/`
  (настройка Eclipse/IntelliJ, Sonar).
- **Процесс апстрима:** `CLA.md`.

Осталось: `src/` (194 java + `datagen` и `test` сорссеты + ресурсы), `LICENSE`
(GPL-3.0), `README.md` и `structurize.png` самого мода, `.gitattributes`.

## Инструкции по порту

Лежат здесь же, в `porting-26.2/` — те же файлы, что в форках MineColonies и
Domum Ornamentum:

| Файл | Что это |
|---|---|
| **`PORT-ANY-MOD-26.2.md`** | Универсальный план: выбор маршрута, окружение, замороженные контракты, разбиение работы между агентами, правило деградации, приёмка, готовые промпты. **Начинать отсюда** |
| `NEOFORGE-TO-FABRIC-26.2.md` + `NOTES-A/B/C.md` | Ближайший аналог задачи: NeoForge → Fabric 26.2 одним хопом, пофайловые рецепты, проверенные по декомпилированным исходникам |
| `PORT-MOD-26.2.md`, `PORT-CHEATSHEET.md`, `PORTING-GUIDE-26.2.md` | Таблицы ренеймов, готовые исправления повторяющихся ошибок компиляции, пер-версионный hit-list ломок |
| `port-resolve-imports.py` | Переписывает `import net.minecraft.*` по декомпилированному дереву — закрывает переехавшие пакеты без ручных правок |
| `fix-recipes.py`, `port-rename*.sh`, `port-rename6.pl` | Рецепты JSON в формат 26.2 и массовые ренеймы |

`API-CHECKLIST-26.2.md` из форка MineColonies сюда не копировался: его цифры получены
grep-ом по тому коду и для этих 194 файлов неверны. Аналог собирается тем же способом —
скрипт пересчёта лежит в конце того файла.
