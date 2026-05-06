package org.ruoyi.service.chat.impl.memory.strategy;

import dev.langchain4j.data.message.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.service.chat.impl.memory.TokenCounter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 滑动窗口策略
 * 保留最近 N 条消息，丢弃更早的消息
 *
 * @author yang
 * @date 2026-04-29
 */
@Slf4j
@Component
public class SlidingWindowStrategy implements MemoryCompressionStrategy {

    /**
     * 默认窗口大小
     */
    private static final int DEFAULT_WINDOW_SIZE = 20;

    @Override
    public String getName() {
        return "sliding-window";
    }

    @Override
    public int getPriority() {
        return 150; // 低优先级，作为兜底策略
    }

    @Override
    public boolean needsCompression(CompressionContext context) {
        // 消息数超过窗口大小时需要压缩
        int windowSize = getWindowSize(context);
        return context.getMessages().size() > windowSize;
    }

    @Override
    public CompressionResult compress(CompressionContext context) {
        List<ChatMessage> messages = context.getMessages();
        int windowSize = getWindowSize(context);
        TokenCounter tokenCounter = context.getTokenCounter();

        if (messages == null || messages.isEmpty()) {
            return CompressionResult.success(getName(), messages, 0, 0, 0, 0);
        }

        int originalTokens = context.getCurrentTokens();
        int originalCount = messages.size();

        // 保留最近 N 条消息
        int fromIndex = Math.max(0, messages.size() - windowSize);
        List<ChatMessage> result = new ArrayList<>(messages.subList(fromIndex, messages.size()));

        int compressedTokens = tokenCounter.countMessages(result);

        log.info("[滑动窗口策略] 完成: 原消息数={} → 截断后={}, 窗口大小={}",
            originalCount, result.size(), windowSize);

        return CompressionResult.success(
            getName(),
            result,
            originalTokens,
            compressedTokens,
            originalCount,
            result.size()
        );
    }

    /**
     * 获取窗口大小
     * 可从上下文或配置中获取
     */
    private int getWindowSize(CompressionContext context) {
        // 可以从上下文的配置中获取，这里使用默认值
        return DEFAULT_WINDOW_SIZE;
    }
}
