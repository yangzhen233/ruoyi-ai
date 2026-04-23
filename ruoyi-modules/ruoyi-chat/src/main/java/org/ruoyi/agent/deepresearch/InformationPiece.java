package org.ruoyi.agent.deepresearch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 信息片段数据结构
 * 从搜索结果中提取的关键信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InformationPiece {

    private String sourceUrl;
    private String sourceTitle;
    private String content;
    private String dimension;
    private Integer relevanceScore;
    private String summary;

    public static InformationPiece of(String sourceUrl, String sourceTitle, String content, String dimension) {
        return InformationPiece.builder()
            .sourceUrl(sourceUrl)
            .sourceTitle(sourceTitle)
            .content(content)
            .dimension(dimension)
            .relevanceScore(50)
            .build();
    }
}
