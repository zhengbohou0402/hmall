package com.hmall.review.controller;

import com.hmall.common.domain.PageDTO;
import com.hmall.common.domain.PageQuery;
import com.hmall.common.utils.BeanUtils;
import com.hmall.review.domain.dto.ReviewFormDTO;
import com.hmall.review.domain.vo.ReviewVO;
import com.hmall.review.service.IReviewService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "评论相关接口")
@RestController
@RequestMapping("/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final IReviewService reviewService;

    @Operation(summary = "提交评论，订单需处于已收货未评价状态且属于当前登录用户")
    @PostMapping
    public void createReview(@RequestBody @Validated ReviewFormDTO formDTO) {
        reviewService.createReview(formDTO);
    }

    @Operation(summary = "分页查询某商品的评论")
    @GetMapping("/item/{itemId}")
    public PageDTO<ReviewVO> queryReviewsByItemId(@PathVariable("itemId") Long itemId, PageQuery pageQuery) {
        return reviewService.queryReviewsByItemId(itemId, pageQuery);
    }

    @Operation(summary = "根据id查询评论详情")
    @GetMapping("{id}")
    public ReviewVO queryReviewById(@PathVariable("id") Long id) {
        return BeanUtils.copyBean(reviewService.getById(id), ReviewVO.class);
    }
}
