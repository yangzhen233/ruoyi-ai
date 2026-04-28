# Token 窗口内存管理实现总结

## 概述
基于 LangChain4j 的 ChatMemory 机制，实现了一套智能的 Token 窗口内存管理系统。该系统能够根据不同模型的 Token 限制，自动管理对话上下文，支持 Token 截断和摘要压缩两种处理方式。

## 架构设计

### 1. 整体框架
```
用户消息 → ChatServiceFacade → ChatMemoryFactory → TokenBasedChatMemory
                                                              ↓
                                                    PersistentChatMemoryStore
                                                              ↓
                                                    数据库查询历史消息
                                                              ↓
                                                    Token 计数检查
                                                              ↓
                                              ┌───────────────┴───────────────┐
                                              ↓                               ↓
                                        Token 未超限                      Token 超限
                                              ↓                               ↓
                                        直接返回消息                    尝试摘要压缩
                                                                              ↓
                                                                    摘要后仍超限？
                                                                              ↓ 是
                                                                        Token 截断
```

### 2. 数据流程

```
用户发送消息
    ↓
ChatServiceFacade.sseChat()
    ↓
chatRequest.setChatModelVo(chatModelVo)  // 先设置模型配置
    ↓
buildContextMessages(chatRequest)
    ↓
createChatMemory(sessionId, model)
    ↓
TokenBasedChatMemory.messages()
    ↓
PersistentChatMemoryStore.getMessages(sessionId)
    ↓
chatMessageService.getMessagesBySessionId(sessionId)
    ↓
SELECT * FROM chat_message WHERE session_id = ? ORDER BY id ASC
    ↓
返回历史消息列表
    ↓
Token 检查 → 摘要/截断处理
    ↓
返回处理后的消息给模型
```

**注意**：摘要和截断只在内存中处理，不修改数据库原始消息。

### 3. 核心组件

#### A. ModelTokenLimits (模型 Token 限制映射)
- **文件**: `org.ruoyi.service.chat.impl.memory.ModelTokenLimits`
- **职责**: 维护 100+ 主流 AI 模型的 Token 限制信息
- **支持模型**:
  - OpenAI: GPT-4.1, GPT-4o, o1/o3/o4-mini 系列
  - DeepSeek: V3, R1 系列
  - 智谱: GLM-5, GLM-4.5, GLM-5.1 系列
  - 通义千问: Qwen3, Qwen-max/plus 系列
  - Claude: Claude 4, Claude 3.5/3.7 系列
  - Google: Gemini 2.5 Pro/Flash 系列
  - 字节豆包: Doubao 1.5 系列
  - xAI: Grok 3 系列
  - Ollama 本地模型: llama3, mistral, qwen2 等

```java
// 获取模型的 Token 限制
int limit = ModelTokenLimits.getLimit("gpt-4o");  // 返回 128000

// 获取输入 Token 上限（预留回复空间）
int inputLimit = ModelTokenLimits.getInputLimit("gpt-4o", 2000);  // 返回 126000

// 检查模型是否已知
boolean known = ModelTokenLimits.isKnownModel("gpt-4o");  // 返回 true
```

#### B. TokenCounter (Token 计数器)
- **文件**: `org.ruoyi.service.chat.impl.memory.TokenCounter`
- **职责**: 估算文本和消息的 Token 数量
- **估算规则**:
  - 中文: 约 2 字符 = 1 token
  - 英文: 约 4 字符 = 1 token
  - 每条消息固定开销: 4 tokens（格式标记）
  - 对话总开销: 3 tokens

```java
TokenCounter counter = new TokenCounter();

// 计算文本 Token 数
int tokens = counter.countTokens("你好世界 Hello World");

// 计算消息列表 Token 数
int total = counter.countMessages(messages);

// 估算指定 Token 预算下可容纳的消息数量
int maxMsgs = counter.estimateMaxMessages(8000, 50);
```

#### C. TokenBasedChatMemory (Token 窗口内存)
- **文件**: `org.ruoyi.service.chat.impl.memory.TokenBasedChatMemory`
- **职责**: 核心内存管理实现，支持 Token 截断和摘要压缩

