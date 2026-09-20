# MineAI 阶段 0-1：Forge 假玩家与动作系统设计

> 状态：阶段 0-1 已实现并在开发服务器上跑通；`javac` 对真实 Forge jar 零错误零警告，ForgeGradle 构建产出 `mineai-0.1.0.jar`
> 目标版本：Minecraft 1.20.1
> 目标加载器：Forge 1.20.1-47.2.0（official mappings）
> Java：17
> 当前阶段：不接入 AI，使用硬编码动作序列验证服务端闭环
>
> 后续阶段见 `docs/阶段2_LLM决策循环设计.md`（工具层 + LLM 决策循环，已实现并用 mock provider 验证）。

## 0. 实现状态（2026-09-18）

工程根目录：`D:\ximu\MineAI`。基础包名 `com.mineai`，mod id `mineai`。

| 设计任务 | 状态 | 主要文件 |
| --- | --- | --- |
| A Forge 工程骨架 | 完成 | `build.gradle`、`gradle.properties`、`settings.gradle`、`src/main/resources/META-INF/mods.toml` |
| B NPC 生命周期 | 完成 | `entity/AgentPlayer`、`entity/AgentPlayerManager`、`entity/NpcGamePacketListener` |
| C Waypoint 移动 | 完成 | `movement/NpcMover`、`movement/WaypointMover`、`movement/NpcCollision`、`movement/MoveState` |
| D 方块动作 | 完成 | `action/MineAction`、`action/PlaceAction`、`action/UseAction`、`action/ActionQueue` |
| E 状态采集 | 完成 | `state/WorldStateCollector`、`util/RegistryIds` |
| F 原版机器适配器 | 完成基础架构 | `integration/*`、`integration/vanilla/VanillaContainerAdapter` |
| 命令入口 | 完成 | `command/NPCCommand`、`command/NpcTestScript` |

### 0.1 实机验证结果（Forge 1.20.1-47.2.0 开发服务器，RCON 驱动）

已在 `gradle runServer` 上实际运行并确认：

| 验收项 | 结果 |
| --- | --- |
| 模组加载 | `mineai` 被 FML 加载，Gametest namespace 注册成功 |
| `/npc spawn` | 成功，NPC 进入 `PlayerList`（`npc list` 可查到 UUID/维度/坐标） |
| 5x5 矩形移动 | 4 段 `MoveAction` 全部 `SUCCESS`，位置按预期推进 |
| 挖掘 | `MineAction(0,-61,-1)` `SUCCESS`，`grass_block` 被移除 |
| 放置 | `PlaceAction(0,-61,-1)` `SUCCESS`，该位置变为 `dirt`，背包泥土 64→63 |
| 状态采集 | `/npc state` 输出合法 JSON，使用注册表 ID，含附近掉落物与环境 |

尚未完成、留待后续阶段的项：

- 真实多人客户端画面可见性（需要真人客户端连接观察；服务端侧 `PlayerList` 与实体同步已确认）。
- 渐进式挖掘 `ProgressiveMineAction`。
- A* 路径规划替换 `WaypointMover`。
- Mekanism / AE2 / Create 等具体模组适配器。
- AI 工具调用层。

构建方式（本机已验证）：

```text
gradle compileJava   # 首次约 1 分 18 秒（含守护进程启动），之后数秒
gradle build         # 约 5-8 秒，产出 build/libs/mineai-0.1.0.jar
gradle runServer     # 开发服务器；本机用 run/server.properties 指定端口 25567、RCON 25576
```

注意：

- 本机 `services.gradle.org` 不可达，wrapper 的 `distributionUrl` 已改为 `https://downloads.gradle.org/distributions/gradle-8.1.1-bin.zip`。
- 本机 25566 端口被另一个 Minecraft 服务器占用，测试服务器改用 25567。

## 1. 文档目的

本文档定义 MineAI 模组阶段 0-1 的工程边界、类职责、运行时生命周期、动作协议、移动实现、状态采集和科技模组适配器基础架构。

阶段 0-1 的唯一验收闭环是：

```text
/npc spawn
    -> 生成一个其他玩家可见的 AgentPlayer
    -> /npc test
    -> 移动到多个 waypoint
    -> 挖掉指定方块
    -> 在合法支撑面上放回方块
    -> 其他真实客户端看到实体移动、方块变化和玩家信息更新
```

