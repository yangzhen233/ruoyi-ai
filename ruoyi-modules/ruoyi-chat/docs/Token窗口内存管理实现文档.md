# Token 窗口内存管理实现文档

## 概述

本次实现为聊天模块新增了基于 Token 的上下文管理策略，支持三种内存管理方式：

| 策略 | 说明 | 适用场景 |
|------|------|----------|
| `message` | 固定消息数量（原有实现） | 简单对话场景 |
| `token` | 基于 Token 数量截断 | 长对话、多模型适配 |
| `hybrid` | Token 限制 + 摘要压缩 | 需要保留历史要点的场景 |

---

## 新增文件

### 1. ModelTokenLimits.java

**路径**: `ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/service/chat/impl/memory/ModelTokenLimits.java`

**功能**: 模型 Token 限制映射表，维护 50+ 常用模型的上下文限制。

**支持模型**:
- OpenAI: GPT-4o, GPT-4, GPT-3.5-turbo 等
- DeepSeek: deepseek-chat, deepseek-coder 等
- 智谱: GLM-4, GLM-4-flash 等
- 通义千问: qwen-max, qwen-plus 等
- Claude: claude-3-opus, claude-3-sonnet 等
- Ollama 本地模型: llama3, mistral, qwen2 等

**核心方法**:

```java
// 获取模型的 Token 限制
int limit = ModelTokenLimits.getLimit("gpt-4o");  // 返回 128000

// 获取输入 Token 上限（预留回复空间）
int inputLimit = ModelTokenLimits.getInputLimit("gpt-4o", 2000);  // 返回 126000
```

---

### 2. TokenCounter.java

**路径**: `ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/service/chat/impl/memory/TokenCounter.java`

**功能**: Token 计数器，支持中英文混合估算。

**估算规则**:
- 中文: 约 2 字符 = 1 token
- 英文: 约 4 字符 = 1 token
- 每条消息固定开销: 4 tokens（格式标记）

**核心方法**:

```java
TokenCounter counter = new TokenCounter();

// 计算文本 Token 数
int tokens = counter.countTokens("你好世界 Hello World");

// 计算消息列表 Token 数
int total = counter.countMessages(messages);
```

---

### 3. TokenBasedChatMemory.java

**路径**: `ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/service/chat/impl/memory/TokenBasedChatMemory.java`

**功能**: 基于 Token 的聊天内存实现，支持：
- Token 窗口截断
- 可选的摘要压缩
- 系统消息保护

**核心特性**:

```java
TokenBasedChatMemory memory = TokenBasedChatMemory.builder()
    .memoryId(sessionId)
    .maxTokens(8000)                    // 最大 Token 数
    .tokenCounter(tokenCounter)         // Token 计数器
    .store(persistentStore)             // 持久化存储
    .summarizeEnabled(true)             // 启用摘要
    .summarizeThreshold(30)             // 摘要触发阈值
    .summarizer(llmModel)               // 摘要模型
    .preserveSystemMessages(true)       // 保护系统消息
    .reservedForReply(2000)             // 预留回复空间
    .build();
```

---

### 4. ChatMemoryFactory.java

**路径**: `ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/service/chat/impl/memory/ChatMemoryFactory.java`

**功能**: ChatMemory 工厂类，根据配置创建不同策略的内存实例。

**核心方法**:

```java
// 创建默认内存
ChatMemory memory = chatMemoryFactory.create(sessionId, chatModelVo);

// 创建带摘要功能的内存
ChatMemory memory = chatMemoryFactory.create(sessionId, chatModelVo, summarizerModel);

// 获取模型 Token 限制
int limit = chatMemoryFactory.getModelTokenLimit("gpt-4o");
```

---

### 5. ChatMemoryConfig.java

**路径**: `ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/service/chat/impl/memory/ChatMemoryConfig.java`

**功能**: Spring 配置类，注入必要的 Bean。

---

## 修改文件

### 1. ChatMemoryProperties.java

**路径**: `ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/service/chat/impl/memory/ChatMemoryProperties.java`

**新增配置项**:

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `strategy` | String | `token` | 内存管理策略: message/token/hybrid |
| `maxTokens` | Integer | null | 最大 Token 数（null 则自动获取） |
| `reservedForReply` | Integer | 2000 | 预留给回复的 Token 数 |
| `preserveSystemMessages` | Boolean | true | 是否保护系统消息不被截断 |

---

### 2. ChatServiceFacade.java

**路径**: `ruoyi-modules/ruoyi-chat/src/main/java/org/ruoyi/service/chat/impl/ChatServiceFacade.java`

**修改内容**:

1. 注入 `ChatMemoryFactory`
2. 修改 `createChatMemory` 方法使用工厂类
3. 保留原有方法并标记 `@Deprecated`

