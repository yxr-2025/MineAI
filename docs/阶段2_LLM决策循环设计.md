# MineAI 阶段 2：LLM 决策循环设计

> 状态：已实现并在开发服务器上跑通（`mock` provider 验证）
> 前置：阶段 0-1 已完成（假玩家 + 动作队列 + 移动 + 方块动作 + 状态采集）
> 目标版本：Minecraft 1.20.1 / Forge 1.20.1-47.2.0 / Java 17
> 本阶段：把动作能力暴露为受限工具，接入 LLM 做感知-决策-执行循环
>
> 后续阶段见 `docs/阶段3_自主感知与技能设计.md`（自主 mission、A* 寻路、渐进挖掘、容器/合成、技能库与记忆）。

## 1. 目标与边界

阶段 2 的闭环是：

```text
/npc spawn
/npc agent start <goal>
    -> AgentController 收集状态
    -> 异步调用 LLM（OpenAI 兼容 /chat/completions）
    -> LLM 返回 tool_calls
    -> 工具层把 tool_call 转换成 ActionQueue 中的动作
    -> 动作完成后把结果回传给模型
    -> 重复直到模型不再调用工具或达到 max_steps
    -> 状态变为 FINISHED / FAILED
```

边界：

- LLM 只能通过白名单工具影响世界，不能直接调用 Minecraft API。
- LLM 调用必须是异步的，绝不阻塞服务器线程。
- API key 不落库、不写日志。
- 未配置 LLM 时，模组其余功能照常工作。

## 2. 分层架构

```text
AgentController（每个 NPC 一个，状态机）
├── 读取状态：WorldStateCollector
├── 决策：LlmService.provider().complete(...)  异步
├── 执行：ToolRegistry → AgentTool → ActionQueue → Action
└── 回传：tool 角色消息写回对话历史
```

```text
com.mineai/
├── agent/
│   ├── AgentState.java          # IDLE/THINKING/AWAITING_ACTION/FINISHED/FAILED
│   ├── AgentController.java     # 决策状态机
│   └── ChatMessage.java         # OpenAI 格式消息构造
├── llm/
│   ├── LlmProvider.java         # 异步接口
│   ├── LlmResponse.java
│   ├── ToolCall.java
│   ├── LlmService.java          # 进程级 provider/config 持有
│   ├── OpenAiCompatibleProvider.java
│   └── MockLlmProvider.java
├── tool/
│   ├── AgentTool.java           # 工具接口 + schema 辅助
│   ├── ToolResult.java
│   ├── ToolRegistry.java        # 白名单
│   ├── CollectStateTool / QueryBlockTool
│   ├── GotoTool / MineTool / PlaceTool / UseTool
└── config/
    └── LlmConfig.java
```

## 3. 工具层

工具是模型唯一可用的能力集合。每个工具声明名称、描述、JSON Schema 参数，并返回 `ToolResult`。

`ToolResult` 有两个关键标志：

- `success`：即时工具是否成功。
- `queued`：工具是否把工作推进了 `ActionQueue`，真实结果要等队列排空才知道。

工具清单：

| 工具 | 参数 | 类型 | 映射动作 |
| --- | --- | --- | --- |
| `collect_state` | 无 | 即时 | `WorldStateCollector` |
| `query_block` | x, y, z | 即时 | 读取方块状态 |
| `goto` | x, y, z | 排队 | `MoveAction` |
| `mine` | x, y, z | 排队 | `MineAction` |
| `place` | x, y, z | 排队 | `PlaceAction` |
| `use` | 无 | 排队 | `UseAction` |

`goto` 语义：走到给定 x/z 的方块列并在地面站定；移动器跟随地形，因此 y 只是提示。

## 4. LLM 层

### 4.1 Provider 接口

```java
public interface LlmProvider {
    String name();
    CompletableFuture<LlmResponse> complete(List<JsonObject> messages, JsonArray tools);
}
```

`complete` 必须异步返回，`AgentController` 在 tick 中检查 future 是否完成。

### 4.2 OpenAI 兼容实现

`OpenAiCompatibleProvider` 使用 `java.net.http.HttpClient.sendAsync` 调用 `{base_url}/chat/completions`，请求体包含：