本阶段不包含：

- 大模型或 DSV4 Flash 调用
- 自动任务规划
- 完整的真人挖掘动画
- 全部科技模组的实现
- 复杂动态结构（例如 Create contraption）的自动理解

## 2. 设计结论

### 2.1 保留的方向

- `AgentPlayer extends ServerPlayer`，而不是使用 Forge `FakePlayer`。
- 使用自定义的空网络监听器，使 NPC 可以进入 `PlayerList`。
- 动作通过服务端游戏 API 执行，不模拟键盘和鼠标。
- 所有动作由每 tick 驱动的 `ActionQueue` 串行执行。
- 世界状态采集和动作执行分离。
- 科技模组适配器以能力（Capability）为优先，菜单适配器作为补充。

### 2.2 必须修正的方向

`ServerPlayer` 不是 `Mob`，不能直接依赖 `PathNavigation`。移动系统必须由独立的 `NpcMover` 实现，阶段 0-1 先做 waypoint 移动和碰撞检查，后续再替换或接入 A* 路径规划。

`PlaceAction` 的目标应该表示“最终要放置的方块”，内部再寻找支撑方块和点击面。`BlockHitResult` 的方块坐标通常表示被点击的支撑方块，不是最终放置位置。

动作结果不能只用 `boolean` 表示，至少要区分运行中、成功、失败和取消。

## 3. 工程结构

建议的 Forge 模组工程结构如下：

```text
MineAI/
├── build.gradle
├── gradle.properties
├── settings.gradle
├── gradlew
├── gradlew.bat
├── src/main/java/com/yourname/mineai/
│   ├── MineAiMod.java
│   ├── command/
│   │   └── NPCCommand.java
│   ├── entity/
│   │   ├── AgentPlayer.java
│   │   ├── AgentPlayerManager.java
│   │   ├── NpcConnectionFactory.java
│   │   └── NpcGamePacketListener.java
│   ├── action/
│   │   ├── Action.java
│   │   ├── ActionQueue.java
│   │   ├── ActionResult.java
│   │   ├── MoveAction.java
│   │   ├── MineAction.java
│   │   ├── PlaceAction.java
│   │   └── UseAction.java
│   ├── movement/
│   │   ├── NpcMover.java
│   │   ├── WaypointMover.java
│   │   └── NpcCollision.java
│   ├── state/
│   │   └── WorldStateCollector.java
│   ├── integration/
│   │   ├── MachineAdapter.java
│   │   ├── MachineAdapterRegistry.java
│   │   ├── MachineContext.java
│   │   ├── MachineDescription.java
│   │   ├── MachineOperation.java
│   │   └── vanilla/VanillaContainerAdapter.java
│   └── util/
│       └── RegistryIds.java
└── src/main/resources/
    ├── META-INF/mods.toml
    └── pack.mcmeta
```

`NpcGamePacketListener` 属于 NPC 生命周期基础设施，放在 `entity`，不放在 `util`。`util` 只放无状态通用工具。

## 4. 运行时拓扑

```text
MineAiMod
├── Forge event subscribers
│   ├── server starting/stopping
│   ├── command registration
│   └── server tick
├── AgentPlayerManager
│   ├── active NPC map
│   ├── spawn/remove lifecycle
│   └── shutdown cleanup
└── MachineAdapterRegistry
    ├── vanilla adapters
    └── optional mod adapters

AgentPlayer
├── NpcGamePacketListener
├── NpcMover
├── ActionQueue
└── ServerPlayerGameMode
```

数据流遵循以下方向：

```text
NPCCommand -> AgentPlayerManager -> AgentPlayer
AgentPlayer tick -> NpcMover -> entity movement
AgentPlayer tick -> ActionQueue -> Action -> game API
WorldStateCollector <- AgentPlayer / level read-only state
MachineAdapterRegistry -> MachineAdapter -> capability/menu operations
```

动作不能直接修改 `AgentPlayerManager`，管理器也不负责执行方块操作。这样可以避免生命周期和玩法逻辑相互耦合。

## 5. 版本和 API 约束

### 5.1 必须先固定的版本

在写具体 Java 代码前，工程必须固定以下内容：

