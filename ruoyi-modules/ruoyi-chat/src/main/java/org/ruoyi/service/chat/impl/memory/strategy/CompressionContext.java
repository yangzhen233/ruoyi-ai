package org.ruoyi.service.chat.impl.memory.strategy;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import lombok.Builder;
import lombok.Data;
import org.ruoyi.service.chat.impl.memory.TokenCounter;

import java.util.List;

/**
 * 压缩上下文
 * 包含压缩所需的所有输入信息
 *
 * @author yang
 * @date 2026-04-29
 */
@Data
@Builder
public class CompressionContext {

    /**
     * 内存 ID（通常是会话 ID）
     */
    private Object memoryId;

    /**
     * 原始消息列表
     */
    private List<ChatMessage> messages;

    /**
     * 当前 Token 数量
     */
    private int currentTokens;

    /**
     * Token 上限
     */
    private int maxTokens;

    /**
     * 预留给回复的 Token 数
     */
    @Builder.Default
    private int reservedForReply = 2000;

    /**
     * 有效 Token 上限（maxTokens - reservedForReply）
     */
    public int getEffectiveMaxTokens() {
        return maxTokens - reservedForReply;
    }

    /**
     * Token 使用比例
     */
    public double getUsageRatio() {
        return (double) currentTokens / getEffectiveMaxTokens();
    }

    /**
     * 是否超过 Token 限制
     */
    public boolean isOverLimit() {
        return currentTokens > getEffectiveMaxTokens();
    }

    /**
     * 超出的 Token 数量
     */
    public int getExcessTokens() {
        return Math.max(0, currentTokens - getEffectiveMaxTokens());
    }

    /**
     * 摘要模型（可选，用于摘要策略）
     */
    private ChatModel summarizer;

    /**
     * Token 计数器
     */
    private TokenCounter tokenCounter;

    /**
     * 是否保留系统消息
     */
    @Builder.Default
    private boolean preserveSystemMessages = true;

    /**
     * 摘要触发阈值 - Token 使用比例
     */
    @Builder.Default
    private double summarizeTokenRatio = 0.7;

    /**
     * 摘要触发阈值 - 消息数量
     */
    @Builder.Default
    private int summarizeThreshold = 10;
}
