package com.hmall.review.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmall.common.domain.PageDTO;
import com.hmall.common.domain.PageQuery;
import com.hmall.review.domain.dto.ReviewFormDTO;
import com.hmall.review.domain.po.Review;
import com.hmall.review.domain.vo.ReviewVO;

public interface IReviewService extends IService<Review> {

    void createReview(ReviewFormDTO formDTO);

    PageDTO<ReviewVO> queryReviewsByItemId(Long itemId, PageQuery pageQuery);
}