- Minecraft 版本：1.20.1
- Forge 构建版本：1.20.1-47.2.0（已固定）
- mappings：official 1.20.1
- Java 17
- ForgeGradle 6.x

以下签名已在 `forge-1.20.1-47.2.0_mapped_official_1.20.1.jar` 上用 `javap` 核实，与实现保持一致：

| API | 1.20.1-47.2.0 实际签名 |
| --- | --- |
| `ServerPlayer` 构造 | `ServerPlayer(MinecraftServer, ServerLevel, GameProfile)` |
| `ServerPlayer.connection` | `public ServerGamePacketListenerImpl connection`，构造时不赋值 |
| `ServerGamePacketListenerImpl` 构造 | `(MinecraftServer, Connection, ServerPlayer)` |
| `ServerGamePacketListenerImpl.send` | `send(Packet<?>)` 与 `send(Packet<?>, PacketSendListener)` |
| `PlayerList.placeNewPlayer` | `(Connection, ServerPlayer)`，**没有** `CommonListenerCookie` 参数 |
| `Connection` 构造 | `Connection(PacketFlow)` |
| `ServerPlayerGameMode.destroyBlock` | `boolean destroyBlock(BlockPos)` |
| `ServerPlayerGameMode.useItemOn` | `(ServerPlayer, Level, ItemStack, InteractionHand, BlockHitResult)` |
| `ServerPlayerGameMode.useItem` | `(ServerPlayer, Level, ItemStack, InteractionHand)` |

关键结论：

- `CommonListenerCookie` 与 `ClientInformation` 是 1.20.2+ 才引入的类型，**1.20.1 不存在**。旧稿中“1.20.1 需要传 `ClientInformation` 和 `CommonListenerCookie`”的说法是错误的。
- `placeNewPlayer` 不会写入 `player.connection`，必须在调用它之前像原版登录流程那样自行赋值，否则 NPC 在广播或移除阶段会因 `connection == null` 失败。
- 玩家信息广播使用 `ClientboundPlayerInfoUpdatePacket(EnumSet<Action>, Collection<ServerPlayer>)` 与 `ClientboundPlayerInfoRemovePacket(List<UUID>)`。
- 1.20.1 的数学工具类是 `net.minecraft.util.Mth`，不是 1.16 的 `MathHelper`。
- 注册表 ID 使用 `ForgeRegistries.BLOCKS/ITEMS/ENTITY_TYPES`；`BuiltInRegistries` 的静态字段在 Forge 1.20.1 中已标记过时。

### 5.2 不允许的跨版本假设

- 不假设所有 1.20.x 的 `placeNewPlayer` 参数完全一致。
- 不假设 `ServerPlayer` 存在 `getNavigation()`。
- 不假设发送玩家信息包的内部方法永远不变。
- 不通过反射掩盖版本错误；如果 API 版本不匹配，优先调整实现到固定版本。

## 6. AgentPlayer 设计

### 6.1 身份

每个 NPC 使用稳定但唯一的离线 UUID。不能让所有 NPC 共用一个固定 UUID，否则第二个 NPC 会与第一个发生身份冲突。

```java
UUID uuid = UUID.nameUUIDFromBytes(
    ("mineai:npc:" + npcId).getBytes(StandardCharsets.UTF_8)
);
GameProfile profile = new GameProfile(uuid, "AgentNPC_" + npcId);
```

`npcId` 可以是服务端内递增的整数或持久化字符串。阶段 0-1 可以只支持 `AgentNPC_1`、`AgentNPC_2` 等名称，但 UUID 必须不同。

### 6.2 类职责

`AgentPlayer` 只承担 NPC 玩家实体本身和领域入口：

- 保存 `NpcMover`
- 保存 `ActionQueue`
- 保存 NPC 标识
- 提供 tick 入口
- 继承原版背包、生命、游戏模式和实体同步行为

它不负责：

- 选择 AI 目标
- 管理所有 NPC
- 注册命令
- 解析科技模组 GUI

示意接口：

```java
public final class AgentPlayer extends ServerPlayer {
    private final String npcId;
    private final NpcMover mover;
    private final ActionQueue actionQueue;

    public NpcMover mover() { return mover; }
    public ActionQueue actionQueue() { return actionQueue; }
    public String npcId() { return npcId; }

    public void serverTick() {
        mover.tick(this);
        actionQueue.tick(this);
    }
}
```

