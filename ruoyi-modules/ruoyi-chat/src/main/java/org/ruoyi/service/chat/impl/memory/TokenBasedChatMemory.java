package org.ruoyi.service.chat.impl.memory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;
import org.ruoyi.service.chat.impl.memory.strategy.CompressionContext;
import org.ruoyi.service.chat.impl.memory.strategy.CompressionResult;
import org.ruoyi.service.chat.impl.memory.strategy.CompressionStrategyManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 基于 Token 的聊天内存管理
 * 支持 Token 窗口限制和可选的摘要压缩
 *
 * @author yang
 * @date 2026-04-27
 */
@Slf4j
public class TokenBasedChatMemory implements ChatMemory {

    /**
     * 内存 ID（通常是会话 ID）
     */
    private final Object memoryId;

    /**
     * 最大 Token 数
     */
    private final int maxTokens;

    /**
     * Token 计数器
     */
    private final TokenCounter tokenCounter;

    /**
     * 持久化存储
     */
    private final ChatMemoryStore store;

    /**
     * 是否启用摘要压缩
     */
    private final boolean summarizeEnabled;

    /**
     * 触发摘要的 Token 使用比例（如 0.7 表示 70%）
     */
    private final double summarizeTokenRatio;

    /**
     * 触发摘要的消息数量阈值（避免消息太少时摘要无意义）
     */
    private final int summarizeThreshold;

    /**
     * 用于摘要的 LLM 模型（可选）
     */
    private final ChatModel summarizer;

    /**
     * 是否保留系统消息（不被截断）
     */
    private final boolean preserveSystemMessages;

    /**
     * 预留给回复的 Token 数
     */
    private final int reservedForReply;

    /**
     * 压缩策略管理器（可选）
     * 如果设置，优先使用策略管理器进行压缩
     */
    private final CompressionStrategyManager strategyManager;

    /**
     * 构造函数
     */
    private TokenBasedChatMemory(Builder builder) {
        this.memoryId = builder.memoryId;
        this.maxTokens = builder.maxTokens;
        this.tokenCounter = builder.tokenCounter != null ? builder.tokenCounter : new TokenCounter();
        this.store = builder.store;
        this.summarizeEnabled = builder.summarizeEnabled;
        this.summarizeTokenRatio = builder.summarizeTokenRatio;
        this.summarizeThreshold = builder.summarizeThreshold;
        this.summarizer = builder.summarizer;
        this.preserveSystemMessages = builder.preserveSystemMessages;
        this.reservedForReply = builder.reservedForReply;
        this.strategyManager = builder.strategyManager;
    }

    @Override
    public Object id() {
        return memoryId;
    }

    @Override
    public void add(ChatMessage message) {
        List<ChatMessage> messages = new ArrayList<>(messages());
        messages.add(message);
        store.updateMessages(memoryId, messages);
    }

    @Override
    public List<ChatMessage> messages() {
        List<ChatMessage> messages = store != null ? store.getMessages(memoryId) : new ArrayList<>();

        if (messages == null || messages.isEmpty()) {
            return messages;
        }

        int totalTokens = tokenCounter.countMessages(messages);
        int effectiveMaxTokens = maxTokens - reservedForReply;

        // 输出当前状态日志
        log.info("[Token内存管理] 会话={}, 消息数={}, 当前Token={}, Token上限={}, 预留回复空间={}",
                memoryId, messages.size(), totalTokens, maxTokens, reservedForReply);

        // 未超限，直接返回
        if (totalTokens <= effectiveMaxTokens) {
            log.debug("Token 数量 {} 在限制 {} 内，无需处理", totalTokens, effectiveMaxTokens);
            return messages;
        }

        log.info("Token 数量 {} 超过限制 {}，开始处理", totalTokens, effectiveMaxTokens);

        // 优先使用策略管理器
        if (strategyManager != null) {
            CompressionContext context = buildCompressionContext(messages, totalTokens);
            CompressionResult result = strategyManager.execute(context);
            if (result.isSuccess()) {
                log.info("[策略框架] 压缩成功: 策略={}, Token: {} → {}, 消息数: {} → {}",
                        result.getStrategyName(), result.getOriginalTokens(), result.getCompressedTokens(),
                        result.getOriginalMessageCount(), result.getCompressedMessageCount());
                return result.getMessages();
            } else {
                log.warn("[策略框架] 压缩失败: {}, 回退到原有逻辑", result.getErrorMessage());
            }
        }

        // 回退到原有逻辑
        return legacyCompress(messages, totalTokens, effectiveMaxTokens);
    }

    /**
     * 构建压缩上下文
     */
    private CompressionContext buildCompressionContext(List<ChatMessage> messages, int totalTokens) {
        return CompressionContext.builder()
                .memoryId(memoryId)
                .messages(messages)
                .currentTokens(totalTokens)
                .maxTokens(maxTokens)
                .reservedForReply(reservedForReply)
                .summarizer(summarizer)
                .tokenCounter(tokenCounter)
                .preserveSystemMessages(preserveSystemMessages)
                .summarizeTokenRatio(summarizeTokenRatio)
                .summarizeThreshold(summarizeThreshold)
                .build();
    }

