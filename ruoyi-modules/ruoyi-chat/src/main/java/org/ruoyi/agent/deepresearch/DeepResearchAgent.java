package org.ruoyi.agent.deepresearch;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;
import org.ruoyi.common.sse.utils.SseMessageUtils;
import org.ruoyi.observability.MyMcpClientListener;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Deep Research Agent
 * 执行多轮搜索、阅读、评估的迭代研究流程
 *
 * 核心特性：
 * - 规划：分析问题，制定搜索策略
 * - 搜索：执行搜索获取信息
 * - 阅读：分析搜索结果
 * - 评估：判断信息是否充足
 * - 纠错：信息不足时调整策略重新搜索
 * - 综合：生成最终回答
 */
@Slf4j
@Service
public class DeepResearchAgent {

    private static final int MAX_ITERATIONS = 5;
    private static final int TIMEOUT_SECONDS = 60;

    /**
     * 执行深度研究
     */
    public String execute(String query, ChatModelVo chatModelVo, DeepResearchState state) {
        log.info("开始深度研究: {}", query);
        state.setOriginalQuery(query);

        ChatModel llm = createChatModel(chatModelVo);
        McpClient searchClient = createSearchMcpClient(state.getUserId());

        try {
            // 阶段1: 规划
            sendStatus(state, "正在分析问题，规划搜索策略...");
            List<SearchTask> tasks = plan(llm, query, state);
            state.setPendingTasks(tasks);

            // 如果有直接回答，跳过迭代直接返回
            if (state.getDirectAnswer() != null) {
                log.info("简单问题，直接返回答案");
                return state.getDirectAnswer();
            }

            // 阶段2-4: 迭代搜索、阅读、评估
            while (state.shouldContinueIteration()) {
                state.incrementIteration();

                log.info("========== 第 {} 次迭代 ==========", state.getCurrentIteration());

                SearchTask task = state.getNextPendingTask();
                if (task == null) {
                    log.info("没有待处理任务，结束迭代");
                    break;
                }

                log.info("当前任务: [{}] 维度={}", task.getQuery(), task.getDimension());

                List<SearchResult> results = search(searchClient, task, state);
                log.info("搜索结果数: {}", results.size());

                List<InformationPiece> info = read(llm, results, task, state);
                log.info("提取信息数: {}", info.size());

                state.addCollectedInfo(info);

                EvaluationResult eval = evaluate(llm, state);
                state.setEvaluation(eval);

                if (eval.shouldFinish()) {
                    log.info("信息充足，结束迭代");
                    break;
                }

                if (eval.getRecommendedSearches() != null) {
                    state.addNewSearchTasks(eval.getRecommendedSearches());
                    log.info("添加新搜索任务: {}", eval.getRecommendedSearches());
                }

                log.info("================================");
            }

            // 阶段5: 综合
            sendStatus(state, "正在综合分析，生成回答...");
            String answer = synthesize(llm, state);
            state.setFinalAnswer(answer);

            log.info("深度研究完成，迭代 {} 次，收集 {} 条信息",
                state.getCurrentIteration(), state.getCollectedInfo().size());

            return answer;

        } finally {
            closeMcpClient(searchClient);
        }
    }

    private ChatModel createChatModel(ChatModelVo vo) {
        String modelName = vo.getModelName() != null ? vo.getModelName() : "glm-4-flash";
        return OpenAiChatModel.builder()
            .baseUrl("https://open.bigmodel.cn/api/paas/v4")
            .apiKey(vo.getApiKey())
            .modelName(modelName)
            .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .maxRetries(3)  // 增加重试次数
            .build();
    }

    private McpClient createSearchMcpClient(Long userId) {
        McpTransport transport = new StdioMcpTransport.Builder()
            .command(List.of("D:\\Program Files\\nodejs\\npx.cmd", "-y", "bing-cn-mcp"))
            .build();
        return new DefaultMcpClient.Builder()
            .transport(transport)
            .listener(new MyMcpClientListener(userId))
            .build();
    }

    private void closeMcpClient(McpClient client) {
        if (client != null) {
            try { client.close(); } catch (Exception e) { log.warn("关闭 MCP 失败", e); }
        }
    }

