package org.ruoyi.service.chat.impl.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;

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
     * 触发摘要的消息数量阈值
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
     * 构造函数
     */
    private TokenBasedChatMemory(Builder builder) {
        this.memoryId = builder.memoryId;
        this.maxTokens = builder.maxTokens;
        this.tokenCounter = builder.tokenCounter != null ? builder.tokenCounter : new TokenCounter();
        this.store = builder.store;
        this.summarizeEnabled = builder.summarizeEnabled;
        this.summarizeThreshold = builder.summarizeThreshold;
        this.summarizer = builder.summarizer;
        this.preserveSystemMessages = builder.preserveSystemMessages;
        this.reservedForReply = builder.reservedForReply;
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

        // 未超限，直接返回
        if (totalTokens <= effectiveMaxTokens) {
            log.debug("Token 数量 {} 在限制 {} 内，无需处理", totalTokens, effectiveMaxTokens);
            return messages;
        }

        log.info("Token 数量 {} 超过限制 {}，开始处理", totalTokens, effectiveMaxTokens);

        // 尝试摘要压缩
        if (summarizeEnabled && summarizer != null && messages.size() > summarizeThreshold) {
            messages = summarizeOldMessages(messages);
            totalTokens = tokenCounter.countMessages(messages);

            if (totalTokens <= effectiveMaxTokens) {
                log.info("摘要后 Token 数量 {} 在限制内", totalTokens);
                return messages;
            }
        }

        // 按 Token 截断
        messages = truncateByTokens(messages, effectiveMaxTokens);
        log.info("截断后消息数: {}, Token 数: {}", messages.size(), tokenCounter.countMessages(messages));

        return messages;
    }

    /**
     * 摘要旧消息
     */
    private List<ChatMessage> summarizeOldMessages(List<ChatMessage> messages) {
        if (summarizer == null) {
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
                return messages;
            }

            // 选择要摘要的消息（前半部分）
            int summarizeCount = regularMessages.size() / 2;
            List<ChatMessage> toSummarize = regularMessages.subList(0, summarizeCount);
            List<ChatMessage> toKeep = regularMessages.subList(summarizeCount, regularMessages.size());

            // 构建摘要提示
            StringBuilder summaryPrompt = new StringBuilder();
            summaryPrompt.append("请用简洁的语言总结以下对话的关键信息，保留重要的上下文和用户偏好：\n\n");

            for (ChatMessage msg : toSummarize) {
                summaryPrompt.append(msg.text()).append("\n");
            }

            // 调用 LLM 生成摘要
            String summary = summarizer.chat(summaryPrompt.toString());
            log.info("生成摘要: {}...", summary.substring(0, Math.min(100, summary.length())));

            // 构建新的消息列表
            List<ChatMessage> result = new ArrayList<>(systemMessages);
            result.add(SystemMessage.from("【历史对话摘要】" + summary));
            result.addAll(toKeep);

            return result;

        } catch (Exception e) {
            log.warn("摘要生成失败: {}", e.getMessage());
            return messages;
        }
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
     * 构建器
     */
    public static class Builder {
        private Object memoryId;
        private int maxTokens = 4096;
        private TokenCounter tokenCounter;
        private ChatMemoryStore store;
        private boolean summarizeEnabled = false;
        private int summarizeThreshold = 30;
        private ChatModel summarizer;
        private boolean preserveSystemMessages = true;
        private int reservedForReply = 2000;

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

        public TokenBasedChatMemory build() {
            Objects.requireNonNull(memoryId, "memoryId 不能为空");
            return new TokenBasedChatMemory(this);
        }
    }
}
