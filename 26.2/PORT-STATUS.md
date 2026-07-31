# PORT-STATUS — Structurize → Fabric / Minecraft 26.2

Живой документ порта. Создан оркестратором на шаге 0, дополняется всеми агентами.
Закон порта — `../porting-26.2/PORT-ANY-MOD-26.2.md`. Выше него по авторитету только
декомпилированные исходники игры в `/opt/mc-src`.

**Писать в этот файл имеет право только оркестратор.** Агент пишет отчёт в свой
`../porting-26.2/FINDINGS-<A|B1|B2|C|D>.md`.

## Toolchain — ГОТОВО, НЕ ПЕРЕУСТАНАВЛИВАТЬ

| | |
|---|---|
| Java | `/usr/lib/jvm/java-25-openjdk-amd64` (Java 25) |
| Gradle | `/opt/gradle-9.6.1/bin/gradle` — **только он**, `./gradlew` не работает (403 на ассеты GitHub) |
| `/opt/mc-src` | декомпилированный Minecraft 26.2, **ГОТОВ** — 7055 java, `net.minecraft.resources.Identifier` на месте, `ResourceLocation` 0 вхождений. **Только grep, никогда не перегенерировать** |
| DO jar | `26.2/libs/domum_ornamentum-26.2-1.0.0.jar` — собран из `/workspace/domum-ornamentum/26.2`, `BUILD SUCCESSFUL` |
| Проект | `/home/user/Structurize/26.2/` |
| Ветка | `claude/strukturayz-dependencies-agents-sytuwq` |

Любая сборка:
```sh
cd /home/user/Structurize/26.2 && JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 \
  /opt/gradle-9.6.1/bin/gradle <task> --no-daemon 2>&1 | tee /tmp/errors.txt
```
**Одна инвокация Gradle одновременно на весь чекаут.** Две параллельные портят кэш Loom.

### Пины версий

`minecraft 26.2` · `fabric-loader 0.19.3` · `fabric-api 0.154.2+26.2` · `loom 1.17.13`
· `gradle 9.6.1` · `java 25` · **строки `mappings` нет**

Свойства мода сохраняются: `mod_id=structurize`, `maven_group=com.ldtteam`,
`archives_base_name=structurize-26.2`.

### Референсные порты на диске (приоритет выше своей памяти)

| Путь | Чем полезен |
|---|---|
| `/workspace/domum-ornamentum/26.2/` | **Ближайший аналог и живая зависимость.** NeoForge 26.1 → Fabric 26.2. Регистрация блоков/предметов через `Supplier`, миксин на `BlockBehaviour$Properties`, датаген, вся клиентская модельная кухня. Его `PORT-GAPS.md` — разобранная по шагам миграция рендера 26.2 |
| `/workspace/simple-planes/26.2/` | **NeoForge 1.21.1 → Fabric 26.2 одним хопом — ровно наш маршрут.** Регистрация, меню/контейнеры, рецепты, сеть, рендер сущностей |
| `/workspace/desolation/` | Fabric-датаген (`fabricApi.configureDataGeneration`), worldgen, дельта 26.1.2 → 26.2 |
| `/workspace/fabric-luckytntmod/` | Рендереры 26.2, HUD, сущности |
| `/home/user/Structurize/1.21.1/` | **Источник порта. ТОЛЬКО ЧТЕНИЕ, не редактировать.** NeoForge 21.1.84 |

## Rules (выжимка §9 + §10 закона — прочитать первым)

**DO**
1. Прежде чем писать — найти тот же паттерн в портированном референсном моде на диске.
2. Любую версионно-зависимую сигнатуру подтвердить `grep -rn '<symbol>' /opt/mc-src/`.
3. Работать **от ошибок, а не от файлов**: список ошибок → открыть только падающие строки → починить.
4. Держаться своего списка файлов. Нужна правка в чужом — написать в отчёте, самому не трогать.
5. Диффы маленькие и механические.
6. Если `/opt/mc-src` противоречит инструкции — **прав он**; сделать по нему и явно сказать в отчёте.