    // ========== 阶段实现 ==========

    /**
     * 阶段1: 规划 - 分析问题并生成搜索计划
     */
    private List<SearchTask> plan(ChatModel llm, String query, DeepResearchState state) {
        String currentDateTime = LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyy年MM月dd日 EEEE", Locale.CHINA));

        String prompt = """
            你是研究规划专家。分析问题复杂度并决定是否需要深度研究。

            【当前时间】%s

            【问题】%s

            【判断规则】
            - 简单问题（日期、时间、基本常识、简单计算等）：needResearch=false，直接回答
            - 复杂问题（需要多角度分析、搜索验证、综合信息）：needResearch=true，生成搜索计划

            【输出格式】JSON:
            {
              "needResearch": false,
              "dimensions": [],
              "searchTasks": [],
              "directAnswer": "简单问题的直接回答"
            }
            或
            {
              "needResearch": true,
              "dimensions": ["维度1", "维度2"],
              "searchTasks": [{"query": "关键词", "dimension": "维度", "priority": 1}]
            }
            """.formatted(currentDateTime, query);

        String response = llm.chat(prompt);
        return parseTasks(response, state);
    }

    private List<SearchTask> parseTasks(String response, DeepResearchState state) {
        List<SearchTask> tasks = new ArrayList<>();
        try {
            String json = extractJson(response);
            JSONObject obj = JSON.parseObject(json);

            // 检查是否需要深度研究
            Boolean needResearch = obj.getBoolean("needResearch");
            if (needResearch == null || !needResearch) {
                String directAnswer = obj.getString("directAnswer");
                if (directAnswer != null && !directAnswer.isEmpty()) {
                    state.setDirectAnswer(directAnswer);
                }
                log.info("========== 规划结果 ==========");
                log.info("问题类型: 简单问题");
                log.info("直接回答: {}", directAnswer);
                log.info("==============================");
                return tasks;
            }

            // 复杂问题，解析搜索任务
            JSONArray dimensions = obj.getJSONArray("dimensions");
            if (dimensions != null) {
                state.setDimensions(dimensions.toJavaList(String.class));
            }

            JSONArray arr = obj.getJSONArray("searchTasks");
            if (arr != null) {
                for (int i = 0; i < arr.size(); i++) {
                    JSONObject t = arr.getJSONObject(i);
                    tasks.add(SearchTask.builder()
                        .query(t.getString("query"))
                        .dimension(t.getString("dimension"))
                        .priority(t.getInteger("priority"))
                        .status("pending")
                        .build());
                }
            }

            // 打印规划结果
            log.info("========== 规划结果 ==========");
            log.info("问题类型: 复杂问题，需要深度研究");
            log.info("分析维度: {}", state.getDimensions());
            log.info("搜索任务数: {}", tasks.size());
            for (int i = 0; i < tasks.size(); i++) {
                SearchTask t = tasks.get(i);
                log.info("  任务{}: [{}] 维度={}, 优先级={}", i + 1, t.getQuery(), t.getDimension(), t.getPriority());
            }
            log.info("==============================");

        } catch (Exception e) {
            log.warn("解析失败，使用默认任务: {}", e.getMessage());
            tasks.add(SearchTask.of(response, "通用搜索"));
        }
        return tasks;
    }

    /**
     * 阶段2: 搜索 - 执行搜索获取信息
     */
    private List<SearchResult> search(McpClient client, SearchTask task, DeepResearchState state) {
        sendStatus(state, "正在搜索: " + task.getQuery());
        log.info("执行搜索: {}", task.getQuery());

        List<SearchResult> results = new ArrayList<>();
        // TODO: 实现实际 MCP 搜索调用
        state.markCurrentTaskCompleted(results.size());
        return results;
    }

    /**
     * 阶段3: 阅读 - 分析搜索结果
     */
    private List<InformationPiece> read(ChatModel llm, List<SearchResult> results,
                                         SearchTask task, DeepResearchState state) {
        sendStatus(state, "正在分析搜索结果...");
        List<InformationPiece> pieces = new ArrayList<>();

        for (SearchResult r : results) {
            String prompt = """
                从搜索结果提取关键信息（维度：%s）
                标题：%s
                摘要：%s
                """.formatted(task.getDimension(), r.getTitle(), r.getSnippet());

            pieces.add(InformationPiece.builder()
                .sourceUrl(r.getUrl())
                .sourceTitle(r.getTitle())
                .content(llm.chat(prompt))
                .dimension(task.getDimension())
                .build());
        }
        return pieces;
    }

