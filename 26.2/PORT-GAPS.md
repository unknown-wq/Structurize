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

**Счёт маркеров: 48** (сверено после фазы 2, сборка зелёная).
🔴 нет ни одного — серверный геймплей не резался нигде.

---

## Отключённый контент

| # | Файл:строка | Марк. | Что отключено | Почему | Что видно в игре | Как чинить | Приоритет |
|---|---|---:|---|---|---|---|---|
| 1 | `client/gui/GuiStubs.java` (9 точек); из sourceSet исключены `client/gui/{Window*,Abstract*}.java` и `client/gui/util/{InputFilters,ItemUtil}.java` — 13 файлов, 4496 строк | 9 | Весь GUI мода: инструмент постройки, сканер, выбор формы, менеджер паков, тег-тул, undo/redo, замена блоков | BlockUI на 26.2 не был готов на момент фаз 1–2. Реализовывать подмножество нельзя: MineColonies зависит от той же библиотеки и требует 34 символа против наших 20 | Ни одно окно не открывается; инструменты по клику не делают ничего. Серверная часть — постановка, операции, undo/redo, сеть — работает полностью | **Разблокировано:** порт BlockUI приехал, `26.2/libs/blockui-0.0.1.jar` собран и покрывает все 20 символов. Фаза 4: убрать блок `exclude` из `build.gradle`, вернуть тела `GuiStubs`, портировать окна. XML-layout'ы (454 строки) не тронуты | 🟡 |
| 2 | `items/ItemScanTool.java:264`, `items/ItemTagSubstitution.java:127` | 2 | `IItemExtension#getHighlightTip` | NeoForge-расширение, ванильного аналога нет | Имя предмета над хотбаром не дописывает « - \<слот\>» / « - \<блок\>». Тот же текст остался в тултипе | Миксин на рендер имени предмета в `Gui` | 🟢 |
| 3 | `items/ItemBuildTool.java:65`, `items/ItemShapeTool.java:49` | 2 | `getCraftingRemainingItem` / `hasCraftingRemainingItem` | NeoForge-расширения; ванильный `craftRemainder(Item)` не умеет «вернуть тот же стек» | Билд-тул и шейп-тул в крафте расходуются. В моде рецепта с ними нет | Миксин на `ItemStack#getCraftingRemainingItem` | 🟢 |
| 4 | `blockentities/BlockEntityTagSubstitution.java:245` | 1 | `BlockEntity#removeComponentsFromTag(CompoundTag)` | Метода в 26.2 нет (0 вхождений) | Невидимо: ваниль сама вычищает component-ключи | Проверить, что тег `captured_block` не дублируется в NBT предмета | 🟢 |
| 5 | `event/ClientLifecycleSubscriber.java:76` | 1 | `ItemBlockRenderTypes.setRenderLayer(blockSubstitution, translucent)` | Класса в 26.2 нет, слой задаётся JSON-ом модели | Блок-заместитель рисуется непрозрачным | `"render_type": "translucent"` в `assets/structurize/models/block/blocksubstitution.json` | 🟢 |
| 6 | `event/ClientLifecycleSubscriber.java:83` | 1 | Регистрация `OverlaidModelLoader` | `IGeometryLoader` — NeoForge; сам лоадер тоже мёртв (строки 16–18) | Оверлей-модель тега не грузится | Вместе со строками 16–18 | 🟢 |
| 7 | `event/ClientLifecycleSubscriber.java:89` | 1 | BER для `ModBlockEntities.TAG_SUBSTITUTION` | Писалось, пока `TagSubstitutionRenderer` был не портирован | Якорь тега в мире не показывает заменяемый блок | **Расшить в фазе 3:** D закончил рендерер, строка `BlockEntityRendererRegistry.register(ModBlockEntities.TAG_SUBSTITUTION.get(), TagSubstitutionRenderer::new)` проверена javac и лежит готовой в комментарии | 🟡 |
| 8 | `event/ClientLifecycleSubscriber.java:96` | 1 | `WorldRenderMacros.RenderTypes.registerBuffer` | `RegisterRenderBuffersEvent` — NeoForge; батчинг делает `SubmitNodeCollection` | Невидимо — в 26.2 буферов мода не существует | Закрыто окончательно, убрать при уборке | 🟢 |
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
| 2 | `compat/common/config/Configurations.java:33` | 1 | Синхронизация серверного конфига на клиент | На Fabric нет `ModConfigSpec` типа SERVER с автосинком при логине | Клиент на удалённом сервере видит **свои локальные** значения `getServer()`. Решения принимаются на сервере и корректны — расходится только показ | Конфиг-пейлоад в `PlayMessageType`, слать при логине | 🟢 |
| 3 | `compat/common/fakelevel/FakeLevel.java:59` | 1 | `IFakeLevelLightProvider#getShade` | В 26.2 нет `Level#getShade`; затенение уехало в `BlockModelLighter` | Превью затеняет грани по-ванильному, а не принудительно ровно | `/opt/mc-src/net/minecraft/client/renderer/block/BlockModelLighter.java` | 🟡 |
| 4 | `blueprints/v1/BlueprintUtil.java:509` (`fixCross1343`) | 1 | Тело закомментировано целиком | `ChunkPalettedStorageFix.FLOWER_POT_MAP` / `NOTE_BLOCK_MAP` уехали в приватный вложенный `ChunkPalettedStorageFix$MappingConstants` | Блупринты **1.12.2** сохраняют в палитре `POTTED_CACTUS` / `NOTE_BLOCK` вместо правильного горшка и настроенного нотного блока; остальной 1343-фиксер работает | Три строки AccessWidener (`accessible class …$MappingConstants` + два поля) → раскомментировать | 🟢 |
| 5 | `blueprints/v1/BlueprintUtils.java:47` (`instantiateTileEntities`) | 1 | Убран параметр `Map<BlockPos, ModelData>` | `net.neoforged.neoforge.client.model.data.ModelData` на Fabric не существует | Ничего — чисто клиентский рендер-путь | Канал 26.2 — `RenderDataBlockEntity#getRenderData()`, внешняя карта не нужна | 🟢 |
| 6 | `client/BlueprintRenderer.java:175` | 1 | **Жидкости в превью** | `BlockRenderDispatcher#renderLiquid`, `ItemBlockRenderTypes` удалены; `FluidRenderer` живёт внутри сборщика секций и фейк-левелу недоступен | Вода и лава в схематике не рисуются | Миксин на `FluidRenderer` либо генерация квадов вручную через `QuadEmitter` | 🟡 |
| 7 | `client/BlueprintRenderer.java:345` | 1 | `Lighting.setupLevel/setupNetherLevel`, `FogRenderer.setupFog/setupNoFog` | Сигнатур нет, туман — `GpuBufferSlice` уровня | Превью может не попадать под туман точно как ванильная геометрия | — | 🟢 |
| 8 | `client/BlueprintRenderer.java:396` | 1 | `Entity#noCulling` | Поля нет, решение у `EntityRenderer#affectedByCulling` (protected) | Сущность, торчащая далеко за AABB чертежа, может пропадать | AW на `affectedByCulling` | 🟢 |
| 9 | `client/BlueprintRenderer.java:465` | 1 | Per-BE фрустум-куллинг превью | `BlockEntityRenderer#getRenderBoundingBox` удалён | Невидимо (чуть больше работы) | — | 🟢 |
| 10 | `client/BlueprintRenderer.java:511` (`TransparencyHack`) | 1 | **Прозрачность превью** | `glBlendColor`, `GlStateManager.BLEND`, `RenderSystem.blendFunc/enableBlend` удалены; blend — свойство иммутабельного `RenderPipeline` | Превью рисуется непрозрачным, настройка `rendererTransparency` не работает | Свой `RenderPipeline` с `BlendFunction.TRANSLUCENT` + альфа в вершинный цвет через враппер `BlockStateModel` | 🟡 |
| 11 | **вся архитектура** `client/BlueprintRenderer.java` | 1 | Bake в `VertexBuffer` + свой шейдер → per-block `BlockModelRenderState#submit` | `VertexBuffer`, `ShaderInstance`, `Uniform.CHUNK_OFFSET`, `BakedModel`, `ModelData`, `MultiBufferSource` удалены | Превью «плоско освещено» (item-листы, без AO), **возможна просадка FPS на больших чертежах** — один submit на блок на кадр вместо одного draw-call на весь чертёж | Долгосрочно — свой сборщик секций поверх `SectionBufferBuilderPack` + `StagedVertexBuffer` | 🟡 |
| 12 | `client/TagSubstitutionRenderer.java:85` | 1 | Блок-сущность внутри якоря замены | `tryExtractRenderState` куллит по позиции камеры реального уровня и отбрасывает BE в `BlockPos.ZERO` фейк-левела | Сундук в якоре показан без анимированной крышки | Вручную `renderer.createRenderState()` + `extractRenderState(...)` минуя диспетчер | 🟡 |
| 13 | `client/TagSubstitutionRenderer.java:115` | 1 | `NeoForgeRenderTypes.ITEM_LAYERED_TRANSLUCENT` | Класса нет; лист выбирает `BlockModelRenderState#setupModel` | Замена внутри якоря больше не «слоёно-прозрачная» | — | 🟢 |

