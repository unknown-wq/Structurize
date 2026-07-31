# PORT-GAPS — что отключено, деградировано или не проверено

Рабочий список на «потом». Заполняется оркестратором по отчётам агентов, по строке на каждый срез.
Правило заказчика: **зелёная сборка важнее полноты фич, но всё вырезанное должно быть
записано так, чтобы это можно было починить, не занимаясь археологией.**

Каждой строке здесь обязан соответствовать маркер в коде:
```java
// TODO(port-26.2): DISABLED — <причина одной строкой>
/* … оригинальный код нетронутым … */
```
Сверка: `grep -rn "TODO(port-26.2)" src | wc -l` == сумме колонки «маркеров».

Колонки:
- **Что видно в игре** — наблюдаемое следствие. Если следствия нет, так и писать («невидимо»).
- **Как чинить** — конкретная зацепка: какой класс, какой API 26.2, что проверить.
- **Приоритет** — 🔴 серверный геймплей · 🟡 клиентская визуалка · 🟢 совместимость/косметика.

**Счёт маркеров: 44** (сверено после фазы 3: сборка зелёная, `runServer` поднимает мир, ноль `ERROR`/`FATAL`).
🔴 нет ни одного — серверный геймплей не резался нигде.

---

## Отключённый контент

| # | Файл:строка | Марк. | Что отключено | Почему | Что видно в игре | Как чинить | Приоритет |
|---|---|---:|---|---|---|---|---|
| 1 | `client/gui/GuiStubs.java` (9 точек); из sourceSet исключены `client/gui/{Window*,Abstract*}.java` и `client/gui/util/{InputFilters,ItemUtil}.java` — 13 файлов, 4496 строк | 9 | Весь GUI мода: инструмент постройки, сканер, выбор формы, менеджер паков, тег-тул, undo/redo, замена блоков | BlockUI на 26.2 не был готов на момент фаз 1–2. Реализовывать подмножество нельзя: MineColonies зависит от той же библиотеки и требует 34 символа против наших 20 | Ни одно окно не открывается; инструменты по клику не делают ничего. Серверная часть — постановка, операции, undo/redo, сеть — работает полностью | **Разблокировано:** jar BlockUI подключён в фазе 3, покрывает все 20 символов. Фаза 4: убрать блок `exclude` из `build.gradle`, вернуть тела `GuiStubs`, портировать окна. XML-layout'ы (454 строки) не тронуты | 🟡 |
| 2 | `items/ItemScanTool.java:264`, `items/ItemTagSubstitution.java:127` | 2 | `IItemExtension#getHighlightTip` | NeoForge-расширение, ванильного аналога нет | Имя предмета над хотбаром не дописывает « - \<слот\>» / « - \<блок\>». Тот же текст остался в тултипе | Миксин на рендер имени предмета в `Gui` | 🟢 |
| 3 | `items/ItemBuildTool.java:65`, `items/ItemShapeTool.java:49` | 2 | `getCraftingRemainingItem` / `hasCraftingRemainingItem` | NeoForge-расширения; ванильный `craftRemainder(Item)` не умеет «вернуть тот же стек» | Билд-тул и шейп-тул в крафте расходуются. В моде рецепта с ними нет | Миксин на `ItemStack#getCraftingRemainingItem` | 🟢 |
| 4 | `blockentities/BlockEntityTagSubstitution.java:245` | 1 | `BlockEntity#removeComponentsFromTag(CompoundTag)` | Метода в 26.2 нет (0 вхождений) | Невидимо: ваниль сама вычищает component-ключи | Проверить, что тег `captured_block` не дублируется в NBT предмета | 🟢 |
| 5 | `event/ClientLifecycleSubscriber.java:79` | 1 | `ItemBlockRenderTypes.setRenderLayer(blockSubstitution, translucent)` | Класса в 26.2 нет, слой задаётся JSON-ом модели | **✅ ПОЧИНЕНО в фазе 3** — `"render_type": "translucent"` добавлен в `models/block/blocksubstitution.json:3`. Маркер оставлен как документация переезда | — | 🟢 |
| 6 | `event/ClientLifecycleSubscriber.java:86` | 1 | Регистрация `OverlaidModelLoader` | `IGeometryLoader` — NeoForge; сам лоадер тоже мёртв (строки 16–18) | Оверлей-модель тега не грузится | **ЗАКРЫТО ОКОНЧАТЕЛЬНО** — регистрировать некуда и нечего | 🟢 |
| 8 | `event/ClientLifecycleSubscriber.java:96` | 1 | `WorldRenderMacros.RenderTypes.registerBuffer` | `RegisterRenderBuffersEvent` — NeoForge; батчинг делает `SubmitNodeCollection` | Невидимо — в 26.2 буферов мода не существует | **ЗАКРЫТО ОКОНЧАТЕЛЬНО** | 🟢 |
| 9 | `event/ClientLifecycleSubscriber.java:102` | 1 | `IClientItemExtensions#getCustomRenderer` для `blockTagSubstitution` | NeoForge-only + `BlockEntityWithoutLevelRenderer` удалён | Предмет-заместитель в руке рисуется обычной моделью | Вместе со строкой 15 | 🟡 |
| 10 | `util/WorldRenderMacros.java:51` | 1 | `Stage` → свой enum, все 3 стадии стреляют подряд из одного `COLLECT_SUBMITS` | `RenderLevelStageEvent.Stage` — NeoForge; в 26.2 стадий нет, порядок задаёт `RenderType` | Возможные артефакты сортировки прозрачного | Если сортировка врёт — раскидать по `LevelRenderEvents.AFTER_OPAQUE_TERRAIN` / `AFTER_TRANSLUCENT_TERRAIN` | 🟡 |
| 11 | `util/WorldRenderMacros.java:125` | 1 | Ручной push модельно-видовой матрицы | `RenderSystem.applyModelViewMatrix` удалён | Невидимо | — | 🟢 |
| 12 | `util/WorldRenderMacros.java:156,168` | 2 | `pushShaderMvMatrixFromPose` / `popShaderMvMatrix` → no-op | То же | Невидимо, вызывающих нет | — | 🟢 |
| 13 | `util/WorldRenderMacros.java:1142` | 1 | `NEVER_DEPTH_TEST` → `CompareOp.NEVER_PASS` дословно | Старый шард звал `depthFunc(GL_NEVER)` | Невидимо: оба потребителя в Structurize не вызываются | Если MineColonies их использует — заменить на `ALWAYS_PASS` | 🟢 |
| 14 | `util/WorldRenderMacros.java:1174` | 1 | `RenderTypes.registerBuffer` / `finishBuffer` | `RegisterRenderBuffersEvent` — NeoForge | Невидимо | — | 🟢 |
| 15 | `client/TagSubstitutionRenderer.java:123` | 1 | `renderByItem` — предметный рендер якоря | `BlockEntityWithoutLevelRenderer` удалён, `IClientItemExtensions#getCustomRenderer` — NeoForge | Предмет в инвентаре рисует плоскую модель без захваченного блока внутри | `SpecialModelRenderer` через `"minecraft:special"` в `items/blocktagsubstitution.json` + `BuiltInBlockModelsCallback` | 🟡 |
| 16–18 | `client/model/Overlaid{BakedModel,Geometry,ModelLoader}.java` | 3 | Весь кастомный лоадер моделей `structurize:overlaid` | `BakedModel`, `BakedModelWrapper`, `IUnbakedGeometry`, `IGeometryLoader`, `ItemOverrides` — всё удалено (ровно строки 10/12 `PORT-GAPS.md` DO) | Блок якоря рисуется плоской родительской моделью; ключ `"loader"` в JSON ваниль игнорирует | `ModelLoadingPlugin.Context#modifyBlockModelAfterBake` из `fabric-model-loading-api-v1` | 🟢 |
| 19 | `client/ClientItemStackTooltip.java:51` | 1 | Шрифт предмета из `IClientItemExtensions#getFont` | NeoForge-only | Тултип всегда дефолтным шрифтом | — | 🟢 |
| 20 | `client/ModKeyMappings.java:22` | 1 | `IKeyConflictContext` / `BLUEPRINT_WINDOW` | NeoForge-only, у `KeyMapping` контекста нет | Кейбинды окна чертежа стали глобальными | Опрашивать `ModKeyMappings.isBlueprintWindowActive()` в обработчике — **агент C при расшивке BlockUI** | 🟡 |
| 21 | `client/ModKeyMappings.java:77` | 1 | `KeyModifier.SHIFT` у `ROTATE_CW/CCW` | Модификаторов в 26.2 нет | Поворот превью перевешен с `Shift+←/→` на `X` / `Z` | — | 🟡 |
| 22 | `client/ChunkOffsetBufferBuilderWrapper.java:13` | 1 | Класс не используется | Следствие деградации 6 | Невидимо | — | 🟢 |
| 23 | `assets/structurize/shaders/alpha.frag:2` | 1 | GLSL 120 шейдер | 26.2 — GLSL 450 + UBO bind groups, шейдер объявляется из `RenderPipeline` | Невидимо, из java не используется | Вместе с деградацией 5 | 🟢 |

