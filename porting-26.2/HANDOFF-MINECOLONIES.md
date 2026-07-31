# Передача агенту MineColonies — что даёт порт Structurize на Fabric / MC 26.2

Документ для команды, которая портирует **MineColonies**. Отвечает на три вопроса: что уже
готово и на что можно опираться, где мы деградировали так, что это заденет вас, и на какие
грабли вы наступите, если не знать заранее.

Состояние Structurize на момент написания: `gradle build` зелёный, тест зелёный,
`runServer` поднимает мир за 0.5 с с нулём `ERROR` и `FATAL`. 201 java-файл, 0 миксинов.

---

## 1. Порядок портирования — он не обсуждается

```
BlockUI  +  Domum Ornamentum   (листья, внешних зависимостей нет)
        ↓
Structurize                    (готово)
        ↓
MineColonies                   (вы здесь)
```

Причина в одной находке, которая переворачивает наивный план: **отдельной библиотеки
`com.ldtteam.common` не существует.** Пакет физически лежит внутри BlockUI
(`src/main/java/com/ldtteam/common/**`, 33 файла: `network/`, `fakelevel/`, `codec/`, `config/`,
`language/`, `util/`). Искать и копировать нечего — она приезжает вместе с jar-ом BlockUI.

Для вас это значит: **209 импортов `com.ldtteam.common` в 140 файлах MineColonies закрываются
подключением BlockUI**, а не отдельной работой.

### Как подключать зависимости

```groovy
// Loom 1.17 на неообфусцированной игре НЕ создаёт конфигурации mod* — modImplementation
// не существует. Обычный implementation работает: Fabric Loader находит мод по
// fabric.mod.json на classpath.
implementation files("libs/blockui-0.0.1.jar")
implementation files("libs/domum_ornamentum-26.2-1.0.0.jar")
implementation files("libs/structurize-26.2-1.0.0.jar")
```
```json
"depends": { "blockui": "*", "domum_ornamentum": "*", "structurize": "*" }
```

Пины версий, проверенные на пяти портах: `minecraft 26.2`, `fabric-loader 0.19.3`,
`fabric-api 0.154.2+26.2`, `loom 1.17.13`, `gradle 9.6.1`, Java 25.
**Строки `mappings` нет вообще** — 26.1+ идёт неообфусцированным с именами Mojang.

---

## 2. Объём вашей работы — замерено, не оценено

| Что | Файлов MineColonies | Импортов |
|---|---:|---:|
| Всего java | 2061 | — |
| `com.ldtteam.blockui` | 146 | 466 |
| `com.ldtteam.structurize` | 147 | 397 |
| `com.ldtteam.common` | 140 | 209 |
| XML-layout'ов GUI | 94 | — |

Вам нужно **34 различных символа BlockUI** против наших 20. Дельта — `ZoomDragView`,
`SwitchView`, `ScrollingListContainer`, `Gradient`, `AtlasManager`, `Loader`, `PaneParams`,
`BOGuiGraphics`, `UiRenderMacros`, `Alignment`, `AbstractTextBuilder`, весь `util.color`.

---

## 3. Публичный API Structurize, на который можно опираться

Всё ниже — стабильный контракт, менять не будем без предупреждения.

### 3.1 Перечисление data-компонентов

```java
package com.ldtteam.structurize.component;

public static Collection<DataComponentType<?>> all()
```
Замена ушедшему `DeferredRegister.getEntries()`. Для `ItemNbtCalculator`:
```java
// было (NeoForge):
// ModDataComponents.REGISTRY.getEntries().forEach(t -> typesToRemove.add(t.get()));
ModDataComponents.all().forEach(typesToRemove::add);
```
Коллекция наполняется **внутри приватной фабрики** `savedSynced(...)`, а не рукописным списком:
пятый компонент попадёт в `all()` самим фактом объявления, правок на вашей стороне не потребуется.
Возвращается неизменяемое представление, порядок — порядок объявления полей.

### 3.2 Отрисовка: свои `RenderType` без `RenderStateShard`