**DON'T**
1. Не запускать `./gradlew`, ничего не качать (403). Gradle не запускать, если роль не разрешает.
2. Не коммитить и не пушить — это делает оркестратор.
3. Не изобретать имена методов. Не подтвердил после двух grep-ов → §10.
4. Никаких yarn-имён (`MinecraftClient`, `World`, `NbtCompound`, `Text`, `Vec3d`, `DrawContext`,
   `Item.Settings`, `class_XXXX`), никакого `ResourceLocation`, никакого `Identifier.of`.
   Класс — `net.minecraft.resources.Identifier`, фабрика — `Identifier.fromNamespaceAndPath(...)`.
5. Не доверять туториалам до 2026 года и собственной памяти по сигнатурам.
6. Не редактировать `1.21.1/` — это источник, только чтение.
7. Вопросов пользователю не задавать.

**§10 — правило деградации.** Сопротивляется после ~двух честных попыток → **не блокировать сборку
и не удалять код**, а спуститься по лестнице: (1) отключить строку регистрации → (2) заглушить тело
метода, оригинал оставить рядом в комментарии → (3) функциональная деградация → (4) выкинуть
объект данных. Приоритет: **зелёная сборка важнее полноты фич**; серверный геймплей > клиентская
визуалка > совместимость.

**Каждый срез логируется ТРЕМЯ способами:**
1. В коде: `// TODO(port-26.2): DISABLED — <причина одной строкой>`, оригинал рядом, не удалять.
2. Строка в отчёте агента (`FINDINGS-<роль>.md`).
3. Строка в `PORT-GAPS.md` — её пишет оркестратор по отчёту.

Финальная сверка оркестратора: `grep -rn "TODO(port-26.2)" src | wc -l` == числу строк в `PORT-GAPS.md`.

## Копилка знаний для порт-кита

Всё, чего **не было** в `../porting-26.2/*.md` и что пришлось выяснять самому — новый класс,
переехавший пакет, изменившаяся сигнатура, мёртвый API, рабочий обходной путь — агент пишет
в **свой** файл `../porting-26.2/FINDINGS-<A|B1|B2|C|D>.md` в формате:

```
### <Символ или тема>
- **Было (NeoForge 1.21.1):** ...
- **Стало (26.2):** ...
- **Подтверждено:** /opt/mc-src/<путь>:<строка>  ИЛИ  /workspace/<мод>/<файл>:<строка>
- **Комментарий:** грабли, порядок инициализации, что ломается молча
```

Дублировать то, что в ките уже есть, не надо: копилка только для нового.

## Маршрут

База — `1.21.1/` (NeoForge 21.1.84). Один хоп: **NeoForge → Fabric и 1.21.1 → 26.2 одновременно**,
как в simple-planes. Обе стороны уже на Mojang-маппингах, промежуточного 26.1 нет и не будет.

Объём: 194 java, **0 миксинов**, ресурсы 1.7 МБ, 1 шейдер.

Внешние имена — четыре, все ldtteam:

| Зависимость | Файлы | Импорты | Решение |
|---|---|---|---|
| NeoForge | 71 | 141 | переписывается на Fabric |
| `com.ldtteam.common` | 36 | 65 | **копируется внутрь** → `compat/common/` (C8) |
| `com.ldtteam.blockui` | 15 | 64 | **паркуется** до готовности порта BlockUI (C9) |
| `com.ldtteam.domumornamentum` | 4 | 13 | **подключается живьём** — порт DO готов (C7) |

Сторонних библиотек вне ldtteam нет.

## Замороженные контракты