实际构造函数以固定 Forge 版本为准。

## 7. NPC 网络连接和 PlayerList 生命周期

### 7.1 空连接与 EmbeddedChannel（实机验证后的修正）

原稿认为“给 `ServerPlayer` 装一个覆写 `send` 的空监听器即可”。实机验证表明这在 Forge 1.20.1 上**不够**，有两个原因：

1. Forge 的 `PlayerList.placeNewPlayer(Connection, ServerPlayer)` 会**自己 new 一个 vanilla `ServerGamePacketListenerImpl`** 并写入 `player.connection`，覆盖掉预先安装的 `NpcGamePacketListener`。因此空监听器在加入之后不再生效。
2. `placeNewPlayer` 会调用 `NetworkHooks.sendMCRegistryPackets(connection, "PLAY_TO_CLIENT")`，它进入 `NetworkFilters.injectIfNecessary(connection)` 并解引用 `Connection.channel()`。裸的 `new Connection(PacketFlow.SERVERBOUND)` 其 `channel()` 为 null，直接抛出：

   `NullPointerException: Cannot invoke "io.netty.channel.Channel.pipeline()" because the return value of "Connection.channel()" is null`

   （Fabric 的 Carpet 用裸 `NetworkManager` 能工作，是因为 Fabric 没有这段 Forge 钩子。）

正确做法：给假连接挂一个 Netty `EmbeddedChannel`，让 `channel()` 非 null。`NetworkFilters` 在 pipeline 中找不到 `packet_handler` 时会自行跳过注入，之后发往 NPC 的所有包都写进 EmbeddedChannel 并被丢弃。

```java
public static Connection create() {
    Connection connection = new Connection(PacketFlow.SERVERBOUND);
    EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
    connection.channelActive(channel.pipeline().firstContext());
    return connection;
}
```

`NpcGamePacketListener` 仍然保留，因为 `placeNewPlayer` 之前（构造、`setGameMode` 等）可能已经需要 `player.connection` 非空。真实实现见 `entity/NpcConnectionFactory`。

### 7.2 生成流程

`AgentPlayerManager.spawn` 的顺序必须固定：

1. 校验服务器线程和目标 level。
2. 分配唯一 NPC ID、名字和 UUID。
3. 创建 `AgentPlayer`（内部创建带 EmbeddedChannel 的假连接并安装 `NpcGamePacketListener`）。
4. 设置位置、旋转、游戏模式和初始背包。
5. 调用 `PlayerList.placeNewPlayer(npcConnection, npc)`。
6. 确认 NPC 已进入 `PlayerList` 和 level 实体列表。
7. 向已连接真实玩家广播玩家加入信息。
8. 写入 `activeNPCs`。

`activeNPCs` 只有在加入成功后才能写入。任何中途异常都必须清理已创建实体和监听器。

### 7.3 移除流程

`remove` 必须完成以下步骤：

1. 从 `activeNPCs` 删除。
2. 清空动作队列并停止移动。
3. 从 `PlayerList` 移除。
4. 从当前 level 移除实体。
5. 向真实玩家广播玩家移除信息。
6. 释放 NPC 对应的引用。

服务器停止事件必须调用 `removeAll`。不能依赖 JVM 退出时的垃圾回收完成游戏内清理。

### 7.4 生命周期验收

- `getPlayerList().getPlayers()` 包含 NPC。
- 真实玩家客户端的玩家列表出现 NPC。
- NPC 在世界中有可见模型。
- NPC 移动和旋转可被其他客户端看到。
- 移除后玩家列表和世界都不残留 NPC。
- 多个 NPC 的 UUID、名字和实体身份不冲突。

## 8. 移动系统

### 8.1 设计原则

不能调用 `npc.getNavigation()`。`ServerPlayer` 没有 `Mob` 的原版导航组件。

移动分成两个职责：

- `NpcMover`：保存目标、速度、当前状态和卡住检测。
- `NpcCollision`：只负责碰撞箱、方块通行性和简单跳跃判定。

阶段 0-1 的 `WaypointMover` 使用简单路径：目标点之间按直线移动，遇到障碍时尝试左右绕行和单格跳跃。它不是最终的通用寻路器。