## Функциональная деградация

| # | Файл:строка | Марк. | Что деградировало | Почему | Что видно в игре | Как чинить | Приоритет |
|---|---|---:|---|---|---|---|---|
| 1 | `compat/itemhandler/ItemHandlers.java:14`, `api/ItemStackUtils.java:106,206` | 3 | Поиск инвентарей через capability-API NeoForge — заменён ванильным `Container` | На Fabric capability-API нет, `fabric-transfer-api-v1` несовместим по семантике | «Требуемые предметы» для блока, чей инвентарь публикуется **только** модовой capability, выходят пустыми. Все ванильные контейнеры считаются полностью | Мост на `ItemStorage.SIDED` внутри `ItemHandlers` — фасад уже изолирует это в одном месте | 🟢 |
| 2 | `com.ldtteam.common.config.*` — **в jar-е BlockUI, маркеров в нашем дереве нет** | 0 | Конфиг **не персистится и не синхронизируется** | Контракт K4 порта BlockUI: `ModConfigSpec` мёртв, `Configurations` держит значения только в памяти. Наша копия из фазы 1 умела JSON, настоящая библиотека — нет | Настройки превью (прозрачность, свет, share) **сбрасываются при каждом запуске**; клиент на удалённом сервере видит свои значения `getServer()` | Чинить **один раз в BlockUI**: `ConfigValue#save()` + чтение/запись в `Configurations`. Тогда почини́тся и у MineColonies | 🟡 |
| 3 | `com.ldtteam.common.fakelevel.IFakeLevelLightProvider` — **в jar-е BlockUI** | 0 | `getShade` | В 26.2 нет `Level#getShade`; затенение уехало в `BlockModelLighter`. **Порт BlockUI сделал то же самое независимо** — значит это не наша ошибка | Превью затеняет грани по-ванильному, а не принудительно ровно | `/opt/mc-src/net/minecraft/client/renderer/block/BlockModelLighter.java` | 🟡 |
| 5 | `blueprints/v1/BlueprintUtils.java:47` (`instantiateTileEntities`) | 1 | Убран параметр `Map<BlockPos, ModelData>` | `net.neoforged.neoforge.client.model.data.ModelData` на Fabric не существует | Ничего — чисто клиентский рендер-путь | Канал 26.2 — `RenderDataBlockEntity#getRenderData()`, внешняя карта не нужна | 🟢 |
| 6 | `client/BlueprintRenderer.java:175` | 1 | **Жидкости в превью** | `BlockRenderDispatcher#renderLiquid`, `ItemBlockRenderTypes` удалены; `FluidRenderer` живёт внутри сборщика секций и фейк-левелу недоступен | Вода и лава в схематике не рисуются | Миксин на `FluidRenderer` либо генерация квадов вручную через `QuadEmitter` | 🟡 |
| 7 | `client/BlueprintRenderer.java:345` | 1 | `Lighting.setupLevel/setupNetherLevel`, `FogRenderer.setupFog/setupNoFog` | Сигнатур нет, туман — `GpuBufferSlice` уровня | Превью может не попадать под туман точно как ванильная геометрия | — | 🟢 |
| 8 | `client/BlueprintRenderer.java:396` | 1 | `Entity#noCulling` | Поля нет, решение у `EntityRenderer#affectedByCulling` (protected) | Сущность, торчащая далеко за AABB чертежа, может пропадать | AW на `affectedByCulling` | 🟢 |
| 9 | `client/BlueprintRenderer.java:465` | 1 | Per-BE фрустум-куллинг превью | `BlockEntityRenderer#getRenderBoundingBox` удалён | Невидимо (чуть больше работы) | — | 🟢 |
| 10 | `client/BlueprintRenderer.java:511` (`TransparencyHack`) | 1 | **Прозрачность превью** | `glBlendColor`, `GlStateManager.BLEND`, `RenderSystem.blendFunc/enableBlend` удалены; blend — свойство иммутабельного `RenderPipeline` | Превью рисуется непрозрачным, настройка `rendererTransparency` не работает | Свой `RenderPipeline` с `BlendFunction.TRANSLUCENT` + альфа в вершинный цвет через враппер `BlockStateModel` | 🟡 |
| 11 | **вся архитектура** `client/BlueprintRenderer.java` | 1 | Bake в `VertexBuffer` + свой шейдер → per-block `BlockModelRenderState#submit` | `VertexBuffer`, `ShaderInstance`, `Uniform.CHUNK_OFFSET`, `BakedModel`, `ModelData`, `MultiBufferSource` удалены | Превью «плоско освещено» (item-листы, без AO), **возможна просадка FPS на больших чертежах** — один submit на блок на кадр вместо одного draw-call на весь чертёж | Долгосрочно — свой сборщик секций поверх `SectionBufferBuilderPack` + `StagedVertexBuffer` | 🟡 |
| 12 | `client/TagSubstitutionRenderer.java:85` | 1 | Блок-сущность внутри якоря замены | `tryExtractRenderState` куллит по позиции камеры реального уровня и отбрасывает BE в `BlockPos.ZERO` фейк-левела | Сундук в якоре показан без анимированной крышки | Вручную `renderer.createRenderState()` + `extractRenderState(...)` минуя диспетчер | 🟡 |
| 13 | `client/TagSubstitutionRenderer.java:115` | 1 | `NeoForgeRenderTypes.ITEM_LAYERED_TRANSLUCENT` | Класса нет; лист выбирает `BlockModelRenderState#setupModel` | Замена внутри якоря больше не «слоёно-прозрачная» | — | 🟢 |