- **C1 — форма полей реестра сохраняется.** `DeferredRegister`/`DeferredHolder` → статический
  `register(...)`, возвращающий `Supplier<T>`. Все `.get()` по коду **не трогаются**.
  ```java
  public static <T extends Item> Supplier<T> register(String name, Function<Item.Properties, T> factory, Item.Properties props) {
      ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Constants.MOD_ID, name));
      T value = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
      return () -> value;
  }
  ```
  `Item.Properties.setId(ResourceKey)` обязателен, иначе `NullPointerException: Item id not set`.
- **C2 — Identifier.** `net.minecraft.resources.Identifier` вместо `ResourceLocation`. Фабрики:
  `fromNamespaceAndPath` / `parse` / `withDefaultNamespace`. **`Identifier.of` не существует.**
- **C3 — тулчейн.** Никаких строк `mappings` в Gradle. Java 25, Gradle 9.6.1.
- **C4 — Gradle трогает только агент A.** Остальные — никогда, даже одну строку.
- **C5 — entrypoints.** `Structurize implements ModInitializer` и
  `StructurizeClient implements ClientModInitializer` (новый). Оба файла — **только у агента A**.
- **C6 — `PORT-STATUS.md` и `PORT-GAPS.md` пишет только оркестратор.**
- **C7 — Domum Ornamentum подключается живьём и изолированно.**
  Порт DO готов: `/workspace/domum-ornamentum/26.2/`, jar кладётся в `26.2/libs/`.
  Все 9 символов сверены — **расхождений нет**:

  | Зовёт Structurize | Есть в DO 26.2 |
  |---|---|
  | `block.IMateriallyTexturedBlock` | ✅ тот же пакет |
  | `block.AbstractBlockDoor<B extends AbstractBlockDoor<B>>` | ✅ дженерик на месте |
  | `block.decorative.PillarBlock` | ✅ |
  | `client.model.data.MaterialTextureData.CODEC` | ✅ `MaterialTextureData.java:29` |
  | `entity.block.MateriallyTexturedBlockEntity` | ✅ |
  | `entity.block.IMateriallyTexturedBlockEntity#getTextureData()` | ✅ `:23` |
  | `entity.block.ModBlockEntityTypes.MATERIALLY_TEXTURED` | ✅ `Supplier<BlockEntityType<BlockEntity>>`, `.get()` работает |
  | `util.Constants.BLOCK_ENTITY_TEXTURE_DATA` | ✅ `Constants.java:19` |
  | `util.BlockUtils.getMaterializedItemStack(be, provider)` | ✅ varargs, 2-арг вызов компилируется |

  Ни один из 13 маркеров в `PORT-GAPS.md` DO эти символы не задевает — всё вырезанное там
  клиентское (JEI, ghost-render, ItemColor). **DO-интеграция Structurize не деградирует.**
  Тем не менее вся работа с DO идёт через один фасад `compat/DomumCompat.java`: если DO
  когда-нибудь отвалится, отключается одна точка, а не четыре файла.
