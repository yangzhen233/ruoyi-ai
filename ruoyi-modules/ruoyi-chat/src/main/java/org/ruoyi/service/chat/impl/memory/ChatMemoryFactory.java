package org.ruoyi.service.chat.impl.memory;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;
import org.ruoyi.service.chat.impl.memory.strategy.CompressionStrategyManager;
import org.springframework.stereotype.Component;

/**
 * ChatMemory 工厂
 * 根据配置创建不同策略的 ChatMemory 实例
 *
 * @author yang
 * @date 2026-04-27
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMemoryFactory {

    private final ChatMemoryProperties properties;
    private final PersistentChatMemoryStore persistentStore;
    private final TokenCounter tokenCounter;
    private final CompressionStrategyManager strategyManager;

    /**
     * 创建 ChatMemory 实例
     *
     * @param memoryId 内存 ID（通常是会话 ID）
     * @param model    模型配置
     * @return ChatMemory 实例
     */
    public ChatMemory create(Object memoryId, ChatModelVo model) {
        return create(memoryId, model, null);
    }

    /**
     * 创建 ChatMemory 实例（带摘要模型）
     *
     * @param memoryId   内存 ID
     * @param model      模型配置
     * @param summarizer 用于摘要的 LLM 模型（可选）
     * @return ChatMemory 实例
     */
    public ChatMemory create(Object memoryId, ChatModelVo model, ChatModel summarizer) {
        if (!properties.getEnabled()) {
            log.debug("长期记忆已禁用");
            return null;
        }

        String strategy = properties.getStrategy();
        log.info("创建 ChatMemory: strategy={}, memoryId={}", strategy, memoryId);

        // 检查模型是否已知，未知模型回退到消息数量策略
        if (Boolean.TRUE.equals(properties.getFallbackToMessageStrategy())
                && ("token".equalsIgnoreCase(strategy) || "hybrid".equalsIgnoreCase(strategy))) {
            if (model != null && model.getModelName() != null) {
                int tokenLimit = ModelTokenLimits.getLimitOrUnknown(model.getModelName());
                if (tokenLimit == ModelTokenLimits.UNKNOWN_LIMIT) {
                    log.info("模型 [{}] 不在已知列表中，回退到固定消息数量策略 (maxMessages={})",
                            model.getModelName(), properties.getFallbackMaxMessages());
                    return createFallbackMessageMemory(memoryId);
                }
            }
        }

        return switch (strategy.toLowerCase()) {
            case "message" -> createMessageBasedMemory(memoryId);
            case "token" -> createTokenBasedMemory(memoryId, model, summarizer);
            case "hybrid" -> createHybridMemory(memoryId, model, summarizer);
            default -> {
                log.warn("未知的内存策略: {}, 使用默认 token 策略", strategy);
                yield createTokenBasedMemory(memoryId, model, summarizer);
            }
        };
    }

    /**
     * 创建基于消息数量的内存（原有策略）
     */
    private ChatMemory createMessageBasedMemory(Object memoryId) {
        int maxMessages = properties.getMaxMessages();
        log.debug("创建消息窗口内存: maxMessages={}", maxMessages);

        return MessageWindowChatMemory.builder()
            .id(memoryId)
            .maxMessages(maxMessages)
            .chatMemoryStore(persistentStore)
            .build();
    }

    /**
     * 创建回退的消息数量内存（用于未知模型）
     */
    private ChatMemory createFallbackMessageMemory(Object memoryId) {
        int maxMessages = properties.getFallbackMaxMessages() != null
                ? properties.getFallbackMaxMessages()
                : 20;
        log.debug("创建回退消息窗口内存: maxMessages={}", maxMessages);

        return MessageWindowChatMemory.builder()
            .id(memoryId)
            .maxMessages(maxMessages)
            .chatMemoryStore(persistentStore)
            .build();
    }

    /**
     * 创建基于 Token 的内存
     */
    private ChatMemory createTokenBasedMemory(Object memoryId, ChatModelVo model, ChatModel summarizer) {
        int maxTokens = resolveMaxTokens(model);
        int reservedForReply = properties.getReservedForReply();
        boolean summarizeEnabled = properties.getSummarizeEnabled() && summarizer != null;
        double summarizeTokenRatio = properties.getSummarizeTokenRatio() != null
                ? properties.getSummarizeTokenRatio() : 0.7;

        log.info("[Token内存] 创建Token窗口内存: maxTokens={}, reservedForReply={}, 模型={}, 摘要启用={}, 摘要触发比例={}",
                maxTokens, reservedForReply, model != null ? model.getModelName() : "未知",
                summarizeEnabled, summarizeTokenRatio);

        // 判断是否使用策略框架
        boolean useStrategyFramework = properties.getUseStrategyFramework() != null
                ? properties.getUseStrategyFramework() : true;

        return TokenBasedChatMemory.builder()
            .memoryId(memoryId)
            .maxTokens(maxTokens)
            .tokenCounter(tokenCounter)
            .store(persistentStore)
            .summarizeEnabled(summarizeEnabled)
            .summarizeTokenRatio(summarizeTokenRatio)
            .summarizeThreshold(properties.getSummarizeThreshold())
            .summarizer(summarizer)
            .preserveSystemMessages(properties.getPreserveSystemMessages())
            .reservedForReply(reservedForReply)
            .strategyManager(useStrategyFramework ? strategyManager : null)
            .build();
    }

    /**
     * 创建混合策略内存（Token + 摘要）
     */
    private ChatMemory createHybridMemory(Object memoryId, ChatModelVo model, ChatModel summarizer) {
        int maxTokens = resolveMaxTokens(model);
        int reservedForReply = properties.getReservedForReply();
        boolean summarizeEnabled = properties.getSummarizeEnabled() && summarizer != null;
        double summarizeTokenRatio = properties.getSummarizeTokenRatio() != null
                ? properties.getSummarizeTokenRatio() : 0.7;
        int summarizeThreshold = properties.getSummarizeThreshold();

        log.info("[Hybrid内存] 创建混合策略内存: maxTokens={}, summarizeEnabled={}, summarizeTokenRatio={}, summarizeThreshold={}",
            maxTokens, summarizeEnabled, summarizeTokenRatio, summarizeThreshold);

        // 判断是否使用策略框架
        boolean useStrategyFramework = properties.getUseStrategyFramework() != null
                ? properties.getUseStrategyFramework() : true;

        return TokenBasedChatMemory.builder()
            .memoryId(memoryId)
            .maxTokens(maxTokens)
            .tokenCounter(tokenCounter)
            .store(persistentStore)
            .summarizeEnabled(summarizeEnabled)
            .summarizeTokenRatio(summarizeTokenRatio)
            .summarizeThreshold(summarizeThreshold)
            .summarizer(summarizer)
            .preserveSystemMessages(properties.getPreserveSystemMessages())
            .reservedForReply(reservedForReply)
            .strategyManager(useStrategyFramework ? strategyManager : null)
            .build();
    }

    /**
     * 解析最大 Token 数
     * 优先使用配置值，否则根据模型自动获取
     */
    private int resolveMaxTokens(ChatModelVo model) {
        // 优先使用配置值
        if (properties.getMaxTokens() != null && properties.getMaxTokens() > 0) {
            return properties.getMaxTokens();
        }

        // 根据模型自动获取
        if (model != null && model.getModelName() != null) {
            int modelLimit = ModelTokenLimits.getLimit(model.getModelName());
            int inputLimit = ModelTokenLimits.getInputLimit(model.getModelName(), properties.getReservedForReply());
            log.debug("模型 {} 的 Token 限制: {}, 输入限制: {}", model.getModelName(), modelLimit, inputLimit);
            return inputLimit;
        }

        // 默认值
        return 4096;
    }

    /**
     * 获取模型的完整 Token 限制（用于显示）
     */
    public int getModelTokenLimit(String modelName) {
        return ModelTokenLimits.getLimit(modelName);
    }
}