    /**
     * 原有压缩逻辑（回退方案）
     */
    private List<ChatMessage> legacyCompress(List<ChatMessage> messages, int totalTokens, int effectiveMaxTokens) {
        // 尝试摘要压缩
        // 条件: 1. 启用摘要 2. 有摘要模型 3. 消息数足够 4. Token 使用超过阈值比例
        boolean shouldSummarize = summarizeEnabled
                && summarizer != null
                && messages.size() > summarizeThreshold
                && shouldTriggerSummarize(totalTokens, effectiveMaxTokens);

        if (shouldSummarize) {
            messages = summarizeOldMessages(messages);
            totalTokens = tokenCounter.countMessages(messages);

            if (totalTokens <= effectiveMaxTokens) {
                log.info("摘要后 Token 数量 {} 在限制内", totalTokens);
                return messages;
            }
        }

        // 按 Token 截断
        int originalSize = messages.size();
        messages = truncateByTokens(messages, effectiveMaxTokens);
        log.info("[Token截断] 原消息数={} → 截断后={}, Token: {} → {}",
                originalSize, messages.size(), totalTokens, tokenCounter.countMessages(messages));

        return messages;
    }

    /**
     * 判断是否应该触发摘要
     * 基于 Token 使用比例判断，而非仅靠消息数量
     */
    private boolean shouldTriggerSummarize(int currentTokens, int maxTokens) {
        double usageRatio = (double) currentTokens / maxTokens;
        boolean shouldTrigger = usageRatio >= summarizeTokenRatio;
        log.info("[摘要触发判断] Token使用比例: {}%, 触发阈值: {}%, 是否触发: {}",
                String.format("%.1f", usageRatio * 100),
                String.format("%.1f", summarizeTokenRatio * 100),
                shouldTrigger);
        return shouldTrigger;
    }

    /**
     * 摘要旧消息
     */
    private List<ChatMessage> summarizeOldMessages(List<ChatMessage> messages) {
        if (summarizer == null) {
            log.debug("摘要模型未配置，跳过摘要");
            return messages;
        }

        try {
            // 分离系统消息和普通消息
            List<ChatMessage> systemMessages = new ArrayList<>();
            List<ChatMessage> regularMessages = new ArrayList<>();

            for (ChatMessage msg : messages) {
                if (msg instanceof SystemMessage) {
                    systemMessages.add(msg);
                } else {
                    regularMessages.add(msg);
                }
            }

            // 如果普通消息太少，不进行摘要
            if (regularMessages.size() < summarizeThreshold) {
                log.debug("消息数 {} 小于阈值 {}，跳过摘要", regularMessages.size(), summarizeThreshold);
                return messages;
            }

            // 选择要摘要的消息（前半部分）
            int summarizeCount = regularMessages.size() / 2;
            List<ChatMessage> toSummarize = regularMessages.subList(0, summarizeCount);
            List<ChatMessage> toKeep = regularMessages.subList(summarizeCount, regularMessages.size());

            log.info("[摘要压缩] 开始摘要: 原消息数={}, 待摘要={}, 保留={}",
                    messages.size(), summarizeCount, toKeep.size());

            // 构建摘要提示
            StringBuilder summaryPrompt = new StringBuilder();
            summaryPrompt.append("请用简洁的语言总结以下对话的关键信息，保留重要的上下文和用户偏好：\n\n");

            for (ChatMessage msg : toSummarize) {
                summaryPrompt.append(extractText(msg)).append("\n");
            }

            // 调用 LLM 生成摘要
            String summary = summarizer.chat(summaryPrompt.toString());
            log.info("[摘要压缩] 生成摘要成功: {}...", summary.substring(0, Math.min(100, summary.length())));

            // 构建新的消息列表
            List<ChatMessage> result = new ArrayList<>(systemMessages);
            result.add(SystemMessage.from("【历史对话摘要】" + summary));
            result.addAll(toKeep);

            log.info("[摘要压缩] 完成: 原消息数={} → 新消息数={}", messages.size(), result.size());

            return result;

        } catch (Exception e) {
            log.warn("[摘要压缩] 失败: {}", e.getMessage());
            return messages;
        }
    }

    /**
     * 从消息中提取文本内容
     */
    private String extractText(ChatMessage message) {
        if (message instanceof AiMessage aiMessage) {
            return aiMessage.text();
        } else if (message instanceof UserMessage userMessage) {
            return userMessage.singleText();
        } else if (message instanceof SystemMessage systemMessage) {
            return systemMessage.text();
        } else if (message instanceof ToolExecutionResultMessage toolMessage) {
            return toolMessage.text();
        }
        return "";
    }