- **C8 — `com.ldtteam.common` РЕАЛИЗУЕТСЯ ЗАНОВО внутри мода**, в пакете
  `com.ldtteam.structurize.compat.common`, под GPL-3.0. Внешней зависимости на неё не будет.

  **Важно (выяснено на шаге 0): исходников `com.ldtteam.common` на диске НЕТ.**
  Библиотека не отдельный мод — она едет внутри jar-а BlockUI (`neoforge.mods.toml` Structurize
  объявляет ровно два `required`-мода: `blockui` и `domum_ornamentum`; в DO 26.2
  `com/ldtteam/common` отсутствует — проверено `find`). Значит скопировать неоткуда:
  каждый тип восстанавливается **по своим call-site'ам в `1.21.1/`**, где виден полный контракт.

  Нужно ровно 11 типов (65 импортов):

  | Тип | Импортов | Кто ждёт | Как восстанавливать |
  |---|---:|---|---|
  | `network.PlayMessageType` | 25 | 25 сообщений (B1) | реестр типов → `CustomPacketPayload.Type` + `StreamCodec` + `PayloadTypeRegistry.playS2C/playC2S` |
  | `network.AbstractServerPlayMessage` | 17 | B1 | базовый serverbound payload, `onExecute(ServerPlayer)` |
  | `network.AbstractClientPlayMessage` | 7 | B1 | базовый clientbound payload |
  | `network.AbstractPlayMessage` | 1 | B1 | общий предок |
  | `fakelevel.FakeLevel<T>` | 1 | `client/fakelevel/BlueprintBlockAccess` (D) | **самый тяжёлый кусок**: `extends Level`. 26.2-проверенный образец рядом — `/workspace/domum-ornamentum/26.2/src/main/java/com/ldtteam/domumornamentum/util/SingleBlockLevelReader.java` (248 строк, `LevelReader`) и `SingleBlockBlockReader` |
  | `fakelevel.IFakeLevelBlockGetter` | 1 | `blueprints/v1/Blueprint implements` его (B2) | интерфейс доступа к блокам, контракт виден из `Blueprint` |
  | `fakelevel.SingleBlockFakeLevel` (+ `SidedSingleBlockFakeLevel`) | 2 | `api/ItemStackUtils` (A), `client/TagSubstitutionRenderer` (D) | `withFakeLevelContext` / `useFakeLevelContext` / `get(level)` |
  | `fakelevel.IFakeLevelLightProvider` (+ `ConfigBasedLightProvider`) | 2 | `BlueprintBlockAccess` (D) | провайдер уровня света из конфига |
  | `util.BlockToItemHelper` | 3 | B2 | блок → предмет |
  | `config.AbstractConfiguration` + `config.Configurations` | 3 | `config/**` (A) | база конфигов |
  | `language.LanguageHandler` | 2 | A/B2 | обёртка переводов |
  | `codec.Codecs` | 1 | B2 | хелперы кодеков |

  **Делает всё это агент A** — сеть ждёт B1, `fakelevel` ждут B2 и D, конфиги нужны сразу.
  Порядок внутри A: сначала `network/*` и `config/*` (простые, разблокируют B1), потом `fakelevel`.
  Если `FakeLevel` сопротивляется — §10, но **не блокировать фазу**: заглушить методы, которых
  нет в 26.2, и записать в отчёт.

  **Отложенное решение (после порта BlockUI):** когда приедет настоящая `com.ldtteam.common`,
  можно будет механическим переписыванием импортов вернуть Structurize на неё — чтобы у
  MineColonies и Structurize была одна общая иерархия типов, а не две параллельные.
  До тех пор наша копия самодостаточна и ничего не ломает.
- **C9 — BlockUI паркуется.** Порт BlockUI делает заказчик, он приедет позже.
  - Исключаются из sourceSet (13 файлов, 4496 строк):
    `client/gui/*.java` (11 окон + `AbstractWindowSkeleton`) и
    `client/gui/util/InputFilters.java`, `client/gui/util/ItemUtil.java`.
  - **`client/gui/util/ItemPositionsStorage.java` НЕ паркуется** — он BlockUI не трогает,
    а нужен `network/messages/{RemoveBlockMessage,ReplaceBlockMessage}` и
    `operations/{RemoveBlockOperation,RemoveFilteredOperation,ReplaceBlockOperation}`.
  - 14 XML-layout'ов (`assets/structurize/gui/*.xml`, 454 строки) остаются в ресурсах нетронутыми —
    это данные, компиляции не мешают.
  - Все внешние обращения к окнам идут через **`client/gui/GuiStubs.java`** (создаёт A).
    Call-site'ов ровно 9 в 7 файлах:

    | Файл | Что зовёт | Владелец |
    |---|---|---|
    | `items/ItemBuildTool.java:62` | `new WindowExtendedBuildTool(...).open()` | A |
    | `items/ItemScanTool.java:112` | `new WindowScan(data).open()` | A |
    | `items/ItemShapeTool.java:29,42` | `new WindowShapeTool(...).open()` | A |
    | `items/ItemTagTool.java:74` | `new WindowTagTool(...).open()` | A |
    | `network/messages/OperationHistoryMessage.java:54` | `WindowUndoRedo.lastOperations = ...` | B1 |
    | `event/ClientEventSubscriber.java:39,171` | `instanceof BOScreen`, `clearStaticData()` | B1 |
    | `client/ModKeyMappings.java:25` | `instanceof BOScreen` | D |

  - `client/ClientItemStackTooltip.java:51` трогает BlockUI **только в javadoc `@see`** — не блокирует.
  - Расшивка в фазе 4: убрать `exclude` из `build.gradle`, вернуть тела `GuiStubs`.