- `model`
- `messages`（system / user / assistant / tool）
- `tools` + `tool_choice: "auto"`
- `temperature`、`max_tokens`

响应解析 `choices[0].message.content` 与 `choices[0].message.tool_calls[]`。

DeepSeek 使用同一协议，默认 `base_url=https://api.deepseek.com/v1`。

### 4.3 Mock Provider

`MockLlmProvider` 返回固定脚本（collect_state → goto → mine → place → 结束），用于无 key、无网络地验证决策循环本身。

## 5. 配置

配置文件：`config/mineai/llm.json`（开发服务器位于 `run/config/mineai/llm.json`）。

```json
{
  "enabled": false,
  "provider": "deepseek",
  "base_url": "https://api.deepseek.com/v1",
  "api_key": "",
  "model": "deepseek-chat",
  "temperature": 0.2,
  "max_tokens": 1024,
  "timeout_seconds": 60,
  "max_steps": 12
}
```

规则：

- 文件不存在时自动生成上述示例，`enabled=false`，`api_key` 留空。
- API key 读取顺序：环境变量 `MINEAI_LLM_API_KEY` → 配置文件 `api_key`。两者都可单独工作。
- 直接把 key 写进配置文件也可用（已实测）；该文件位于游戏目录 `config/mineai/llm.json`，开发服务器对应 `run/config/mineai/llm.json`，而 `run/` 已在 `.gitignore` 中。
- **不要把带 key 的配置提交到仓库**。若仓库结构变化，务必确认该路径仍被忽略；更稳妥的做法是使用环境变量。
- `provider` 取值：`deepseek`（或任意 OpenAI 兼容）→ HTTP；`mock` → 脚本。
- `enabled=false` 时 `LlmService` 为空，`/npc agent start` 返回明确的未配置错误。

当前开发服务器已把 key 直接写入 `run/config/mineai/llm.json` 的 `api_key` 字段，无需再设环境变量即可运行。

切换到真实 DeepSeek：

```text
1. 编辑 run/config/mineai/llm.json
   "enabled": true, "provider": "deepseek", "model": "deepseek-chat"
2. 设置环境变量 MINEAI_LLM_API_KEY=sk-xxx（推荐），或填入 api_key
3. 重启服务器
```

## 6. 决策状态机

`AgentController` 每个服务器 tick 推进一次：

```text
IDLE
  └─ start(goal) ─> THINKING
THINKING
  ├─ future 未完成 ─> 等待（超时 90 秒则 FAILED）
  └─ future 完成
       ├─ 无 tool_calls ─> FINISHED（记录最终文本）
       └─ 有 tool_calls ─> 依次执行
AWAITING_ACTION
  ├─ 动作队列忙 ─> 等待（超时 60 秒则记失败并继续）
  └─ 队列排空 ─> 读取 lastResult，写入 tool 消息，继续下一个工具调用
```

关键点：

- 工具执行顺序：控制器先 tick，动作队列后 tick，保证入队后至少下一 tick 才结算。
- 即时工具（collect_state / query_block）在同一个 tick 内完成并继续。
- 单步最多处理 16 个工具调用，防止模型死循环。
- 每步把最新的 `self / inventory / environment` 作为 system 上下文注入，不写入历史，避免历史膨胀。

## 7. 安全设计

- **白名单**：模型只能调用 `ToolRegistry` 中注册的工具；未知工具返回失败消息。
- **非阻塞**：HTTP 与脚本都在异步线程完成，服务器线程只做状态检查。
- **超时**：LLM 调用 90 秒、单个动作 60 秒、`ActionQueue` 自身 400 tick 超时。
- **步数上限**：`max_steps` 限制模型调用次数。
- **失败可见**：工具失败、动作失败、超时都会以 `success=false` 写回模型，模型可自行纠正。
- **无密钥泄漏**：日志只打印 provider/model/baseUrl，从不打印 key。

## 8. 命令

| 命令 | 说明 |
| --- | --- |
| `/npc agent start <goal>` | 对首个 NPC 启动决策循环 |
| `/npc agent stop` | 停止循环并清空动作队列 |
| `/npc agent status` | 输出 state / step / goal / last / error |