## Поведенческие решения (маркеров нет, но знать надо)

| Файл | Решение | Почему |
|---|---|---|
| `event/LifecycleSubscriber` | `ServerStructurePackLoader.onServerStarting()` повешен на `SERVER_STARTING` **под `server.isDedicatedServer()`** | На NeoForge его звал `FMLDedicatedServerSetupEvent`, который на встроенном сервере не срабатывает. Без проверки в одиночной игре серверный загрузчик пакетов стартует вторым экземпляром поверх клиентского |
| `management/Manager` | `player.displayClientMessage(msg, false)` → `player.sendSystemMessage(msg)` | Метод удалён; семантика (сообщение в чат) сохранена |
| `util/ChangeStorage` | `level.markAndNotifyBlock(...)` → `level.sendBlockUpdated(...)` | Метод был NeoForge. Оба call-site'а шли сразу после полноценного `setBlock(pos, state, UPDATE_FLAG)`, который уже делает рассылку и обновление соседей — поведение эквивалентно |
| `storage/rendering/types/BlueprintPreviewData` | `@OnlyIn(Dist.CLIENT)` → `@Environment(EnvType.CLIENT)`, **не удаление** | Fabric Loader физически вырезает помеченные члены. Без стриппинга загрузка класса на dedicated server кончается `NoClassDefFoundError` |
| `commands/AbstractCommand` | NeoForge `EnumArgument` → `StringArgumentType.word()` + `suggests(...)` + свой `getEnum(...)` | Свой `ArgumentType` потребовал бы `ArgumentTypeInfo` в `BuiltInRegistries.COMMAND_ARGUMENT_TYPE`, иначе дерево команд не сериализуется клиенту |
| `build.gradle` | Добавлен `testImplementation "junit:junit:4.13.2"` | `src/test` несёт один JUnit 4 тест; на 1.21.1 junit приходил из родительского NeoForge-скрипта, которого больше нет |

## Мёртвые строки AccessWidener (можно убрать, вреда нет)

| Строка | Почему мертва |
|---|---|
| `Frustum.cubeInFrustum(DDDDDD)I` | Заменён публичным `frustum.isVisible(AABB)`; сам метод к тому же возвращает `int`, а не `boolean` |
| `Camera.setPosition(Vec3)` | Поддельная камера больше не нужна: `BlockEntityRenderDispatcher.prepare(Vec3)` берёт позицию напрямую |

## Не проверено (и почему)

| Область | Что именно не проверено | Причина |
|---|---|---|
| `client/**` целиком | Рендер превью схематики, рамки, текст тегов, кейбинды, тултип | В контейнере нет дисплея. `runServer` клиентский код не исполняет. Установлена только чистая компиляция. **Проверку выполняет заказчик на живом клиенте (фаза 5).** Порядок проверки — ниже |
| AccessWidener | Что все перенесённые строки нужны | Loom валидирует существование члена, но не использование |
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