### 8.2 NpcMover 状态

```text
IDLE
MOVING
ARRIVED
BLOCKED
TIMED_OUT
```

建议字段：

```java
public interface NpcMover {
    void moveTo(Vec3 target, double speed);
    void stop();
    void tick(AgentPlayer npc);
    boolean isDone();
    boolean isFailed();
    Vec3 target();
}
```

### 8.3 每 tick 移动流程

1. 如果没有目标，返回。
2. 计算 NPC 到目标的水平距离和垂直差。
3. 到达容差内则停止并标记完成。
4. 计算下一步速度向量。
5. 用 NPC 碰撞箱测试下一位置。
6. 可通行则设置位置并更新朝向。
7. 不可通行则尝试简单跳跃或侧向 waypoint。
8. 连续若干 tick 位移低于阈值则标记 blocked。
9. 超过动作超时则标记 timed out。

移动应在服务端 tick 中执行，不能从命令线程或异步线程直接修改实体位置。

### 8.4 后续路径规划扩展

未来可以把 `WaypointMover` 替换为 A* 实现，接口不变：

```text
PathPlanner -> List<BlockPos>
NpcMover    -> 当前路径点执行
```

A* 节点至少需要知道站立空间、头部空间、方块碰撞、液体、可跳跃高度和目标容差。路径规划不等于 AI，不应与大模型接入绑定。

## 9. 动作模型

### 9.1 结果枚举

```java
public enum ActionResult {
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED
}
```

失败应带有稳定原因码，例如：

```text
TARGET_TOO_FAR
TARGET_BLOCKED
NO_SUPPORT_BLOCK
NO_ITEM
INVALID_TARGET
GAME_RULE_DENIED
TIMEOUT
```

### 9.2 Action 接口

```java
public interface Action {
    default void onStart(AgentPlayer npc) {}
    ActionResult tick(AgentPlayer npc);
    default void onComplete(AgentPlayer npc, ActionResult result) {}
    default void onCancel(AgentPlayer npc) {}
}
```

动作应该在 `onStart` 中准备目标，在 `tick` 中推进状态。一次性动作不能因为队列每 tick 调用而重复破坏或放置。

### 9.3 ActionQueue

```text
queue: Deque<Action>
current: Action?
currentTicks: int
lastResult: ActionResult?
```

每 tick：

1. 没有当前动作时取出队首。
2. 调用一次 `onStart`。
3. 增加当前动作 tick 计数。
4. 调用 `tick`。
5. 若结果不是 `RUNNING`，调用完成回调并清空当前动作。
6. 超过动作超时则转为 `FAILED/TIMEOUT`。

队列需要提供：

```text
enqueue(action)
clear()
cancelCurrent()
isBusy()
currentAction()
lastResult()
```

### 9.4 MoveAction

`MoveAction` 在 `onStart` 调用 `NpcMover.moveTo`，在 `tick` 中读取 mover 状态：

```text
MOVING -> RUNNING
ARRIVED -> SUCCESS
BLOCKED/TIMED_OUT -> FAILED
```

它不计算路径，也不直接设置最终位置。

### 9.5 MineAction

阶段 0-1 先实现 `InstantMineAction` 语义：

1. 检查目标方块仍存在。
2. 检查与目标距离不超过服务端允许距离。
3. 转向目标。
4. 调用 `gameMode.destroyBlock(pos)` 一次。
5. 验证方块已变为空气或可替换状态。
6. 返回成功或失败。

`destroyBlock` 会触发原版掉落、工具耐久和相关事件，但不会完整模拟真人挖掘时长。后续如果需要破坏进度，新增 `ProgressiveMineAction`，不要改变立即挖掘动作的语义。

### 9.6 PlaceAction

构造参数表示最终目标方块：

```java
new PlaceAction(targetPos)
```

执行前：

1. 目标位置必须为空气或可替换方块。
2. 查找相邻的合法支撑方块。
3. 确定支撑方块和点击面。
4. 检查主手物品是可放置方块。
5. 检查距离、碰撞和游戏规则。
6. 构造正确的 `BlockHitResult`。
7. 调用目标版本签名对应的 `useItemOn`。
8. 验证目标方块已经放置。

