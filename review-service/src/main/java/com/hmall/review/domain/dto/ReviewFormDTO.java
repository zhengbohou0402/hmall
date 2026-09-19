package com.hmall.review.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Data
@Schema(description = "提交评论表单")
public class ReviewFormDTO {
    @Schema(description = "订单id")
    @NotNull(message = "订单id不能为空")
    private Long orderId;
    @Schema(description = "商品id")
    @NotNull(message = "商品id不能为空")
    private Long itemId;
    @Schema(description = "评分 1-5")
    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分最小为1")
    @Max(value = 5, message = "评分最大为5")
    private Integer rating;
    @Schema(description = "评论内容")
    private String content;
    @Schema(description = "评论图片，逗号分隔的URL")
    private String images;
}