```java
package com.ldtteam.structurize.util;   // WorldRenderMacros.RenderTypes

public static RenderPipeline createPipeline(Identifier location, PrimitiveTopology topology,
        BlendFunction blend, CompareOp depthTest, boolean writeDepth, boolean cull)

public static RenderType createRenderType(String name, Identifier location, PrimitiveTopology topology,
        BlendFunction blend, CompareOp depthTest, boolean writeDepth, boolean cull)
```
Прямая замена наследованию `AlwaysDepthTestStateShard`:
```java
public static final RenderType NO_DEPTH_LINES = WorldRenderMacros.RenderTypes.createRenderType(
    "minecolonies_no_depth_lines",
    Identifier.fromNamespaceAndPath("minecolonies", "pipeline/no_depth_lines"),
    PrimitiveTopology.TRIANGLES, BlendFunction.TRANSLUCENT,
    CompareOp.ALWAYS_PASS,        // ← «без depth-теста»
    /* writeDepth */ true, /* cull */ true);
```
Пайплайн наследует `RenderPipelines.DEBUG_FILLED_SNIPPET` — свой `.glsl` писать не нужно.

Готовые публичные константы: `LINES`, `LINES_WITH_WIDTH`, `LINES_WITH_WIDTH_DEPTH_INVERT`,
`GLINT_LINES`, `GLINT_LINES_WITH_WIDTH`, `COLORED_TRIANGLES`, `COLORED_TRIANGLES_NC_ND`.

**⚠️ Обратный depth-буфер 26.2** — главная ловушка рендера:

| 1.21.1 | 26.2 | смысл |
|---|---|---|
| `LEQUAL_DEPTH_TEST` / `GL_LEQUAL` | `CompareOp.GREATER_THAN_OR_EQUAL` | обычная отрисовка, перекрывается миром |
| `GREATER_DEPTH_TEST` / `GL_GREATER` | `CompareOp.LESS_THAN_OR_EQUAL` | инверсия, видна только скрытая часть |
| `AlwaysDepthTestStateShard` / `GL_ALWAYS` | `CompareOp.ALWAYS_PASS` | **без depth-теста**, поверх всего |
| `NeverDepthTestStateShard` / `GL_NEVER` | `CompareOp.NEVER_PASS` | не проходит никогда |

Подтверждение: `/opt/mc-src/com/mojang/blaze3d/pipeline/DepthStencilState.java:11` —
`DEFAULT = new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true)`.
`ALWAYS_PASS`/`NEVER_PASS` реверс не затрагивает — они игнорируют значение depth целиком.

Остальные шарды: `setTransparencyState` → `BlendFunction`, `setCullState` → `cull`,
`setWriteMaskState(COLOR_WRITE / COLOR_DEPTH_WRITE)` → `writeDepth = false / true`,
`VertexFormat.Mode` → `PrimitiveTopology`.

### 3.3 `Tuple`

```java
com.ldtteam.structurize.api.Tuple<A, B>     // НЕ compat.util.Tuple
```
`net.minecraft.util.Tuple` в 26.2 удалён (0 вхождений `class Tuple` в `/opt/mc-src`).
Форма `getA()` / `getB()` фиксирована. Встретится в `IBlueprintDataProviderBE.getSchematicCorners()`,
`IPlacementHandler.doesWorldStateMatchBlueprintState()` и обоих DO-плейсмент-хендлерах.

### 3.4 `WorldRenderMacros.Stage` — компилируется, но семантика другая

`renderWithinContext(Stage)` — `protected abstract`, ваш `ColonyBlueprintRenderer` обязан его
реализовать. **Ловушка:** `RenderLevelStageEvent.Stage` в 26.2 не существует, стадий нет вообще.
Наш enum сохранён ради call-site'ов, но все три стадии стреляют **подряд из одного**
`LevelRenderEvents.COLLECT_SUBMITS`, а порядок отрисовки задаёт `RenderType`, а не стадия.
Код, полагавшийся на порядок стадий, скомпилируется и будет работать иначе.

### 3.5 Ещё не решено — скажите своё мнение

`com.ldtteam.structurize.compat.itemhandler.IItemHandler` (наша замена capability-API NeoForge)
**уже течёт в публичные сигнатуры**: `IStructureHandler.getInventory()`,
`ItemStackUtils.deepExtractItemHandler(...)`, `ItemStackUtils.getItemHandlersFromProvider(...)`.
Вы обязаны реализовать `IStructureHandler` для своих строительных хендлеров, то есть назвать
этот тип по имени — а живёт он в пакете `compat/`, что означает «временная прокладка порта».

