package org.ruoyi.service.chat.impl.memory;

import java.util.Map;

/**
 * 模型 Token 限制映射表
 * 维护常用模型的上下文 Token 限制
 *
 * @author yang
 * @date 2026-04-27
 */
public final class ModelTokenLimits {

    private ModelTokenLimits() {}

    /**
     * 模型名称 -> Token 限制映射
     * 按模型名称小写匹配
     */
    private static final Map<String, Integer> TOKEN_LIMITS = Map.ofEntries(
        // ========== OpenAI ==========
        entry("gpt-4o", 128000),
        entry("gpt-4o-mini", 128000),
        entry("gpt-4-turbo", 128000),
        entry("gpt-4-turbo-preview", 128000),
        entry("gpt-4-0125-preview", 128000),
        entry("gpt-4-1106-preview", 128000),
        entry("gpt-4", 8192),
        entry("gpt-4-32k", 32768),
        entry("gpt-4-32k-0613", 32768),
        entry("gpt-3.5-turbo", 16385),
        entry("gpt-3.5-turbo-16k", 16384),
        entry("gpt-3.5-turbo-0125", 16385),
        entry("gpt-3.5-turbo-1106", 16385),

        // ========== DeepSeek ==========
        entry("deepseek-chat", 64000),
        entry("deepseek-coder", 64000),
        entry("deepseek-reasoner", 64000),

        // ========== 智谱 AI ==========
        entry("glm-4", 128000),
        entry("glm-4-plus", 128000),
        entry("glm-4-flash", 128000),
        entry("glm-4-long", 1024000),
        entry("glm-3-turbo", 4096),

        // ========== 通义千问 ==========
        entry("qwen-max", 32768),
        entry("qwen-max-longcontext", 30720),
        entry("qwen-plus", 32768),
        entry("qwen-turbo", 8192),
        entry("qwen-long", 1000000),

        // ========== 百度文心 ==========
        entry("ernie-4.0", 8192),
        entry("ernie-4.0-8k", 8192),
        entry("ernie-3.5", 4096),
        entry("ernie-speed", 8192),

        // ========== 月之暗面 ==========
        entry("moonshot-v1-8k", 8192),
        entry("moonshot-v1-32k", 32768),
        entry("moonshot-v1-128k", 131072),

        // ========== 讯飞星火 ==========
        entry("spark-v3.5", 8192),
        entry("spark-v3.0", 8192),

        // ========== Claude ==========
        entry("claude-3-opus", 200000),
        entry("claude-3-sonnet", 200000),
        entry("claude-3-haiku", 200000),
        entry("claude-3-5-sonnet", 200000),
        entry("claude-3-5-haiku", 200000),

        // ========== Ollama 本地模型 ==========
        entry("llama3", 8192),
        entry("llama3:70b", 8192),
        entry("llama3.1", 131072),
        entry("llama3.2", 131072),
        entry("mistral", 32768),
        entry("mistral-large", 128000),
        entry("codellama", 16384),
        entry("qwen2", 32768),
        entry("qwen2.5", 131072),
        entry("deepseek-v2", 131072),
        entry("gemma2", 8192),

        // ========== 其他 ==========
        entry("yi-34b-chat", 4096),
        entry("yi-large", 32768),
        entry("baichuan2", 4096),
        entry("internlm2", 32768)
    );

    /**
     * 默认 Token 限制（保守值）
     */
    private static final int DEFAULT_LIMIT = 4096;

    /**
     * 获取模型的 Token 限制
     *
     * @param modelName 模型名称
     * @return Token 限制
     */
    public static int getLimit(String modelName) {
        if (modelName == null || modelName.isEmpty()) {
            return DEFAULT_LIMIT;
        }

        String name = modelName.toLowerCase();

        // 1. 精确匹配
        if (TOKEN_LIMITS.containsKey(name)) {
            return TOKEN_LIMITS.get(name);
        }

        // 2. 模糊匹配（处理带版本号或前缀的模型名）
        for (Map.Entry<String, Integer> entry : TOKEN_LIMITS.entrySet()) {
            if (name.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // 3. 根据名称特征推断
        if (name.contains("128k") || name.contains("128k")) {
            return 128000;
        }
        if (name.contains("32k")) {
            return 32768;
        }
        if (name.contains("16k")) {
            return 16384;
        }
        if (name.contains("long")) {
            return 100000;
        }

        return DEFAULT_LIMIT;
    }

    /**
     * 获取输入 Token 上限（预留回复空间）
     *
     * @param modelName 模型名称
     * @param reservedForReply 预留给回复的 Token 数
     * @return 输入 Token 上限
     */
    public static int getInputLimit(String modelName, int reservedForReply) {
        int limit = getLimit(modelName);
        return Math.max(limit - reservedForReply, 1000);
    }

    private static Map.Entry<String, Integer> entry(String key, Integer value) {
        return Map.entry(key, value);
    }
}