```java
// 新方法：推荐使用
private ChatMemory createChatMemory(Object memoryId, ChatModelVo model) {
    return memoryCache.computeIfAbsent(memoryId, key -> {
        return chatMemoryFactory.create(memoryId, model);
    });
}

// 旧方法：已废弃
@Deprecated
private ChatMemory createChatMemory(Object memoryId) {
    log.warn("建议传入模型配置以启用 Token 窗口管理");
    return createChatMemory(memoryId, null);
}

// 原有实现：保留兼容
@Deprecated
private MessageWindowChatMemory createFixedMessageMemory(Object memoryId, int maxMessages) {
    // 原有的固定消息数实现
}
```

---

## 配置说明

### application.yml 配置示例

```yaml
chat:
  memory:
    # 是否启用长期记忆
    enabled: true

    # 内存管理策略
    # - message: 固定消息数量（原有实现）
    # - token: 基于 Token 数量截断
    # - hybrid: Token 限制 + 摘要压缩
    strategy: token

    # message 策略时的消息数上限
    max-messages: 20

    # token 策略时的 Token 上限（null 则根据模型自动获取）
    max-tokens: null

    # 预留给回复的 Token 数
    reserved-for-reply: 2000

    # 是否启用消息持久化
    persistence-enabled: true

    # 是否启用摘要压缩（hybrid 策略）
    summarize-enabled: false

    # 触发摘要的消息数阈值
    summarize-threshold: 30

    # 是否保护系统消息不被截断
    preserve-system-messages: true
```

---

## 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                      ChatServiceFacade                       │
│                         (业务层)                             │
└─────────────────────────┬───────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                    ChatMemoryFactory                         │
│                      (工厂层)                                │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │   message   │  │    token    │  │   hybrid    │         │
│  │   策略      │  │    策略     │  │    策略     │         │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘         │
└─────────┼────────────────┼────────────────┼─────────────────┘
          │                │                │
          ▼                ▼                ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│MessageWindow    │ │TokenBased       │ │TokenBased       │
│ChatMemory       │ │ChatMemory       │ │ChatMemory       │
│(原有实现)        │ │(Token截断)      │ │(+摘要压缩)      │
└─────────────────┘ └─────────────────┘ └─────────────────┘
          │                │                │
          └────────────────┼────────────────┘
                           ▼
              ┌─────────────────────────┐
              │ PersistentChatMemoryStore│
              │     (持久化存储)          │
              └─────────────────────────┘
```

---

## 使用示例

### 1. 基本使用（自动适配模型）

```java
@Service
@RequiredArgsConstructor
public class MyChatService {

    private final ChatMemoryFactory chatMemoryFactory;

    public void chat(Long sessionId, ChatModelVo model) {
        // 自动根据模型配置创建内存
        ChatMemory memory = chatMemoryFactory.create(sessionId, model);

        // 获取历史消息（自动按 Token 截断）
        List<ChatMessage> messages = memory.messages();
    }
}
```

### 2. 启用摘要压缩

```java
// 创建带摘要功能的内存
ChatModel summarizer = createSummarizerModel();
ChatMemory memory = chatMemoryFactory.create(sessionId, chatModelVo, summarizer);
```

### 3. 手动创建 Token 窗口内存

```java
TokenBasedChatMemory memory = TokenBasedChatMemory.builder()
    .memoryId(sessionId)
    .maxTokens(8000)
    .tokenCounter(new TokenCounter())
    .store(persistentStore)
    .preserveSystemMessages(true)
    .build();
```

### 4. 获取模型 Token 限制

```java
// 获取完整限制
int limit = ModelTokenLimits.getLimit("gpt-4o");        // 128000
int limit2 = ModelTokenLimits.getLimit("gpt-3.5-turbo"); // 16385

// 获取输入限制（预留回复空间）
int inputLimit = ModelTokenLimits.getInputLimit("gpt-4o", 2000); // 126000
```

---

## 模型 Token 限制参考

| 模型 | Token 限制 |
|------|-----------|
| GPT-4o / GPT-4o-mini | 128,000 |
| GPT-4-turbo | 128,000 |
| GPT-4 | 8,192 |
| GPT-3.5-turbo | 16,385 |
| DeepSeek-chat/coder | 64,000 |
| GLM-4 | 128,000 |
| GLM-4-long | 1,024,000 |
| Qwen-max/plus | 32,768 |
| Qwen-long | 1,000,000 |
| Claude-3-opus/sonnet/haiku | 200,000 |
| Llama3.1/3.2 | 131,072 |

---

## 注意事项

1. **策略选择**:
   - 简单对话场景可继续使用 `message` 策略
   - 生产环境推荐使用 `token` 策略，防止超出模型限制
   - 需要保留历史要点时使用 `hybrid` 策略

2. **摘要压缩**:
   - 启用摘要会增加额外的 LLM API 调用
   - 建议仅在长对话场景启用

3. **模型适配**:
   - 新增模型时，可在 `ModelTokenLimits` 中添加映射
   - 或在数据库 `chat_model` 表中配置 `max_context_tokens` 字段

4. **向后兼容**:
   - 原有的固定消息数实现已保留
   - 旧代码可继续使用，但建议迁移到新实现


