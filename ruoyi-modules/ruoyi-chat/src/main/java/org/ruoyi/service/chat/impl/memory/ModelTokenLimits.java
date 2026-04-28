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
        // OpenAI 新模型 (2024-2026)
        // GPT-4.1 系列 (1M context)
        entry("gpt-4.1", 1048576),
        entry("gpt-4.1-mini", 1048576),
        entry("gpt-4.1-nano", 1048576),
        // GPT-4.5
        entry("gpt-4.5-turbo", 128000),
        entry("gpt-4.5-preview", 128000),
        // o 系列推理模型 (200K context)
        entry("o1", 200000),
        entry("o1-preview", 128000),
        entry("o1-mini", 128000),
        entry("o3", 200000),
        entry("o3-mini", 200000),
        entry("o4-mini", 200000),
        entry("o4-mini-deep-research", 200000),

        // ========== DeepSeek ==========
        entry("deepseek-chat", 64000),
        entry("deepseek-coder", 64000),
        entry("deepseek-reasoner", 64000),
        // DeepSeek V3 系列
        entry("deepseek-v3", 128000),
        entry("deepseek-v3-base", 128000),
        // DeepSeek R1 推理系列
        entry("deepseek-r1", 128000),
        entry("deepseek-r1-distill-llama-70b", 128000),
        entry("deepseek-r1-distill-qwen-32b", 128000),
        entry("deepseek-r1-zero", 128000),
        // DeepSeek V2
        entry("deepseek-v2", 128000),
        entry("deepseek-v2-lite", 128000),

        // ========== 智谱 AI ==========
        // GLM-5 系列 (最新)
        entry("glm-5", 131072),
        entry("glm-5-plus", 131072),
        entry("glm-5-flash", 131072),
        entry("glm-5-long", 1048576),
        entry("glm-5.1", 131072),
        entry("glm-5.1-plus", 131072),
        // GLM-4.5 系列
        entry("glm-4.5", 131072),
        entry("glm-4.5-plus", 131072),
        entry("glm-4.5-flash", 131072),
        entry("glm-4.5-air", 131072),
        entry("glm-4.5-airx", 131072),
        // GLM-4 系列
        entry("glm-4", 128000),
        entry("glm-4-plus", 128000),
        entry("glm-4-flash", 128000),
        entry("glm-4-long", 1048576),
        entry("glm-4-air", 128000),
        entry("glm-4-airx", 128000),
        // GLM-Z 思考模型
        entry("glm-z1-air", 128000),
        entry("glm-z1-airx", 128000),
        entry("glm-z1-flash", 128000),
        entry("glm-z2", 131072),
        // GLM-3
        entry("glm-3-turbo", 4096),

        // ========== 通义千问 ==========
        // Qwen3 系列 (最新)
        entry("qwen3", 131072),
        entry("qwen3-235b", 131072),
        entry("qwen3-32b", 131072),
        entry("qwen3-14b", 131072),
        entry("qwen3-8b", 131072),
        entry("qwen3-1.7b", 131072),
        entry("qwen3-0.6b", 131072),
        // Qwen2.5 系列
        entry("qwen2.5", 131072),
        entry("qwen2.5-max", 131072),
        entry("qwen2.5-plus", 131072),
        entry("qwen2.5-turbo", 131072),
        entry("qwen2.5-72b", 131072),
        entry("qwen2.5-32b", 131072),
        entry("qwen2.5-14b", 131072),
        entry("qwen2.5-7b", 131072),
        entry("qwen2.5-3b", 131072),
        entry("qwen2.5-1.5b", 131072),
        entry("qwen2.5-0.5b", 131072),
        // Qwen 其他
        entry("qwen-max", 131072),
        entry("qwen-max-longcontext", 30720),
        entry("qwen-plus", 131072),
        entry("qwen-turbo", 131072),
        entry("qwen-long", 1048576),
        // QwQ 思考模型
        entry("qwq-32b", 131072),
        entry("qwq-plus", 131072),

        // ========== 百度文心 ==========
        // ERNIE 4.5 系列 (最新)
        entry("ernie-4.5", 131072),
        entry("ernie-4.5-turbo", 131072),
        entry("ernie-4.5-8k", 8192),
        // ERNIE 4.0 系列
        entry("ernie-4.0", 8192),
        entry("ernie-4.0-8k", 8192),
        entry("ernie-4.0-turbo", 8192),
        entry("ernie-4.0-ultra", 8192),
        // ERNIE 3.5 系列
        entry("ernie-3.5", 8192),
        entry("ernie-3.5-8k", 8192),
        // ERNIE Speed/X 系列
        entry("ernie-speed", 8192),
        entry("ernie-speed-8k", 8192),
        entry("ernie-speed-128k", 131072),
        entry("ernie-lite", 8192),
        entry("ernie-lite-8k", 8192),
        entry("ernie-tiny", 8192),
        entry("ernie-x1", 32768),
        entry("ernie-character", 8192),

        // ========== 月之暗面 (Moonshot AI) ==========
        // Kimi K2 系列 (最新)
        entry("kimi-k2", 131072),
        entry("kimi-k2-pro", 131072),
        entry("kimi-k2-base", 131072),
        // Moonshot V1 系列
        entry("moonshot-v1-8k", 8192),
        entry("moonshot-v1-32k", 32768),
        entry("moonshot-v1-128k", 131072),
        // Kimi 其他
        entry("kimi", 131072),
        entry("kimi-latest", 131072),

        // ========== 讯飞星火 ==========
        // Spark 4.0 系列 (最新)
        entry("spark-4.0-ultra", 8192),
        entry("spark-4.0", 8192),
        entry("spark-v4.0", 8192),
        // Spark Max 系列
        entry("spark-max", 131072),
        entry("spark-max-128k", 131072),
        // Spark Pro 系列
        entry("spark-pro", 8192),
        entry("spark-pro-128k", 131072),
        // Spark Lite
        entry("spark-lite", 4096),
        // Spark V3.x
        entry("spark-v3.5", 8192),
        entry("spark-v3.0", 8192),
        // Spark 其他
        entry("spark-general", 8192),
        entry("spark-generalv2", 8192),
        entry("spark-generalv3", 8192),

        // ========== Claude ==========
        // Claude 4 系列 (最新, 1M context)
        entry("claude-opus-4.7", 1048576),
        entry("claude-opus-4.6", 1048576),
        entry("claude-sonnet-4.6", 1048576),
        entry("claude-opus-4", 1048576),
        entry("claude-sonnet-4", 200000),
        entry("claude-haiku-4", 200000),
        entry("claude-4-opus", 1048576),
        entry("claude-4-sonnet", 200000),
        entry("claude-4-haiku", 200000),
        // Claude 3.5 系列 (200K, 部分 1M beta)
        entry("claude-3.5-sonnet", 200000),
        entry("claude-3.5-haiku", 200000),
        entry("claude-3-5-sonnet", 200000),
        entry("claude-3-5-haiku", 200000),
        // Claude 3 系列
        entry("claude-3-opus", 200000),
        entry("claude-3-sonnet", 200000),
        entry("claude-3-haiku", 200000),

        // ========== Google Gemini ==========
        entry("gemini-pro", 32760),
        entry("gemini-pro-vision", 16384),
        entry("gemini-1.0-pro", 32760),
        entry("gemini-1.5-pro", 1048576),
        entry("gemini-1.5-flash", 1048576),
        entry("gemini-1.5-flash-8b", 1048576),
        entry("gemini-2.0-flash", 1048576),
        entry("gemini-2.0-flash-lite", 1048576),
        entry("gemini-2.5-pro", 1048576),
        entry("gemini-2.5-flash", 1048576),

        // ========== 豆包 (字节跳动) ==========
        // Doubao 1.5 系列
        entry("doubao-1.5-pro", 131072),
        entry("doubao-1.5-pro-32k", 32768),
        entry("doubao-1.5-pro-128k", 131072),
        entry("doubao-1.5-lite", 131072),
        entry("doubao-1.5-lite-32k", 32768),
        entry("doubao-1.5-lite-128k", 131072),
        // Doubao Pro 系列
        entry("doubao-pro-32k", 32768),
        entry("doubao-pro-128k", 131072),
        entry("doubao-pro-256k", 262144),
        // Doubao Lite 系列
        entry("doubao-lite-4k", 4096),
        entry("doubao-lite-32k", 32768),
        entry("doubao-lite-128k", 131072),
        // Doubao Seed
        entry("doubao-seed", 131072),
        entry("doubao-seed-1.5", 131072),
        // Doubao 其他
        entry("doubao-character", 8192),
        entry("doubao-vision", 131072),

        // ========== MiniMax ==========
        // MiniMax 01 系列 (最新)
        entry("minimax-text-01", 1048576),
        entry("minimax-vision-01", 1048576),
        // ABAB 7 系列
        entry("abab7-chat", 245000),
        entry("abab7-chat-preview", 245000),
        // ABAB 6.5 系列
        entry("abab6.5-chat", 245000),
        entry("abab6.5s-chat", 245000),
        entry("abab6.5g-chat", 245000),
        entry("abab6.5t-chat", 245000),
        // ABAB 5.5 系列
        entry("abab5.5-chat", 16384),
        entry("abab5.5s-chat", 16384),

        // ========== 阶跃星辰 ==========
        entry("step-1-8k", 8192),
        entry("step-1-32k", 32768),
        entry("step-1-128k", 131072),
        entry("step-1-256k", 262144),
        entry("step-1-flash", 8192),
        entry("step-2-16k", 16384),

        // ========== Groq ==========
        entry("llama-3.1-405b-reasoning", 131072),
        entry("llama-3.1-70b-versatile", 131072),
        entry("llama-3.1-8b-instant", 131072),
        entry("llama-3.2-1b-preview", 131072),
        entry("llama-3.2-3b-preview", 131072),
        entry("llama-3.2-11b-vision-preview", 131072),
        entry("llama-3.2-90b-vision-preview", 131072),
        entry("llama-3.3-70b-versatile", 131072),
        entry("mixtral-8x7b-32768", 32768),
        entry("gemma2-9b-it", 8192),

        // ========== Ollama 本地模型 ==========
        // Llama 4 系列 (最新)
        entry("llama4", 1048576),
        entry("llama4-scout", 1048576),
        entry("llama4-maverick", 1048576),
        // Llama 3.x 系列
        entry("llama3", 8192),
        entry("llama3:70b", 8192),
        entry("llama3.1", 131072),
        entry("llama3.1:8b", 131072),
        entry("llama3.1:70b", 131072),
        entry("llama3.2", 131072),
        entry("llama3.2:1b", 131072),
        entry("llama3.2:3b", 131072),
        entry("llama3.3", 131072),
        entry("llama3.3:70b", 131072),
        // Qwen 本地
        entry("qwen2", 32768),
        entry("qwen2.5:7b", 131072),
        entry("qwen2.5:14b", 131072),
        entry("qwen2.5:32b", 131072),
        entry("qwen2.5:72b", 131072),
        // DeepSeek 本地
        entry("deepseek-r1:7b", 131072),
        entry("deepseek-r1:8b", 131072),
        entry("deepseek-r1:14b", 131072),
        entry("deepseek-r1:32b", 131072),
        entry("deepseek-r1:70b", 131072),
        // Mistral 系列
        entry("mistral", 32768),
        entry("mistral:7b", 32768),
        entry("mistral-large", 128000),
        entry("mixtral", 32768),
        entry("mixtral:8x7b", 32768),
        entry("mixtral:8x22b", 65536),
        // CodeLlama
        entry("codellama", 16384),
        entry("codellama:7b", 16384),
        entry("codellama:13b", 16384),
        entry("codellama:34b", 16384),
        // Gemma 系列
        entry("gemma", 8192),
        entry("gemma2", 8192),
        entry("gemma2:9b", 8192),
        entry("gemma2:27b", 8192),
        entry("gemma3", 131072),
        // Phi 系列
        entry("phi-3", 128000),
        entry("phi-3-mini", 128000),
        entry("phi-3-medium", 128000),
        entry("phi-4", 16384),
        // 其他本地模型
        entry("command-r", 128000),
        entry("command-r-plus", 128000),
        entry("starcoder2", 16384),
        entry("nomic-embed-text", 8192),

        // ========== 其他国产模型 ==========
        // 零一万物 Yi
        entry("yi-34b-chat", 4096),
        entry("yi-large", 32768),
        entry("yi-large-turbo", 32768),
        entry("yi-lightning", 16384),
        entry("yi-large-rag", 32768),
        entry("yi-vision", 16384),
        entry("yi-1.5", 32768),
        entry("yi-1.5-9b", 32768),
        entry("yi-1.5-34b", 32768),
        // 百川
        entry("baichuan2", 4096),
        entry("baichuan2-7b", 4096),
        entry("baichuan2-13b", 4096),
        entry("baichuan4", 131072),
        entry("baichuan-4", 131072),
        entry("baichuan-3", 131072),
        // 书生 InternLM
        entry("internlm2", 32768),
        entry("internlm2-chat", 32768),
        entry("internlm3", 32768),
        entry("internlm3-chat", 32768),
        // 澜舟科技孟子
        entry("mengzi", 4096),
        entry("mengzi-gpt", 4096),
        // 商汤日日新
        entry("sensechat", 8192),
        entry("sensechat-5", 8192),
        entry("sensechat-128k", 131072),
        // 昆仑万维天工
        entry("skywork", 8192),
        entry("skywork-13b", 8192),
        entry("tiangong", 8192),
        // 智源研究院
        entry("aquila", 2048),
        entry("aquila2", 4096),

        // ========== Mistral AI ==========
        entry("mistral-small", 128000),
        entry("mistral-medium", 128000),
        entry("mistral-large-2407", 128000),
        entry("mistral-large-2411", 128000),
        entry("codestral", 32768),
        entry("pixtral-12b", 128000),
        entry("pixtral-large", 128000),

        // ========== xAI Grok ==========
        // Grok 3 系列 (最新)
        entry("grok-3", 131072),
        entry("grok-3-fast", 131072),
        entry("grok-3-mini", 131072),
        entry("grok-3-mini-fast", 131072),
        // Grok 2 系列
        entry("grok-2", 131072),
        entry("grok-2-1212", 131072),
        entry("grok-2-vision", 131072),
        entry("grok-2-vision-1212", 131072),
        // Grok 1
        entry("grok-1", 8192),
        entry("grok-beta", 131072)
    );

    /**
     * 默认 Token 限制（保守值）
     */
    private static final int DEFAULT_LIMIT = 4096;

    /**
     * 表示模型未知的特殊值
     * 当模型不在已知列表中时返回此值，用于触发回退策略
     */
    public static final int UNKNOWN_LIMIT = -1;

    /**
     * 检查模型是否在已知列表中
     *
     * @param modelName 模型名称
     * @return 是否已知
     */
    public static boolean isKnownModel(String modelName) {
        if (modelName == null || modelName.isEmpty()) {
            return false;
        }

        String name = modelName.toLowerCase();

        // 1. 精确匹配
        if (TOKEN_LIMITS.containsKey(name)) {
            return true;
        }

        // 2. 模糊匹配
        for (String key : TOKEN_LIMITS.keySet()) {
            if (name.contains(key)) {
                return true;
            }
        }

        // 3. 根据名称特征推断（如 128k, 32k 等）
        if (name.contains("128k") || name.contains("32k") || name.contains("16k") || name.contains("long")) {
            return true;
        }

        return false;
    }

    /**
     * 获取模型的 Token 限制（未知模型返回 UNKNOWN_LIMIT）
     * 用于判断是否需要回退到固定消息数量策略
     *
     * @param modelName 模型名称
     * @return Token 限制，未知模型返回 UNKNOWN_LIMIT (-1)
     */
    public static int getLimitOrUnknown(String modelName) {
        if (!isKnownModel(modelName)) {
            return UNKNOWN_LIMIT;
        }
        return getLimit(modelName);
    }

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