不要把最终目标坐标直接当成 `BlockHitResult` 的支撑坐标。

### 9.7 UseAction

`UseAction` 用于不需要方块点击的主手物品使用。需要方块交互的行为应走 `PlaceAction` 或单独的 block interaction action，以便携带目标、面和验证信息。

## 10. 硬编码测试脚本

测试序列必须记录起点，不能在每一步动态重新读取导致目标漂移：

```java
BlockPos start = npc.blockPosition();
BlockPos p1 = start.offset(5, 0, 0);
BlockPos p2 = start.offset(5, 0, 5);
BlockPos p3 = start.offset(0, 0, 5);

queue.enqueue(new MoveAction(Vec3.atBottomCenterOf(p1)));
queue.enqueue(new MoveAction(Vec3.atBottomCenterOf(p2)));
queue.enqueue(new MoveAction(Vec3.atBottomCenterOf(p3)));
queue.enqueue(new MoveAction(Vec3.atBottomCenterOf(start)));

BlockPos mineTarget = start.relative(Direction.NORTH);
BlockPos placeTarget = mineTarget;
queue.enqueue(new MineAction(mineTarget));
queue.enqueue(new PlaceAction(placeTarget));
```

实际测试场地应保证：

- NPC 不站在即将挖掉的方块上。
- 挖掘目标旁边有合法支撑面。
- 主手有足够的目标方块。
- 矩形路线没有天然悬崖或水体。
- 测试方块不是受保护区域或不可破坏方块。

## 11. 命令入口

### 11.1 `/npc spawn`

在执行者当前位置生成 NPC。命令返回 NPC ID，并在失败时返回明确原因。

### 11.2 `/npc remove [id|all]`

移除指定 NPC 或所有 NPC。`all` 用于服务器停止前和测试清场。

### 11.3 `/npc list`

列出 ID、名字、UUID、维度、坐标、当前动作和移动状态。

### 11.4 `/npc test [id]`

获取指定 NPC，清空现有队列，注入矩形移动、挖掘和放置序列。找不到 NPC 时命令失败，不静默创建新实体。

测试序列会选择前方“实体方块”作为挖掘目标：若正前方是空气，则退而挖掘其下方方块，避免在平坦地形上挖空气。

### 11.5 `/npc state [id]`（实现期新增）

调用 `WorldStateCollector`，把快照 JSON 同时写入服务器日志并作为命令反馈返回，用于验证采集协议。

命令回调只负责读取上下文和提交请求；所有实体修改仍在服务器线程执行。

## 12. AgentPlayerManager

管理器是唯一的 NPC 生命周期所有者：

```text
spawn(server, level, position) -> AgentPlayer
get(id) -> Optional<AgentPlayer>
getFirst() -> Optional<AgentPlayer>
all() -> Collection<AgentPlayer>
remove(server, id) -> boolean
removeAll(server)
tick(server)
```

tick 时应遍历快照，避免动作执行过程中修改活动 map 导致并发修改：

```java
for (AgentPlayer npc : List.copyOf(activeNPCs.values())) {
    if (npc.isRemoved()) {
        activeNPCs.remove(npc.npcId());
        continue;
    }
    npc.serverTick();
}
```

## 13. 世界状态采集

### 13.1 采集边界

`WorldStateCollector` 只读，不执行动作，不触发事件，不修改实体。建议输出以下稳定字段：

```json
{
  "self": {
    "x": 0.0,
    "y": 64.0,
    "z": 0.0,
    "health": 20.0,
    "food": 20,
    "yaw": 0.0,
    "pitch": 0.0
  },
  "inventory": [],
  "nearby_blocks": [],
  "nearby_entities": [],
  "environment": {}
}
```

字段协议阶段 0-1 固定为 snake_case，后续工具调用和 AI 协议继续沿用，避免双格式。

### 13.2 注册表 ID

不要使用 `getDescriptionId()` 作为外部协议 ID。使用 Forge/Minecraft 注册表的稳定 ID，例如：

```text
minecraft:stone
minecraft:chest
mekanism:enriched_iron
```

可以在 `RegistryIds` 中统一封装方块、物品、实体类型和菜单类型的 ID 转换。

### 13.3 附近方块压缩

默认只采集有意义的方块：容器、液体、危险方块、机器和动作目标相关方块。但必须额外保留：