```java
TokenBasedChatMemory memory = TokenBasedChatMemory.builder()
    .memoryId(sessionId)
    .maxTokens(128000)                   // 最大 Token 数
    .tokenCounter(tokenCounter)          // Token 计数器
    .store(persistentStore)              // 持久化存储
    .summarizeEnabled(true)              // 启用摘要
    .summarizeTokenRatio(0.7)            // 70% 时触发摘要
    .summarizeThreshold(10)              // 最少 10 条消息才摘要
    .summarizer(llmModel)                // 摘要模型
    .preserveSystemMessages(true)        // 保护系统消息
    .reservedForReply(2000)              // 预留回复空间
    .build();
```

#### D. ChatMemoryFactory (内存工厂)
- **文件**: `org.ruoyi.service.chat.impl.memory.ChatMemoryFactory`
- **职责**: 根据配置和模型创建合适的 ChatMemory 实例

```java
// 创建默认内存（根据配置策略）
ChatMemory memory = chatMemoryFactory.create(sessionId, chatModelVo);

// 创建带摘要功能的内存
ChatMemory memory = chatMemoryFactory.create(sessionId, chatModelVo, summarizerModel);

// 获取模型 Token 限制
int limit = chatMemoryFactory.getModelTokenLimit("gpt-4o");
```

#### E. PersistentChatMemoryStore (持久化存储)
- **文件**: `org.ruoyi.service.chat.impl.memory.PersistentChatMemoryStore`
- **职责**: 从数据库读取和存储消息

```java
// 从数据库获取历史消息
List<ChatMessage> messages = chatMessageService.getMessagesBySessionId(sessionId);
```

### 4. 配置体系

#### ChatMemoryProperties
配置文件前缀：`chat.memory`
```yaml
chat:
  memory:
    # 是否启用长期记忆
    enabled: true

    # 内存管理策略: message/token/hybrid
    strategy: hybrid

    # message 策略时的消息数上限
    max-messages: 20

    # token 策略时的 Token 上限（null 则根据模型自动获取）
    max-tokens: null

    # 预留给回复的 Token 数
    reserved-for-reply: 2000

    # 是否启用消息持久化
    persistence-enabled: true

    # 是否启用摘要压缩
    summarize-enabled: true

    # 触发摘要的 Token 使用比例（0.7 = 70%）
    summarize-token-ratio: 0.7

    # 触发摘要的最小消息数
    summarize-threshold: 10

    # 是否保护系统消息不被截断
    preserve-system-messages: true

    # 未知模型是否回退到消息数量策略
    fallback-to-message-strategy: true

    # 回退时的消息数量
    fallback-max-messages: 20
```

## 触发机制详解

### Token 限制检查 vs 摘要压缩

| 机制 | 触发条件 | 作用 | 性质 |
|------|----------|------|------|
| **Token 限制检查** | `totalTokens > maxTokens - reservedForReply` | 判断是否需要处理 | 检测 |
| **摘要压缩** | Token 超限 + 启用摘要 + 有模型 + 消息数 > 阈值 | 压缩旧消息为摘要 | 软处理 |
| **Token 截断** | 摘要后仍超限 或 未启用摘要 | 直接删除旧消息 | 硬处理 |

### 触发流程

```
每次对话调用 messages()
        ↓
从数据库查询历史消息
        ↓
计算当前 Token 总数
        ↓
┌─────────────────────────────────────┐
│ Token ≤ maxTokens - reservedForReply │ → 直接返回，不做处理
└─────────────────────────────────────┘
        ↓ (Token 超限)
┌─────────────────────────────────────┐
│ 尝试摘要压缩（软处理）                │
│ 条件：                               │
│   1. 启用摘要 (summarizeEnabled)     │
│   2. 有摘要模型 (summarizer != null) │
│   3. 消息数 > summarizeThreshold    │
│   4. Token 比例 ≥ summarizeTokenRatio│
└─────────────────────────────────────┘
        ↓
  摘要后 Token 还超限？
        ↓ 是
┌─────────────────────────────────────┐
│ Token 截断（硬处理）                  │
│ 从旧消息开始删除，直到 Token 在限制内 │
└─────────────────────────────────────┘
```

### 生产环境示例

以 GLM-4.5-AIR (131072 tokens) 为例：

```
maxTokens = 131072 (自动获取)
reservedForReply = 2000
有效上限 = 129072 tokens

摘要触发阈值 = 129072 × 70% = 90,350 tokens

当 Token > 90,350 且消息数 > 10 时：
  → 触发摘要压缩
  → 摘要后通常 < 129072，无需截断
```