| 14 | `blueprints/v1/DataVersion.java:15,20` | 0 | Добавлен `v26_2(4903)`, промежуточные релизы 1.21.2…26.1.2 пропущены | Их номера ниоткуда не подтверждаются | Невидимо: цепочка нужна только для пошагового прохода `DataFixerUtils`, ванильный фиксер прыгает сразу | Дописать недостающие `DataVersion`, если найдётся источник номеров | 🟢 |
| 15 | `fabric.mod.json` **мода BlockUI** | 0 | `Unsupported root entry "credits"` | Схема 1 такого поля не знает | WARN в логе на каждом старте, загрузку не ломает | Перенести содержимое в `authors`/`contributors` в дереве BlockUI | 🟢 |

## Починено в фазе 3 (не гэпы — исправленные баги)

| Что | Симптом | Причина | Лечение |
|---|---|---|---|
| `blueprints/v1/DataVersion` | **Сервер не стартовал вообще**: `RuntimeException: You are trying to run old mod on much newer vanilla` из `Structurize.checkDataFixer()`, до регистрации чего-либо | Перечисление заканчивалось на `v1_21_1(3955)`/`UPCOMING(3956)`, а данные версии 26.2 — **4903** (`/opt/mc-src/net/minecraft/DetectedVersion.java:28`) | `v26_2(4903, "26.2", UPCOMING)` + `UPCOMING(4904)` |
| Все 6 рецептов мода | `Couldn't parse data file 'structurize:<recipe>' … No key fabric:type in MapLike[{"tag":"c:ingots/iron"}]` — рецептов в игре просто нет | Ингредиент в 26.2 — **строка**: `"minecraft:iron_ingot"` или `"#minecraft:logs"`, а не объект `{"item":…}` / `{"tag":…}` | Переписаны все ключи всех рецептов. Рецепты Structurize рукописные, а не датаген — `runDatagen` их бы не поймал |
| BER якоря тега | Якорь не показывал заменяемый блок | Писалось, пока `TagSubstitutionRenderer` был не портирован | `BlockEntityRendererRegistry.register(ModBlockEntities.TAG_SUBSTITUTION.get(), TagSubstitutionRenderer::new)` включён |
| Фиксер блупринтов 1.12.2 | `fixCross1343` был закомментирован целиком | `FLOWER_POT_MAP`/`NOTE_BLOCK_MAP` уехали в приватный `ChunkPalettedStorageFix$MappingConstants` | Три строки AccessWidener (класс + два поля), тело расшито |
| AccessWidener | Две мёртвые строки | `Frustum.cubeInFrustum(DDDDDD)I` заменён публичным `isVisible(AABB)`; `Camera.setPosition(Vec3)` не нужен | Удалены, сборка и `runServer` зелёные |