Варианты: перенести в `com.ldtteam.structurize.api.itemhandler` (для нас ~5 файлов импортов),
либо согласовать общий дом в BlockUI. **Если вы изобретёте своё — получим две несовместимые
иерархии.** Напишите, что удобнее.

---

## 4. Деградации, которые заденут вас

Полный список — `26.2/PORT-GAPS.md`, 37 маркеров в коде. Ниже только то, что видно снаружи.

### 🔴 Ничего. Серверный геймплей не резался нигде.

### 🟡 Конфиг не персистится и не синхронизируется

Контракт K4 порта BlockUI: `ModConfigSpec` мёртв, `Configurations` держит значения **только
в памяти**. Настройки сбрасываются при каждом запуске; клиент на удалённом сервере видит свои
локальные значения `getServer()`.

У вас конфигов на порядок больше, чем у нас, и это будет заметнее. **Чинить надо один раз
в BlockUI** (`ConfigValue#save()` + чтение/запись в `Configurations`) — тогда починится у всех.

### 🟡 Поиск инвентарей через capability-API молча деградировал

`ItemStackUtils.getItemHandlersFromProvider(...)` компилируется, но на Fabric capability-API нет,
и инвентари ищутся только через ванильный `Container`. Для блока, публикующего инвентарь
**исключительно** модовой capability, список выйдет **пустым, без ошибки**. Вы строите на этом
«required items» — знать лучше заранее.

Все ванильные контейнеры (сундуки, бочки, шалкеры, воронки, печи, рамки) считаются полностью.

### 🟡 Превью схематики: четыре потери

Наш `BlueprintRenderer` переписан с bake-в-`VertexBuffer` на per-block
`BlockModelRenderState#submit`, потому что `VertexBuffer`, `ShaderInstance`, `BakedModel`,
`MultiBufferSource` и `Uniform.CHUNK_OFFSET` в 26.2 удалены целиком:

1. **прозрачность не работает** — `glBlendColor` мёртв, blend стал свойством иммутабельного
   `RenderPipeline`;
2. **воды и лавы в превью нет** — `FluidRenderer` живёт внутри сборщика секций и фейк-левелу
   недоступен;
3. **превью плоско освещено** — идёт через item-листы (`cutoutBlockItemSheet`), без ambient
   occlusion и затенения граней от соседей;
4. **возможна просадка FPS на больших чертежах** — один submit на блок на кадр вместо одного
   draw-call на весь чертёж. На 10k+ блоках стоит померить.

Если вам нужен другой компромисс — это место общее, обсудим.

### 🟢 Мелочи

- Кейбинды: `KeyModifier` и `IKeyConflictContext` — NeoForge-only, аналогов нет. Кейбинд с
  модификатором (`Shift+←`) выразить нечем; контекст конфликта восстанавливается только
  поведенчески, декларативная половина (подсветка конфликтов в настройках управления) — никак.
- `IItemExtension#getHighlightTip`, `getCraftingRemainingItem`, `IClientItemExtensions#getFont`
  и `getCustomRenderer` — NeoForge-расширения без ванильных аналогов, нужны миксины.
- Кастомные лоадеры моделей (`IGeometryLoader`, `IUnbakedGeometry`, `BakedModel`) удалены
  целиком; замена — `ModelLoadingPlugin.Context#modifyBlockModelAfterBake` из
  `fabric-model-loading-api-v1`.

---

## 5. Грабли 26.2, на которых мы уже наступили

Полные разборы с построчными ссылками — в `porting-26.2/FINDINGS-*.md`. Здесь — то, что ловится
не компилятором, а только запуском или глазами.

### Ломает старт сервера

- **`DataVersion` мода.** Если мод сверяется с `DataFixers.getDataFixer()` / `SharedConstants`,
  он падает **до** регистрации чего-либо. Данные версии 26.2 — **4903**
  (`/opt/mc-src/net/minecraft/DetectedVersion.java:28`). Грепните
  `getDataFixer\|SharedConstants.getCurrentVersion` **до** первого `runServer`.
- **`@OnlyIn(Dist.CLIENT)` нельзя просто удалять** → `@Environment(EnvType.CLIENT)`. Fabric Loader
  физически вырезает помеченные члены; без этого загрузка класса с клиентскими типами в теле
  на dedicated server кончается `NoClassDefFoundError`.

### Компилируется, но молча не работает