    /**
     * 按 Token 数截断消息
     */
    private List<ChatMessage> truncateByTokens(List<ChatMessage> messages, int maxTokens) {
        // 分离系统消息和普通消息
        List<ChatMessage> systemMessages = new ArrayList<>();
        List<ChatMessage> regularMessages = new ArrayList<>();

        for (ChatMessage msg : messages) {
            if (preserveSystemMessages && msg instanceof SystemMessage) {
                systemMessages.add(msg);
            } else {
                regularMessages.add(msg);
            }
        }

        // 计算系统消息占用的 Token
        int systemTokens = tokenCounter.countMessages(systemMessages);
        int availableTokens = maxTokens - systemTokens - tokenCounter.countMessages(List.of());

        if (availableTokens <= 0) {
            log.warn("系统消息已占用全部 Token 空间");
            return systemMessages;
        }

        // 从最新的消息开始保留
        List<ChatMessage> keptMessages = new ArrayList<>();
        int currentTokens = 0;

        for (int i = regularMessages.size() - 1; i >= 0; i--) {
            ChatMessage msg = regularMessages.get(i);
            int msgTokens = tokenCounter.countMessage(msg);

            if (currentTokens + msgTokens <= availableTokens) {
                keptMessages.add(0, msg); // 添加到头部保持顺序
                currentTokens += msgTokens;
            } else {
                break;
            }
        }

        // 合并结果
        List<ChatMessage> result = new ArrayList<>(systemMessages);
        result.addAll(keptMessages);

        log.debug("截断完成: 系统{}条 + 普通{}条, 共{} tokens",
            systemMessages.size(), keptMessages.size(), tokenCounter.countMessages(result));

        return result;
    }

    @Override
    public void clear() {
        if (store != null) {
            store.deleteMessages(memoryId);
        }
    }

    /**
     * 创建构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 从模型配置创建
     */
    public static TokenBasedChatMemory fromModel(Object memoryId, ChatModelVo model,
                                                   ChatMemoryStore store, ChatModel summarizer) {
        int maxTokens = ModelTokenLimits.getLimit(model.getModelName());
        int inputLimit = ModelTokenLimits.getInputLimit(model.getModelName(), 2000);

        return builder()
            .memoryId(memoryId)
            .maxTokens(inputLimit)
            .store(store)
            .summarizer(summarizer)
            .summarizeEnabled(false) // 默认关闭摘要
            .summarizeThreshold(30)
            .preserveSystemMessages(true)
            .reservedForReply(2000)
            .build();
    }

    /**
     * 从模型配置创建（带策略管理器）
     */
    public static TokenBasedChatMemory fromModel(Object memoryId, ChatModelVo model,
                                                   ChatMemoryStore store, ChatModel summarizer,
                                                   CompressionStrategyManager strategyManager) {
        int maxTokens = ModelTokenLimits.getLimit(model.getModelName());
        int inputLimit = ModelTokenLimits.getInputLimit(model.getModelName(), 2000);

        return builder()
            .memoryId(memoryId)
            .maxTokens(inputLimit)
            .store(store)
            .summarizer(summarizer)
            .summarizeEnabled(false) // 默认关闭摘要
            .summarizeThreshold(30)
            .preserveSystemMessages(true)
            .reservedForReply(2000)
            .strategyManager(strategyManager)
            .build();
    }

    /**
     * 构建器
     */
    public static class Builder {
        private Object memoryId;
        private int maxTokens = 4096;
        private TokenCounter tokenCounter;
        private ChatMemoryStore store;
        private boolean summarizeEnabled = false;
        private double summarizeTokenRatio = 0.7;
        private int summarizeThreshold = 10;
        private ChatModel summarizer;
        private boolean preserveSystemMessages = true;
        private int reservedForReply = 2000;
        private CompressionStrategyManager strategyManager;

        public Builder memoryId(Object memoryId) {
            this.memoryId = memoryId;
            return this;
        }

        public Builder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder tokenCounter(TokenCounter tokenCounter) {
            this.tokenCounter = tokenCounter;
            return this;
        }

        public Builder store(ChatMemoryStore store) {
            this.store = store;
            return this;
        }

        public Builder summarizeEnabled(boolean summarizeEnabled) {
            this.summarizeEnabled = summarizeEnabled;
            return this;
        }

        public Builder summarizeTokenRatio(double summarizeTokenRatio) {
            this.summarizeTokenRatio = summarizeTokenRatio;
            return this;
        }

        public Builder summarizeThreshold(int summarizeThreshold) {
            this.summarizeThreshold = summarizeThreshold;
            return this;
        }

        public Builder summarizer(ChatModel summarizer) {
            this.summarizer = summarizer;
            return this;
        }

        public Builder preserveSystemMessages(boolean preserveSystemMessages) {
            this.preserveSystemMessages = preserveSystemMessages;
            return this;
        }

        public Builder reservedForReply(int reservedForReply) {
            this.reservedForReply = reservedForReply;
            return this;
        }

        public Builder strategyManager(CompressionStrategyManager strategyManager) {
            this.strategyManager = strategyManager;
            return this;
        }

        public TokenBasedChatMemory build() {
            Objects.requireNonNull(memoryId, "memoryId 不能为空");
            return new TokenBasedChatMemory(this);
        }
    }
}