## Поведенческие решения (маркеров нет, но знать надо)

| Файл | Решение | Почему |
|---|---|---|
| `event/LifecycleSubscriber` | `ServerStructurePackLoader.onServerStarting()` повешен на `SERVER_STARTING` **под `server.isDedicatedServer()`** | На NeoForge его звал `FMLDedicatedServerSetupEvent`, который на встроенном сервере не срабатывает. Без проверки в одиночной игре серверный загрузчик пакетов стартует вторым экземпляром поверх клиентского |
| `management/Manager` | `player.displayClientMessage(msg, false)` → `player.sendSystemMessage(msg)` | Метод удалён; семантика (сообщение в чат) сохранена |
| `util/ChangeStorage` | `level.markAndNotifyBlock(...)` → `level.sendBlockUpdated(...)` | Метод был NeoForge. Оба call-site'а шли сразу после полноценного `setBlock(pos, state, UPDATE_FLAG)`, который уже делает рассылку и обновление соседей — поведение эквивалентно |
| `storage/rendering/types/BlueprintPreviewData` | `@OnlyIn(Dist.CLIENT)` → `@Environment(EnvType.CLIENT)`, **не удаление** | Fabric Loader физически вырезает помеченные члены. Без стриппинга загрузка класса на dedicated server кончается `NoClassDefFoundError` |
| `commands/AbstractCommand` | NeoForge `EnumArgument` → `StringArgumentType.word()` + `suggests(...)` + свой `getEnum(...)` | Свой `ArgumentType` потребовал бы `ArgumentTypeInfo` в `BuiltInRegistries.COMMAND_ARGUMENT_TYPE`, иначе дерево команд не сериализуется клиенту |
| `build.gradle` | Добавлен `testImplementation "junit:junit:4.13.2"` | `src/test` несёт один JUnit 4 тест; на 1.21.1 junit приходил из родительского NeoForge-скрипта, которого больше нет |