- **Ингредиенты рецептов стали строками.** `{"tag": "c:ingots/iron"}` → `"#c:ingots/iron"`,
  `{"item": "minecraft:paper"}` → `"minecraft:paper"`. Ошибка сообщает только **первый** сбойный
  ключ в файле. У рукописных рецептов датаген это не поймает.
- **`Pane#onKeyTyped(char, int)` на уровне окна больше не вызывается**, но остался в `Pane` как
  deprecated — `@Override` компилируется, IDE молчит, метод мёртв. Заменяется на **два** события:
  `onKeyEvent(KeyEvent)` и `onCharactedEvent(CharacterEvent)`. Грепните `onKeyTyped` явно.
- **Текст с нулевой альфой не рисуется вообще.** `GuiGraphicsExtractor#text` начинается с
  `if (ARGB.alpha(color) != 0)`, а `Font.drawInBatch` больше не дописывает альфу автоматически.
  `ChatFormatting.BLACK.getColor()` даёт ровно такой цвет → **невидимые надписи на кнопках**.
  Лечение: `ARGB.opaque(TextColor.BLACK.getValue())`. Грепните `setTextColor|setColors|drawString`.
  При этом `ChatFormatting` в 26.2 цвет вообще не хранит — таблица уехала в
  `net.minecraft.network.chat.TextColor`.
- **`Registry#get(Identifier)` теперь возвращает `Optional<Holder.Reference<T>>`.** Старое
  поведение — `getValue(...)`. Самая тихая ловушка: `get` компилируется в `var`, и `null`-проверка
  превращается в проверку непустого `Optional`, которая всегда истинна.
- **`getMaxBuildHeight()` был исключающим, `getMaxY()` — включающий.** Любое
  `getMaxBuildHeight() - 1` превращается в просто `getMaxY()`. То же у
  `IFakeLevelBlockGetter#getMaxX/getMaxZ` — там javadoc врёт, говоря «exclusive»; смотрите на
  `isPosInside`, он сравнивает через `<=`.

### Массовые механические замены

| Было | Стало |
|---|---|
| `ResourceLocation` | `net.minecraft.resources.Identifier`, фабрики `fromNamespaceAndPath` / `parse` / `withDefaultNamespace`. **`Identifier.of` не существует** |
| `net.minecraft.util.Tuple` | удалён |
| `InteractionResultHolder` | удалён; `InteractionResult` — sealed interface, `switch` по константам не компилируется, нужен `instanceof` |
| `CompoundTag.getInt/getString/getCompound/getList` | вернули `Optional`; старая форма — `getIntOr(k, def)`, `getStringOr`, `getCompoundOrEmpty`, `getListOrEmpty`. `getAllKeys()` → `keySet()` |
| `BlockEntity` NBT | `loadAdditional(ValueInput)` / `saveAdditional(ValueOutput)`. Сырой `CompoundTag` оттуда не достать — мост для legacy-кода: `output.store(k, CompoundTag.CODEC, tag)` |
| `Entity#save/load`, `EntityType.by` | через `TagValueInput.create(ProblemReporter.DISCARDING, registryAccess, tag)`; `type.create(level, EntitySpawnReason.LOAD)` и результат **`@Nullable`** |
| `Level.isClientSide` | приватное поле → `level.isClientSide()` |
| `ResourceKey#location()` | `identifier()` |
| `RegistryAccess#registryOrThrow` | `lookupOrThrow` |
| `Player#displayClientMessage(c, bool)` | `sendSystemMessage(c)` (чат) **или** `sendOverlayMessage(c)` (над хотбаром) — флаг стал выбором метода |
| `Entity#moveTo` | `snapTo` |
| `ChunkPos.x` / `.z` | record → `x()` / `z()` |
| `DirectionProperty` | `EnumProperty<Direction>`; различать `property.getValueClass() == Direction.class` |
| `ItemStack#getDescriptionId()` | `stack.getItem().getDescriptionId()` |
| `Inventory#getSelected()` / `.items` | `getSelectedItem()` / `getNonEquipmentItems()` |
| `Minecraft#screen` | `Minecraft.getInstance().gui.screen()` |
| `LevelRenderer#setBlocksDirty` | `Minecraft#levelExtractor.setBlocksDirty(...)` |
| `Blocks.PINK_GLAZED_TERRACOTTA` | `Blocks.GLAZED_TERRACOTTA.pick(DyeColor.PINK)` — `ColorCollection<Block>` |
| `DimensionType#ultraWarm()` | `level.environmentAttributes().getDimensionValue(EnvironmentAttributes.WATER_EVAPORATES)` |