- **C10 — `/opt/mc-src` принадлежит оркестратору.** Агент только grep. Пусто или отсутствует →
  остановиться и доложить, **не регенерировать**.

## Ownership — списки файлов, пересечений нет

Пути от `26.2/src/main/java/com/ldtteam/structurize/`, если не сказано иное.

### Агент A — фундамент, сборка, регистрация (фаза 1, работает один)

Сборочные (единственный владелец, C4):
`26.2/build.gradle`, `26.2/settings.gradle`, `26.2/gradle.properties`,
`src/main/resources/fabric.mod.json` (новый), удаление `src/main/resources/META-INF/neoforge.mods.toml`

Entrypoints: `Structurize.java`, `StructurizeClient.java` (новый)

Новое: `compat/DomumCompat.java`, `client/gui/GuiStubs.java`, `compat/common/**` (перенос C8)

Существующее: `api/**` (20), `blocks/**` (11), `items/**` (12), `blockentities/**` (3),
`component/**` (2), `tag/**` (1), `config/**` (2), `datagen/**` (3)

### Агент B1 — сеть, события, команды (фаза 2)
`network/**` (25), `event/**` (6), `commands/**` (9), `management/**` (2)

### Агент B2 — логика мира (фаза 2)
`placement/**` (24), `storage/**` (13), `util/**` (15, кроме `util/WorldRenderMacros.java`),
`operations/**` (9), `blueprints/**` (7)

### Агент D — рендер (фаза 2)
`util/WorldRenderMacros.java` (1263), `client/BlueprintRenderer.java` (669),
`client/BlueprintHandler.java`, `client/TagSubstitutionRenderer.java`,
`client/fakelevel/BlueprintBlockAccess.java`, `client/ChunkOffsetBufferBuilderWrapper.java`,
`client/model/**` (3), `client/RenderingCacheKey.java`, `client/BlueprintBlockInfoTransformHandler.java`,
`client/BlueprintEntityInfoTransformHandler.java`, `client/ModKeyMappings.java`,
`client/ClientItemStackTooltip.java`, `src/main/resources/assets/structurize/shaders/**`

### Агент C — GUI (фаза 4, после порта BlockUI)
`client/gui/*.java` (12), `client/gui/util/{InputFilters,ItemUtil}.java`,
`assets/structurize/gui/*.xml` (14), расшивка `GuiStubs`

### Не распределено (общее, редактирует оркестратор)
`client/gui/util/ItemPositionsStorage.java` — портируется агентом B2 вместе с `operations/`
(он от BlockUI не зависит и никому больше не нужен)

## Disabled content

_(пусто — заполняется по ходу порта)_

## Журнал фаз

| Фаза | Что | Статус |
|---|---|---|
| 0 | Тулчейн, `/opt/mc-src`, jar DO, каркас `26.2/` | **готово** |
| 1 | Агент A — фундамент | в работе |
| 2 | Агенты B1, B2, D параллельно | не начата |
| 3 | Интеграция, `runServer` | не начата |
| 4 | Агент C — GUI, после BlockUI | не начата |
| 5 | Проверка рендера заказчиком на живом клиенте | не начата |