## 摘要机制详解

### 摘要逻辑

**只摘要前半部分的消息，保留后半部分完整**：

```
原消息: [Msg1, Msg2, Msg3, Msg4, Msg5, Msg6, Msg7, Msg8, Msg9, Msg10]

         ↓ 分割（一半摘要，一半保留）

待摘要: [Msg1, Msg2, Msg3, Msg4, Msg5]  → 生成摘要
保留:   [Msg6, Msg7, Msg8, Msg9, Msg10] → 不处理

         ↓ 合并结果

[摘要消息, Msg6, Msg7, Msg8, Msg9, Msg10]  → 6 条消息
```

### 设计原因

| 方案 | 问题 |
|------|------|
| 摘要全部 | 丢失最近对话的完整上下文，AI 可能无法理解当前话题 |
| 摘要前半 | 保留最近对话的完整性，压缩早期对话为摘要 ✓ |

**最近的消息最重要**，所以保留后半部分不处理。

### 摘要模型智能映射

系统自动根据主模型选择合适的摘要模型：

| 主模型 | 摘要模型 | 说明 |
|--------|----------|------|
| glm-5 | glm-5-flash | 智谱最新轻量版 |
| glm-4.5-air | glm-4.5-air | 保持原模型 |
| glm-4 | glm-4-flash | 智谱便宜版本 |
| gpt-4 | gpt-4o-mini | OpenAI 最便宜 |
| claude | claude-3-5-haiku | Anthropic 轻量版 |
| deepseek | deepseek-chat | 本身便宜 |
| qwen | qwen-turbo | 阿里轻量版 |

### 免费摘要模型推荐

| 类型 | 模型 | 说明 |
|------|------|------|
| **智谱** | glm-4-flash | ¥0.1/百万token，有免费额度 |
| **Google** | gemini-2.0-flash | 免费额度大 |
| **本地 Ollama** | qwen2.5:7b | 完全免费，需本地部署 |
| **Groq** | llama-3.3-70b | 免费快速推理 |

### 智谱 API 地址修正

智谱 API 使用 OpenAI 兼容格式时，需要完整路径：
- 错误: `https://open.bigmodel.cn`
- 正确: `https://open.bigmodel.cn/api/paas/v4/`

系统会自动检测并修正智谱模型的 API 地址。

## 工作流程示例

### 场景 1: 正常对话（Token 未超限）

```
1. 用户发送消息
   ↓
2. 从数据库查询历史消息
   ↓
3. TokenCounter 计算当前 Token 数
   ↓
4. Token 数 < maxTokens - reservedForReply
   ↓
5. 直接返回所有消息，不做处理
```

### 场景 2: Token 超限 + 启用摘要

```
1. 用户发送消息，Token 累积
   ↓
2. Token 数 > maxTokens - reservedForReply
   ↓
3. 检查摘要条件：
   - summarizeEnabled = true
   - summarizer != null
   - messages.size() > summarizeThreshold
   - Token 比例 ≥ summarizeTokenRatio
   ↓
4. 调用 LLM 生成历史对话摘要（前半部分）
   ↓
5. 替换旧消息为摘要消息
   ↓
6. 摘要后 Token 仍超限？
   ↓ 是
7. 执行 Token 截断，删除最旧的消息
```

### 场景 3: 未知模型处理

```
1. 用户选择了一个新模型 "new-model-v1"
   ↓
2. ChatMemoryFactory 检查模型是否在 ModelTokenLimits 中
   ↓
3. 模型未知，检查 fallbackToMessageStrategy 配置
   ↓
4. 若 fallbackToMessageStrategy = true
   ↓
5. 使用 fallbackMaxMessages 条消息的固定数量策略
   ↓
6. 日志输出: "模型 [new-model-v1] 不在已知列表中，回退到固定消息数量策略"
```

## 日志输出示例