## 9. 实机验证（mock provider）

开发服务器（`gradle runServer` + RCON）实测：

| 验收项 | 结果 |
| --- | --- |
| 配置自动生成 | 首次启动生成 `llm.json`（空 key、disabled） |
| 未配置时的行为 | `/npc agent start` 返回 “LLM provider is not configured”，不崩溃 |
| 启用 mock | 日志 `LLM enabled: provider=mock` |
| 决策循环 | `step 1 collect_state → step 2 goto → step 3 mine → step 4 place → step 5 结束` |
| 工具→动作 | `MoveAction/MineAction/PlaceAction` 全部 `SUCCESS` |
| 世界变化 | `(2,-61,1)` 由 `grass_block` 变为 `dirt` |
| 终止条件 | 模型无 tool_calls 后 `state=FINISHED`，`last=Goal complete.` |

日志样例：

```text
[mineai] agent npc_1 step 1 content=Let me inspect the area first. toolCalls=1
[mineai] agent npc_1 tool collect_state -> collected state
[mineai] agent npc_1 step 2 content=Moving two blocks east. toolCalls=1
[mineai] agent npc_1 tool goto -> walking to 2,-60,0
[mineai] agent npc_1 step 3 content=Mining the block in front of me. toolCalls=1
[mineai] agent npc_1 tool mine -> mining 2,-61,1
[mineai] agent npc_1 step 4 content=Placing the block back. toolCalls=1
[mineai] agent npc_1 tool place -> placing at 2,-61,1
[mineai] agent npc_1 step 5 content=Goal complete. toolCalls=0
[mineai] agent npc_1 finished: Goal complete.
```

### 9.1 真实 DeepSeek 验证（OpenAI 兼容中转站）

配置：`base_url=https://tokeness.ai/v1`、`model=deepseek-v4-flash`、key 由 `MINEAI_LLM_API_KEY` 注入。

先单独验证该模型支持 function calling（返回了正确的 `goto` 调用），再跑完整循环。

目标：`Walk to x=5 z=0 and break the ground block north of you, then place a dirt block there.`

真实模型自主产生的轨迹（未做任何提示词硬编码坐标）：

```text
step 1  collect_state
step 2  goto 3,-61,0            # 模型自行推断地面 y=-61
step 3  collect_state
step 4  query_block x2          # 挖掘前确认目标方块
step 5  mine 3,-61,-1
step 6  place 3,-61,-1
step 7  query_block             # 放置后自检
step 8  无 tool_calls -> FINISHED
```

模型最终输出：

```text
Goal complete. I walked east to around x=3, z=0, then broke the grass block just north of
my position (at x=3, y=-61, z=-1) and placed a dirt block back in that exact spot.
The block is now confirmed to be dirt.
```

世界状态核验：任务结束瞬间 `(x,-61,-1)` 为 `minecraft:dirt`，`grass_block`/`air` 检测均失败。

> 注意：平坦世界里被放置的泥土会被相邻草方块**随机刻蔓延**重新长成草（原版行为）。因此核验必须在任务结束后立即进行；延迟几十秒再查可能看到草方块，这不是动作失败。

## 10. 已知限制与后续

- `goto` 只按水平距离判定到达，垂直方向跟随地形；不支持跨维度、跨水、长距离。
- 尚未实现渐进式挖掘，`mine` 是瞬时破坏。
- 工具集未包含容器/机器交互；`integration` 适配器尚未接到工具层。
- 尚无对话记忆持久化，重启后历史丢失。
- 尚未做并发限流；多 NPC 同时决策会并发请求同一 API。
- 真实模型偶尔会先 `query_block` 多次再行动，`max_steps` 需要留足余量（实测 7-8 步）。
- 平坦世界里放置的泥土会被草地随机刻覆盖，验收时须即时检查。

后续可选方向：

1. 把 `VanillaContainerAdapter` 封装成 `open_container / insert / extract` 工具。
2. 增加 `ProgressiveMineAction` 与 A* 寻路，提升动作真实度。
3. 增加每 tick 请求节流与多 NPC 调度。
4. 对话记忆持久化与失败重试策略。
