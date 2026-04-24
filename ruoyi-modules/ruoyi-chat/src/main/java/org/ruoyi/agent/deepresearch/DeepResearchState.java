package org.ruoyi.agent.deepresearch;

import lombok.Getter;
import lombok.Setter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;

/**
 * Deep Research 状态数据结构
 */
@Setter
@Getter
public class DeepResearchState {

    private int currentIteration = 0;
    private int maxIterations = 5;

    private String originalQuery;
    private List<String> dimensions = new ArrayList<>();
    private List<SearchTask> pendingTasks = new ArrayList<>();
    private SearchTask currentTask;
    private List<SearchTask> completedTasks = new ArrayList<>();
    private List<InformationPiece> collectedInfo = new ArrayList<>();
    private EvaluationResult evaluation;
    private String decision;
    private String directAnswer;  // 简单问题的直接回答

    private SseEmitter sseEmitter;
    private Long userId;
    private String tokenValue;

    private String finalAnswer;

    public DeepResearchState() {}

    public DeepResearchState(SseEmitter sseEmitter, Long userId, String tokenValue) {
        this.sseEmitter = sseEmitter;
        this.userId = userId;
        this.tokenValue = tokenValue;
    }

    public SearchTask getNextPendingTask() {
        if (pendingTasks.isEmpty()) return null;
        currentTask = pendingTasks.remove(0);
        currentTask.setStatus("running");
        return currentTask;
    }

    public void markCurrentTaskCompleted(int resultCount) {
        if (currentTask != null) {
            currentTask.setStatus("completed");
            currentTask.setResultCount(resultCount);
            completedTasks.add(currentTask);
            currentTask = null;
        }
    }

    public void markCurrentTaskFailed(String errorMessage) {
        if (currentTask != null) {
            currentTask.setStatus("failed");
            completedTasks.add(currentTask);
            currentTask = null;
        }
    }

    public void addNewSearchTasks(List<String> queries) {
        for (String query : queries) {
            pendingTasks.add(SearchTask.of(query, "补充搜索"));
        }
    }

    public void addCollectedInfo(InformationPiece info) {
        collectedInfo.add(info);
    }

    public void addCollectedInfo(List<InformationPiece> infos) {
        collectedInfo.addAll(infos);
    }

    public boolean shouldContinueIteration() {
        if (currentIteration >= maxIterations) return false;
        if (pendingTasks.isEmpty()) return false;
        if (evaluation != null && evaluation.shouldFinish()) return false;
        return true;
    }

    public void incrementIteration() {
        currentIteration++;
    }

    public String getCollectedInfoSummary() {
        if (collectedInfo == null || collectedInfo.isEmpty()) {
            return "暂无收集信息";
        }
        StringBuilder sb = new StringBuilder();
        for (InformationPiece info : collectedInfo) {
            sb.append("来源: ").append(info.getSourceTitle() != null ? info.getSourceTitle() : "未知").append("\n");
            sb.append("URL: ").append(info.getSourceUrl() != null ? info.getSourceUrl() : "无").append("\n");
            sb.append("内容: ").append(info.getContent() != null ? info.getContent() : "无").append("\n");
            sb.append("---\n");
        }
        return sb.toString();
    }

    public int getCompletedTaskCount() {
        return completedTasks.size();
    }
}