## Не проверено (и почему)

| Область | Что именно не проверено | Причина |
|---|---|---|
| `client/**` целиком | Рендер превью схематики, рамки, текст тегов, кейбинды, тултип | В контейнере нет дисплея. `runServer` клиентский код не исполняет. Установлена только чистая компиляция. **Проверку выполняет заказчик на живом клиенте (фаза 5).** Порядок проверки — ниже |
| AccessWidener | Что все перенесённые строки нужны | Loom валидирует существование члена, но не использование |
| Датаген | `runDatagen` ни разу не запускался | Оракул из `1.21.1/` скопирован в ресурсы, мод укомплектован контентом без него |
| `compat/common/fakelevel/FakeLevel` | Что 29 реализованных абстрактных методов ведут себя как на NeoForge | Компиляция проходит; поведение видно только на клиенте |

## Порядок проверки на живом клиенте (фаза 5)

По убыванию вероятности расхождения — всё ниже написано вслепую.

1. **Белая рамка превью и красная рамка якоря.** Это `submitCustomGeometry` + семь новых `RenderPipeline`. Симптом «мимо»: рамок не видно, или видно сквозь блоки. Причина №1 — **обратный depth-буфер 26.2**: отображено `LEQUAL → GREATER_THAN_OR_EQUAL`, `GREATER → LESS_THAN_OR_EQUAL`. Если глубина инвертирована — поменять эти два `CompareOp` местами в `WorldRenderMacros.RenderTypes`, одна строка на тип.
2. **Само превью схематики.** (а) рисуется ли вообще; (б) **FPS на схематике в 10k+ блоков**; (в) освещение — будет «плоским», без AO; (г) прозрачность не работает; (д) воды и лавы в превью нет.
3. **Текст тегов над блоками** (tag tool). `drawInBatch` → `submitText`, порядок аргументов другой — смотреть цвет и фон.
4. **Сущности и блок-сущности внутри превью** (сундуки, таблички, мобы). Извлечение состояния идёт в фазе `COLLECT_SUBMITS`, ваниль извлекает раньше.
5. **Якорь замены тега** в мире — должен показывать модель захваченного блока (после расшивки строки 7).
6. **Кейбинды**: категория покажется сырым ключом `key.category.structurize.general`, пока не добавлена строка в lang. `Rotate CW/CCW` перевешены с `Shift+←/→` на `X` / `Z`.
7. **Тултип со стаком** — переписан на `extractText`/`extractImage`, проверить выравнивание иконки и текста.

## Расхождения датагена с оракулом

Оракул — JSON в `src/datagen/generated/structurize/`, скопированные из `1.21.1/`.

_(пусто — заполняется после первого `runDatagen`)_
