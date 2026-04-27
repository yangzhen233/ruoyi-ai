package org.ruoyi.service.chat.impl.memory;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 聊天长期记忆配置属性
 * 支持通过 application.yml 配置长期记忆行为
 *
 * @author yang
 * @date 2026-04-27
 */
@Data
@Component
@ConfigurationProperties(prefix = "chat.memory")
public class ChatMemoryProperties {

    /**
     * 是否启用长期记忆功能（默认启用）
     */
    private Boolean enabled = true;

    /**
     * 内存管理策略（默认 token）
     * - message: 固定消息数量
     * - token: 基于 Token 数量
     * - hybrid: 混合策略（Token + 摘要）
     */
    private String strategy = "token";

    /**
     * 消息窗口大小 - 最多保留的消息条数（默认20）
     * 仅当 strategy=message 时生效
     */
    private Integer maxMessages = 20;

    /**
     * 最大 Token 数（默认 4096）
     * 仅当 strategy=token 或 hybrid 时生效
     * 如果为空，则根据模型自动获取
     */
    private Integer maxTokens;

    /**
     * 预留给回复的 Token 数（默认 2000）
     */
    private Integer reservedForReply = 2000;

    /**
     * 是否启用消息持久化（默认启用）
     * 关闭后消息仅保存在内存中，重启后丢失
     */
    private Boolean persistenceEnabled = true;

    /**
     * 自动清理过期消息的时间间隔（天数，默认不清理）
     * 设为 0 表示禁用自动清理
     */
    private Integer autoCleanupDays = 0;

    /**
     * 消息摘要是否启用（默认禁用）
     * 启用后，超过阈值的消息会被摘要压缩
     */
    private Boolean summarizeEnabled = false;

    /**
     * 摘要触发阈值 - 消息数量（默认 30）
     * 当消息数超过此值时，对旧消息进行摘要
     */
    private Integer summarizeThreshold = 30;

    /**
     * 是否保留系统消息（默认 true）
     * 系统消息不会被截断
     */
    private Boolean preserveSystemMessages = true;

    /**
     * 是否在日志中记录内存加载情况（默认启用，用于调试）
     */
    private Boolean debugLoggingEnabled = true;

    /**
     * 数据库查询超时时间（毫秒，默认5000）
     */
    private Integer queryTimeoutMs = 5000;

    /**
     * 最大并发内存访问数（默认100）
     */
    private Integer maxConcurrentMemories = 100;

    /**
     * 获取格式化的配置信息
     */
    @Override
    public String toString() {
        return "ChatMemoryProperties{" +
                "enabled=" + enabled +
                ", strategy='" + strategy + '\'' +
                ", maxMessages=" + maxMessages +
                ", maxTokens=" + maxTokens +
                ", reservedForReply=" + reservedForReply +
                ", persistenceEnabled=" + persistenceEnabled +
                ", autoCleanupDays=" + autoCleanupDays +
                ", summarizeEnabled=" + summarizeEnabled +
                ", summarizeThreshold=" + summarizeThreshold +
                ", preserveSystemMessages=" + preserveSystemMessages +
                ", debugLoggingEnabled=" + debugLoggingEnabled +
                ", queryTimeoutMs=" + queryTimeoutMs +
                ", maxConcurrentMemories=" + maxConcurrentMemories +
                '}';
    }
}