```
# 创建内存
创建 ChatMemory: strategy=hybrid, memoryId=12345
[Hybrid内存] 创建混合策略内存: maxTokens=131072, summarizeEnabled=true, summarizeTokenRatio=0.7

# 摘要模型创建
[摘要模型] 开始创建，model=glm-4.5-air, apiKey=已配置, apiHost=https://open.bigmodel.cn
[摘要模型] 修正智谱 API 地址: https://open.bigmodel.cn → https://open.bigmodel.cn/api/paas/v4/
[摘要模型] 智能映射: glm-4.5-air → glm-4.5-air

# 正常状态
[Token内存管理] 会话=12345, 消息数=15, 当前Token=8500, Token上限=131072, 预留回复空间=2000

# Token 超限
Token 数量 127000 超过限制 129072，开始处理

# 摘要触发
[摘要触发判断] Token使用比例: 79.5%, 触发阈值: 70.0%, 是否触发: true
[摘要压缩] 开始摘要: 原消息数=25, 待摘要=12, 保留=13
[摘要压缩] 生成摘要成功: 本次对话主要讨论了用户的项目需求，包括功能设计和技术选型...
[摘要压缩] 完成: 原消息数=25 → 新消息数=14

# Token 截断
[Token截断] 原消息数=30 → 截断后=18, Token: 130000 → 124500
```

## 性能考虑

### 当前方案

| 操作 | 说明 |
|------|------|
| **读取** | 每次对话都从数据库查询全部历史消息 |
| **写入** | 每条新消息单独 INSERT 到数据库 |
| **摘要** | 只在内存中处理，不更新数据库 |
| **截断** | 只在内存中处理，不更新数据库 |

### 优化建议

| 场景 | 建议 |
|------|------|
| 当前规模（用户少） | 现有方案够用 |
| 中等规模 | 加 Redis 缓存 |
| 大规模 | Redis + 分页查询 + 异步摘要 |

### Redis 缓存方案（推荐）

```
首次查询 → 数据库 → 写入 Redis
后续查询 → Redis → 命中则返回
新消息 → 写入数据库 + 更新 Redis
```

## 安全考虑

1. **Token 估算**: 使用中英文混合估算，不依赖外部 Tokenizer，性能高
2. **摘要开销**: 摘要会增加 LLM API 调用，建议仅在长对话场景启用
3. **系统消息保护**: 系统消息不会被截断，确保 AI 角色设定不丢失
4. **回复空间预留**: 预留 reservedForReply tokens 给 AI 回复，避免上下文占满
5. **未知模型处理**: 回退到固定消息数量策略，避免使用默认 4096 导致截断过多
6. **数据完整性**: 摘要和截断不修改数据库，保留完整历史记录

## 模型 Token 限制参考

| 模型系列 | 代表模型 | Token 限制 |
|----------|----------|------------|
| GPT-4.1 | gpt-4.1, gpt-4.1-mini | 1,047,576 |
| GPT-4o | gpt-4o, gpt-4o-mini | 128,000 |
| o 系列 | o1, o3, o4-mini | 200,000 |
| DeepSeek | deepseek-chat, deepseek-reasoner | 64,000 |
| GLM-5 | glm-5, glm-5.1 | 128,000 |
| GLM-4-long | glm-4-long | 1,024,000 |
| Qwen3 | qwen3, qwen3-235b | 128,000 |
| Qwen-long | qwen-long | 1,000,000 |
| Claude 4 | claude-4-opus, claude-4-sonnet | 200,000 |
| Claude 3.5 | claude-3.5-sonnet | 200,000 |
| Gemini 2.5 | gemini-2.5-pro, gemini-2.5-flash | 1,048,576 |
| Doubao 1.5 | doubao-1.5-pro, doubao-1.5-thinking | 256,000 |
| Grok 3 | grok-3, grok-3-mini | 131,072 |
| Llama 3.1/3.2 | llama3.1, llama3.2 | 131,072 |

## 扩展指南

### 添加新模型

在 `ModelTokenLimits.java` 的 `TOKEN_LIMITS` Map 中添加：

```java
TOKEN_LIMITS.put("new-model-name", 64000);
```

### 自定义摘要模型映射

在 `ChatServiceFacade.getSmartSummarizerModel()` 中添加：

```java
if (model.contains("new-model")) return "new-model-lite";
```

### 自定义摘要策略

继承 `TokenBasedChatMemory` 并重写 `summarizeOldMessages()` 方法。

### 自定义 Token 计数

实现 `TokenCounter` 接口或继承现有类，重写 `countTokens()` 方法。
