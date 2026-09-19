package com.hmall.review.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "评论VO")
public class ReviewVO {
    @Schema(description = "评论id")
    private Long id;
    @Schema(description = "订单id")
    private Long orderId;
    @Schema(description = "商品id")
    private Long itemId;
    @Schema(description = "评论人id")
    private Long userId;
    @Schema(description = "评分 1-5")
    private Integer rating;
    @Schema(description = "评论内容")
    private String content;
    @Schema(description = "评论图片，逗号分隔的URL")
    private String images;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
