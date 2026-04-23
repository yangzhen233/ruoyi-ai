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

import java.util.ArrayList;
import java.util.List;

/**
 * Deep Research Agent
 * 执行多轮搜索、阅读、评估的迭代研究流程
 */
@Slf4j
@Service
public class DeepResearchAgent {

    /**
     * 执行深度研究
     */
    public String execute(String query, ChatModelVo chatModel, DeepResearchState state) {
        log.info("开始深度研究: {}", query);
        state.setOriginalQuery(query);

        ChatModel llm = createChatModel(chatModel);
        McpClient searchClient = createSearchMcpClient(state.getUserId());

        try {
            // 阶段1: 规划
            List<SearchTask> tasks = plan(llm, query, state);
            state.setPendingTasks(tasks);

            // 阶段2-4: 迭代搜索、阅读、评估
            while (state.shouldContinueIteration()) {
                state.incrementIteration();
                log.info("开始第 {} 次迭代", state.getCurrentIteration());

                SearchTask task = state.getNextPendingTask();
                if (task == null) break;

                List<SearchResult> results = search(searchClient, task, state);
                List<InformationPiece> info = read(llm, results, task, state);
                state.addCollectedInfo(info);

                EvaluationResult eval = evaluate(llm, state);
                state.setEvaluation(eval);

                if (eval.shouldFinish()) {
                    log.info("信息充足，结束迭代");
                    break;
                }

                if (eval.getRecommendedSearches() != null) {
                    state.addNewSearchTasks(eval.getRecommendedSearches());
                }
            }

            // 阶段5: 综合
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
        return OpenAiChatModel.builder()
            .baseUrl(vo.getApiHost())
            .apiKey(vo.getApiKey())
            .modelName(vo.getModelName())
            .build();
    }

    private McpClient createSearchMcpClient(Long userId) {
        McpTransport transport = new StdioMcpTransport.Builder()
            .command(List.of("D:\\Program Files\\nodejs\\npx.cmd", "-y", "bing-cn-mcp"))
            .logEvents(true)
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

    private List<SearchTask> plan(ChatModel llm, String query, DeepResearchState state) {
        sendStatus(state, "正在分析问题，规划搜索策略...");
        String prompt = """
            你是研究规划专家。分析问题并生成搜索计划。

            【问题】%s

            【输出格式】JSON:
            {
              "dimensions": ["维度1", "维度2"],
              "searchTasks": [{"query": "关键词", "dimension": "维度", "priority": 1}]
            }
            """.formatted(query);

        String response = llm.chat(prompt);
        return parseTasks(response);
    }

    private List<SearchTask> parseTasks(String response) {
        List<SearchTask> tasks = new ArrayList<>();
        try {
            String json = extractJson(response);
            JSONObject obj = JSON.parseObject(json);
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
        } catch (Exception e) {
            log.warn("解析失败，使用默认任务");
            tasks.add(SearchTask.of(response, "通用搜索"));
        }
        return tasks;
    }

    private List<SearchResult> search(McpClient client, SearchTask task, DeepResearchState state) {
        sendStatus(state, "正在搜索: " + task.getQuery());
        log.info("执行搜索: {}", task.getQuery());

        List<SearchResult> results = new ArrayList<>();
        // TODO: 实现实际 MCP 搜索调用
        state.markCurrentTaskCompleted(results.size());
        return results;
    }

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

    private EvaluationResult evaluate(ChatModel llm, DeepResearchState state) {
        sendStatus(state, "正在评估信息完整性...");

        String prompt = """
            评估信息完整性。
            问题：%s
            维度：%s
            已收集：%s

            输出JSON:
            {"completenessScore":75,"decision":"CONTINUE或FINISH","recommendedSearches":["建议"]}
            """.formatted(state.getOriginalQuery(),
                String.join(",", state.getDimensions()),
                state.getCollectedInfoSummary());

        return parseEval(llm.chat(prompt));
    }

    private EvaluationResult parseEval(String response) {
        try {
            JSONObject json = JSON.parseObject(extractJson(response));
            return EvaluationResult.builder()
                .completenessScore(json.getInteger("completenessScore"))
                .decision(json.getString("decision"))
                .recommendedSearches(json.getJSONArray("recommendedSearches") != null
                    ? json.getJSONArray("recommendedSearches").toJavaList(String.class)
                    : new ArrayList<>())
                .build();
        } catch (Exception e) {
            return EvaluationResult.builder().completenessScore(50).decision("FINISH").build();
        }
    }

    private String synthesize(ChatModel llm, DeepResearchState state) {
        sendStatus(state, "正在综合分析，生成回答...");

        return llm.chat("""
            基于收集的信息回答问题。
            问题：%s
            信息：%s

            要求：结构清晰、引用来源、多角度分析。
            """.formatted(state.getOriginalQuery(), state.getCollectedInfoSummary()));
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