# MineAI 阶段 7：接入 Mekanism（通用机械）与假玩家拾取修复

> 状态：已实现并在开发服务器上验证
> 前置：阶段 0-1 ~ 6
> 目标版本：Minecraft 1.20.1 / Forge 1.20.1-47.2.0 / Java 17

> 后续阶段见 `docs/阶段8_客观验收循环守卫与配方查询.md`（客观验收、循环守卫、配方查询、每步预算）。

## 1. 本阶段范围

1. 把 **Mekanism（中文名"通用机械"）** 作为可选依赖接入模组，并用它真实的**冶金灌注机**（Metallurgic Infuser）作为科技模组适配器的参照机器。
2. 用能力优先的适配器读取/操作 Mekanism 机器。
3. 验证 agent 能合成 Mekanism 冶金灌注机，并尝试"徒手从零"。
4. 过程中修复了一个假玩家的真实缺陷：**掉落物拾取失效**。

## 2. Mekanism 接入

### 2.1 依赖

`build.gradle`：

```groovy
repositories {
    maven { name = 'ModMaven'; url = 'https://modmaven.dev/' }
}
dependencies {
    minecraft "net.minecraftforge:forge:${minecraft_version}-${forge_version}"
    implementation fg.deobf("mekanism:Mekanism:${mekanism_version}")
}
```

`gradle.properties`：`mekanism_version=1.20.1-10.4.16.80`

`mods.toml` 声明为**可选**依赖（`mandatory=false`、`ordering="AFTER"`），模组在没有 Mekanism 时仍可运行。

开发服务器日志确认加载：`Found valid mod file Mekanism-1.20.1-10.4.16.80_mapped`。

### 2.2 冶金灌注机配方（来自模组数据）

```json
{
  "type": "minecraft:crafting_shaped",
  "pattern": ["I#I", "ROR", "I#I"],
  "key": {
    "I": {"tag": "forge:ingots/iron"},
    "#": {"item": "minecraft:furnace"},
    "O": {"tag": "forge:ingots/osmium"},
    "R": {"tag": "forge:dusts/redstone"}
  },
  "result": {"item": "mekanism:metallurgic_infuser"}
}
```

即 **4 铁锭 + 2 熔炉 + 1 锇锭 + 4 红石粉**。锇（osmium）是 Mekanism 的矿石，物品 ID 为 `mekanism:ingot_osmium`。

## 3. 能力优先的机器适配器（第 4 项原则的落地）

新增 `integration/CapabilityMachineAdapter`：

- `supports`：方块实体暴露 Forge `IItemHandler` 即支持。
- `describe`：按 capability 的槽位数量生成槽位表。
- `execute`：`query` / `insert` / `extract` 全部走 `IItemHandler`。

它**不引用任何 Mekanism 类**，因此 Mekanism、Thermal 以及其它暴露物品能力的科技模组都能直接用；注册顺序在 `VanillaContainerAdapter` 之后，原版容器仍走更精确的适配器。

实机验证：agent 对 Mekanism 冶金灌注机执行 `list_container`，返回 4 个槽位（`slot_0`~`slot_3`），与机器实际槽位数一致。

这解决了设计文档里 `MachineAdapter` 早期版本"靠类名/坐标推断"的不可靠问题。

## 4. 假玩家拾取缺陷与修复（重要）

### 4.1 现象

agent 挖掉方块后，掉落物就在 **0.29 格**处却始终进不了背包，导致"徒手从零"永远拿不到木头。

### 4.2 排查

- `collect_state` 显示掉落物距离 0.29，背包为空。
- 用 `AgentPlayer` 覆写 `tick()` 计数确认 `Player.tick()` **确实在运行**（`playerTicks` 与 `entityTickCount` 同步增长）。
- 反编译定位：拾取逻辑在 `Player.aiStep()` 内（`Level.getEntities` → `Player.touch` → `Entity.playerTouch`），守卫条件本身没有问题。
- 结论：假玩家走了 `tick`/`aiStep`，但原版拾取扫描对其不稳定生效。

### 4.3 修复

在 `AgentPlayer.serverTick()` 中显式做一次拾取扫描：

```java
private void collectNearbyItems() {
    for (Entity entity : this.level().getEntities(this, this.getBoundingBox().inflate(1.0D, 0.5D, 1.0D))) {
        if (entity instanceof ItemEntity item && !item.isRemoved()) {
            item.playerTouch(this);
        }
    }
}
```

与"假连接需要 EmbeddedChannel""死亡需要取消 `LivingDeathEvent`"同类——这是假玩家机制补丁，不是决策逻辑。

验证：召唤 3 个橡木原木 → agent 背包出现 `minecraft:oak_log x3`。

### 4.4 影响

修复后，"徒手从零"才真正可跑：agent 从空背包开始砍树并**成功累积了 10 个云杉原木**。

## 5. 实机验证

| 项 | 结果 |
| --- | --- |
| Mekanism 加载 | `Found valid mod file Mekanism-1.20.1-10.4.16.80_mapped` |
| 物品可用 | `/give` 成功获得 `mekanism:metallurgic_infuser`、`mekanism:ingot_osmium` |
| 能力适配器 | `list_container` 读到冶金灌注机 4 个槽位 |
| 合成（有材料） | `crafted 1 mekanism:metallurgic_infuser`，背包确认 |
| 拾取修复 | 掉落物 3 个原木成功入包 |
| 徒手从零（前半段） | 原木 10 → 木板 20 → 工作台 → 木棍 4 → 木镐 + 木斧 |
| 工作台校验 | 无工作台时报 `this recipe needs a crafting table within 5 blocks`，放置后成功 |

## 6. 已知限制

- **徒手从零尚未走完全程**：前半段（木材→工具）已验证；后半段（挖石→熔炉→铁/锇/红石→熔炼→合成）受单目标 60 步预算限制，需要分多轮继续。
- 模型有时会在拿到工具后提前判定"完成"，需要更明确的续跑目标或更长的步数预算。
- 能力适配器只覆盖物品能力（`IItemHandler`）；Mekanism 的气体、能量、流体需要各自的能力适配器。
- Mekanism 的机器 NBT 不用原版 `Items` 列表，`/item replace block` 之类的原版命令改不了它的内容，测试需用 agent 的 `insert_item` 或玩家 GUI。

## 7. 后续可选方向

1. 为 Mekanism 增加气体/能量能力适配器（`IGasHandler`、`IEnergyStorage`）。
2. 用 plan 模式跑完整条从零链（每步一个子目标，避免单目标步数耗尽）。
3. 把"提前收尾"改为由 `noul` 式校验把关（或在提示词中明确"未达成即不得结束"）。
