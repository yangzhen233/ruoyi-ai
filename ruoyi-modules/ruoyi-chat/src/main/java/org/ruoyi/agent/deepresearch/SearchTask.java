package org.ruoyi.agent.deepresearch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 搜索任务数据结构
 * LLM 生成的搜索任务
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchTask {

    /**
     * 搜索关键词/查询语句
     */
    private String query;

    /**
     * 所属探索维度
     */
    private String dimension;

    /**
     * 任务优先级（1-5，1最高）
     */
    private Integer priority;

    /**
     * 任务状态：pending, running, completed, failed
     */
    private String status;

    /**
     * 搜索结果数量
     */
    private Integer resultCount;

    public static SearchTask of(String query, String dimension) {
        return SearchTask.builder()
            .query(query)
            .dimension(dimension)
            .priority(3)
            .status("pending")
            .resultCount(0)
            .build();
    }
}