- NPC 脚下方块
- NPC 前方方块
- 当前移动路径附近碰撞方块
- 当前动作目标
- 放置候选支撑方块

这样状态采集和移动/动作判断不会出现信息不一致。

## 14. 科技模组适配器架构

### 14.1 核心结论

不能承诺一次性支持“所有科技模组”。正确做法是先冻结统一能力模型，再按实际安装模组增量实现适配器。

优先级：

1. 原版容器：箱子、熔炉、工作台、漏斗。
2. Capability：`IItemHandler`、`IFluidHandler`、`IEnergyStorage`。
3. Mekanism：机器输入、输出、升级和能量能力。
4. AE2/Refined Storage：网络查询和存取能力。
5. Create：动态结构和 Mounted Storage，单独处理。
6. 其他模组：根据真实需求增加。

### 14.2 能力优先分层

```text
MachineIntegration
├── CapabilityAdapter
│   ├── ItemHandlerAdapter
│   ├── FluidHandlerAdapter
│   └── EnergyStorageAdapter
├── MenuAdapter
│   └── 解释 AbstractContainerMenu 的槽位和按钮
└── ModSpecificAdapter
    ├── AE2Adapter
    ├── RefinedStorageAdapter
    └── CreateAdapter
```

Capability 是机器真实行为边界，菜单只是某种交互表现。因此 AI 或上层工具应优先查询能力，不应依赖 GUI 坐标。

### 14.3 MachineAdapter 接口

不要让适配器直接接收自然语言 `String instruction`。先转换成受限的结构化操作：

```java
public interface MachineAdapter {
    boolean supports(MachineContext context);
    MachineDescription describe(MachineContext context);
    ActionResult execute(
        AgentPlayer npc,
        MachineContext context,
        MachineOperation operation
    );
}
```

`MachineOperation` 可以包含：

```text
QueryContents
InsertItem
ExtractItem
InsertFluid
ExtractFluid
QueryEnergy
SetMachineMode
```

所有操作都必须在服务端线程、目标距离和权限检查之后执行。

### 14.4 适配器发现

使用 `MachineAdapterRegistry` 注册适配器，不通过类名字符串判断：

```text
registry.register(new VanillaContainerAdapter())
if ModList.isLoaded("mekanism"):
    registry.register(new MekanismAdapter())
```

可选模组适配器应该放入独立 compat 包，并通过 `ModList` 判断是否加载。模组不存在时不能触发类加载错误。

菜单适配器只有在没有可用 capability 或确实需要 GUI 参数时才介入。坐标推断只能作为最后兜底，并且必须绑定版本和测试样本。

### 14.5 Create 的特殊边界

Create 的动态 contraption 不能按普通方块实体机器处理。第一版只支持：

- 静态方块实体提供的 item/fluid capability。
- 明确可定位的 Mounted Storage。

动态 contraption 的移动、挂载存储和控制面板需要独立的 `CreateAdapter` 与专门测试，不放入阶段 0-1 的最小闭环。

## 15. Tick 时序

服务器 tick 中建议按以下顺序执行：

```text
1. AgentPlayerManager 清理无效 NPC
2. AgentPlayerManager.tick
3. NpcMover.tick
4. ActionQueue.tick
5. 状态采集或调试日志（低频）
```

动作队列和移动器都在主服务器线程运行。状态采集可以低频执行，例如每 5 或 10 tick 一次；不能为了日志每 tick 输出完整 JSON。

移动器和动作队列都只能有一个写入实体位置或游戏状态的入口，避免同一 tick 中两个系统互相覆盖。

## 16. 日志和可观测性

每个动作至少记录：

```text
npc_id
action_type
target
start_tick
finish_tick
result
failure_reason
```

建议日志级别：

- `INFO`：spawn/remove、动作成功或失败。
- `DEBUG`：每次 waypoint 变更、碰撞绕行、状态摘要。
- `TRACE`：逐 tick 移动向量，仅开发测试开启。

日志不得每 tick 打印完整背包和附近方块 JSON，否则测试服务器很快被日志淹没。

## 17. 阶段 0-1 实施拆分

### 任务 A：Forge 工程骨架

- 固定 Minecraft、Forge、Java 和 mappings。
- 建立 `mods.toml`、主类和事件订阅。
- 能启动开发服务端。

