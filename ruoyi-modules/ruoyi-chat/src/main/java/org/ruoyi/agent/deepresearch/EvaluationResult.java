package org.ruoyi.agent.deepresearch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM 评估结果数据结构
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationResult {

    private Integer completenessScore;

    @Builder.Default
    private List<String> missingDimensions = new ArrayList<>();

    @Builder.Default
    private List<String> recommendedSearches = new ArrayList<>();

    private String reasoning;
    private String decision;

    @Builder.Default
    private List<String> coveredDimensions = new ArrayList<>();

    public boolean shouldContinue() {
        return "CONTINUE".equalsIgnoreCase(decision) && completenessScore < 80;
    }

    public boolean shouldFinish() {
        return "FINISH".equalsIgnoreCase(decision) || completenessScore >= 80;
    }
}