    /**
     * 阶段4: 评估 - 判断信息是否充足
     */
    private EvaluationResult evaluate(ChatModel llm, DeepResearchState state) {
        sendStatus(state, "正在评估信息完整性...");

        String currentDateTime = LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyy年MM月dd日", Locale.CHINA));

        String prompt = """
            评估信息完整性，决定是否需要继续搜索。

            【当前时间】%s
            【问题】%s
            【分析维度】%s
            【已收集信息】%s

            【评分标准】
            - 0-30分：几乎没有有效信息，无法回答问题
            - 31-50分：信息严重不足，缺少大部分关键维度
            - 51-70分：信息部分完整，但仍有重要缺失
            - 71-85分：信息基本完整，可以生成初步回答
            - 86-100分：信息充分完整，可以生成高质量回答

            【决策规则】
            - 分数 >= 80 或信息足够回答问题 → decision: "FINISH"
            - 分数 < 80 或缺少关键信息 → decision: "CONTINUE"

            【输出格式】JSON:
            {
              "completenessScore": 分数(0-100),
              "decision": "FINISH或CONTINUE",
              "recommendedSearches": ["建议搜索的关键词"]
            }
            """.formatted(currentDateTime,
                state.getOriginalQuery(),
                String.join(",", state.getDimensions()),
                state.getCollectedInfoSummary());

        return parseEval(llm.chat(prompt));
    }

    private EvaluationResult parseEval(String response) {
        try {
            JSONObject json = JSON.parseObject(extractJson(response));
            EvaluationResult result = EvaluationResult.builder()
                .completenessScore(json.getInteger("completenessScore"))
                .decision(json.getString("decision"))
                .recommendedSearches(json.getJSONArray("recommendedSearches") != null
                    ? json.getJSONArray("recommendedSearches").toJavaList(String.class)
                    : new ArrayList<>())
                .build();

            // 打印评估结果
            log.info("========== 评估结果 ==========");
            log.info("完整性评分: {}/100", result.getCompletenessScore());
            log.info("决策: {}", result.getDecision());
            log.info("是否结束: {}", result.shouldFinish() ? "是" : "否");
            if (result.getRecommendedSearches() != null && !result.getRecommendedSearches().isEmpty()) {
                log.info("建议搜索: {}", String.join(", ", result.getRecommendedSearches()));
            }
            log.info("==============================");

            return result;
        } catch (Exception e) {
            log.warn("解析评估结果失败: {}", e.getMessage());
            return EvaluationResult.builder().completenessScore(50).decision("FINISH").build();
        }
    }

    /**
     * 阶段5: 综合 - 生成最终回答
     */
    private String synthesize(ChatModel llm, DeepResearchState state) {
        String currentDateTime = LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyy年MM月dd日", Locale.CHINA));

        return llm.chat("""
            基于收集的信息回答问题。
            【当前时间】%s
            【问题】%s
            【已收集信息】%s

            【要求】
            - 结构清晰，使用标题和列表
            - 注明信息来源
            - 多角度分析
            - 如有不确定信息，明确说明
            """.formatted(currentDateTime, state.getOriginalQuery(), state.getCollectedInfoSummary()));
    }

    private void sendStatus(DeepResearchState state, String msg) {
        if (state.getUserId() != null) {
            SseMessageUtils.sendContent(state.getUserId(), "[Deep Research] " + msg + "\n");
        }
    }

    private String extractJson(String s) {
        int start = s.indexOf('{'), end = s.lastIndexOf('}');
        return start >= 0 && end > start ? s.substring(start, end + 1) : s;
    }

    // ========== 内部类 ==========

    @lombok.Data @lombok.Builder @lombok.NoArgsConstructor @lombok.AllArgsConstructor
    public static class SearchResult {
        private String title;
        private String url;
        private String snippet;
    }
}