验收：开发服务端正常启动，模组被加载。

### 任务 B：NPC 生命周期

- 实现 `AgentPlayer`。
- 实现 dummy connection。
- 实现加入和移除 `PlayerList`。
- 实现 `/npc spawn`、`/npc remove`、`/npc list`。

验收：两个真实客户端都能看到 NPC，移除后不残留。

### 任务 C：Waypoint 移动

- 实现 `NpcMover`、`WaypointMover` 和碰撞检查。
- 实现 `MoveAction` 和 `ActionQueue`。
- 增加卡住和超时结果。

验收：NPC 能走完无障碍 5x5 矩形，遇到障碍不会无限卡死。

### 任务 D：方块动作

- 实现立即挖掘。
- 实现支撑面解析。
- 实现放置和结果验证。
- 实现 `/npc test`。

验收：指定方块确实消失，然后在指定目标位置重新出现。

### 任务 E：状态采集

- 实现稳定注册表 ID。
- 实现自身、背包、附近实体、附近方块和环境摘要。
- 增加低频调试输出。

验收：JSON 字段稳定、体积可控、目标方块和支撑面不会被过滤掉。

### 任务 F：原版机器适配器

- 实现容器上下文。
- 实现箱子、熔炉的基础描述。
- 实现查询、插入和取出操作的受限入口。

验收：NPC 能在测试世界打开原版容器并完成一次物品放入/取出。

## 18. 测试清单

### 18.1 生命周期

- 单 NPC 生成。
- 多 NPC 生成。
- 重复名字和 UUID 防护。
- 跨维度生成拒绝或正确处理。
- 移除指定 NPC。
- 移除全部 NPC。
- 服务器停止清理。

### 18.2 移动

- 空旷平面直线移动。
- 5x5 矩形移动。
- 一格障碍绕行。
- 一格高度跳跃。
- 目标不可达超时。
- 移动途中 NPC 被移除。

### 18.3 方块动作

- 可破坏方块挖掘。
- 目标不存在时失败。
- 距离过远时失败或先移动。
- 不可放置位置失败。
- 没有支撑面时失败。
- 没有物品时失败。
- 放置后目标方块验证。

### 18.4 客户端可见性

- 玩家列表可见。
- 实体模型可见。
- 位置同步。
- 旋转同步。
- 装备变化同步。
- 移除包生效。

## 19. 明确不做的事情

- 不用 `FakePlayer` 代替 `AgentPlayer`。
- 不调用 `ServerPlayer.getNavigation()`。
- 不在异步线程修改实体、方块或容器。
- 不通过类名字符串作为唯一模组识别机制。
- 不把 GUI 槽位坐标当作所有机器的通用语义。
- 不在阶段 0-1 引入大模型决策。
- 不为“支持所有科技模组”提前写大量猜测性代码。

## 20. 阶段完成定义

阶段 0-1 只有在以下条件全部满足时才算完成：

1. 开发服务器能加载 MineAI 模组。
2. `/npc spawn` 生成的 NPC 出现在 `PlayerList`。
3. 至少一个真实客户端能看到 NPC。
4. NPC 能由独立移动器走完 5x5 矩形。
5. 移动动作有明确成功、失败和超时结果。
6. NPC 能挖掉指定方块。
7. NPC 能在合法支撑面上放回方块。
8. `/npc test` 可以重复执行而不会产生失控队列。
9. `/npc remove all` 和服务器停止都能完成清理。
10. 世界状态 JSON 使用稳定注册表 ID，且不会无界膨胀。
11. 原版容器适配器的接口边界已经冻结，但不要求 Mekanism 等适配器在本阶段完成。

## 21. 后续阶段接口

阶段 0-1 完成后，后续 AI 层只能通过结构化工具调用接入：

```text
goto(x, y, z)
mine(x, y, z)
place(x, y, z, item)
use(hand)
query_block(x, y, z)
collect_state()
```

AI 不能直接调用 Minecraft API、修改世界对象或绕过 `ActionQueue`。工具调用必须转换成动作，动作必须经过距离、目标和结果验证。这样阶段 0-1 的硬编码脚本和后续 AI 调用共享同一执行层，不需要重写实体和方块逻辑。