### Сеть на `com.ldtteam.common` — три отличия от NeoForge-версии

1. `registerClientReceivers()` больше нет → `ModNetworking.registerClient()` из клиентского
   entrypoint. `PlayMessageType#register()` сам вешает клиентский приёмник.
2. `context.isClientSide()` → `context.flow()` (`PacketFlow`); `enqueueWork` возвращает
   `CompletableFuture<Void>`.
3. **Отправка «не в ту сторону» больше не компилируется**: `IServerboundDistributor` (1 метод) и
   `IClientboundDistributor` (8 методов, включая `sendToDimension`, `sendToTrackingEntity`,
   `sendToPlayersTrackingChunk`) — интерфейсы-миксины на соответствующих базовых классах.
   Все девять целей `PacketDistributor` уже есть, изобретать не надо.
4. `ModNetworking.register()` из своего entrypoint звать **не надо** — BlockUI делает это сам.

### Инструменты

- **AccessWidener**: `fabric-transitive-access-wideners-v1` расширяет **519** членов ещё до вашего
  файла. Прежде чем чинить `has protected access`, посмотрите в `/opt/mc-src` — если над членом
  стоит javadoc «Access widened by fabric-transitive-access-wideners-v1», чинить нечего.
  Формат AW у Fabric 26.2 — `.classtweaker`, не `.accesswidener`.
- **Проверка типов без Gradle**: гоняйте javac по ремапнутому jar'у из `loom-cache` проекта
  (`.gradle/loom-cache/minecraftMaven/**`, берите самый новый по mtime) — там AW уже применён,
  ложных срабатываний нет. Готовый скрипт — `porting-26.2/typecheck.sh`.
- **`runServer` и stdin**: Loom не пробрасывает stdin, `printf 'stop\n' | gradle runServer` не
  работает. Команды на dev-сервере — только через RCON (`enable-rcon=true` в
  `run/server.properties`). Что проверять: `help <modid>` (дерево команд сериализовалось),
  `summon` (предмет зарегистрирован, `setId` не забыт), `forceload add` + `setblock` (блок
  зарегистрирован), `data get block` (блок-сущность сохраняется).
- Убить зависший сервер — `pkill -f "[d]evlaunch"`, скобка обязательна.
- `timeout N gradle runServer | tee` съедает лог целиком — пишите прямо в файл.

---

## 6. Где лежит остальное знание

| Файл | О чём |
|---|---|
| `26.2/PORT-STATUS.md` | Контракты C1–C10, тулчейн, пофайловые зоны, наследие каждой фазы |
| `26.2/PORT-GAPS.md` | Все 37 деградаций: что видно в игре, как чинить, приоритет |
| `porting-26.2/FINDINGS-A.md` | Реестры, NBT, предметы и блоки, сборка, AccessWidener |
| `porting-26.2/FINDINGS-B1.md` | Сеть, события Fabric, команды, `ListTag`, HUD |
| `porting-26.2/FINDINGS-B2.md` | Мир, `ValueInput`/`ValueOutput`, `FakePlayer`, `@Environment` |
| `porting-26.2/FINDINGS-D.md` | **Рендер 26.2 целиком** — самая дефицитная область |
| `porting-26.2/FINDINGS-C.md` | GUI на BlockUI, ловушки ввода и цвета текста |
| `porting-26.2/FINDINGS-INTEGRATION.md` | Настоящая `com.ldtteam.common` против восстановленной, RCON |
| `porting-26.2/PORT-ANY-MOD-26.2.md` | Общий плейбук порта на 26.2 |

Все находки с подтверждением построчной ссылкой на `/opt/mc-src` или на портированный мод
на диске. Правило, которое себя оправдало: **ни одной сигнатуры по памяти** — либо grep по
декомпилированным исходникам, либо строка из уже портированного мода.

---

## 7. Что не проверено на живом клиенте

В контейнере нет дисплея, `runServer` клиентский код не исполняет. Весь рендер и GUI написаны
вслепую и ждут ручной проверки. Порядок проверки, отсортированный по вероятности расхождения,
лежит в `26.2/PORT-GAPS.md`, раздел «Порядок проверки на живом клиенте» — 7 пунктов по рендеру
и 9 по окнам. Если найдёте расхождения раньше нас — пишите, поправим у себя.
