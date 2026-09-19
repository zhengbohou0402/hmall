package com.hmall.review.service.impl;

import cn.hutool.core.lang.Snowflake;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmall.api.client.TradeClient;
import com.hmall.common.domain.PageDTO;
import com.hmall.common.domain.PageQuery;
import com.hmall.common.utils.RabbitMQHelper;
import com.hmall.common.utils.UserContext;
import com.hmall.review.constants.MQConstants;
import com.hmall.review.domain.dto.ReviewCreatedEvent;
import com.hmall.review.domain.dto.ReviewFormDTO;
import com.hmall.review.domain.po.Review;
import com.hmall.review.domain.vo.ReviewVO;
import com.hmall.review.mapper.ReviewMapper;
import com.hmall.review.service.IReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl extends ServiceImpl<ReviewMapper, Review> implements IReviewService {

    private final TradeClient tradeClient;
    private final Snowflake snowflake;
    private final RabbitMQHelper rabbitMQHelper;

    @Override
    public void createReview(ReviewFormDTO formDTO) {
        Long userId = UserContext.getUser();
        // 订单状态是否允许评价、订单是否属于当前用户，都交给 trade-service 的状态机校验，
        // review-service 自己不重复判断订单归属，避免两处逻辑不一致
        tradeClient.markOrderCommented(formDTO.getOrderId(), userId);

        Review review = new Review()
                .setId(snowflake.nextId())
                .setOrderId(formDTO.getOrderId())
                .setItemId(formDTO.getItemId())
                .setUserId(userId)
                .setRating(formDTO.getRating())
                .setContent(formDTO.getContent())
                .setImages(formDTO.getImages())
                .setStatus(1);
        save(review);

        rabbitMQHelper.sendMessage(
                MQConstants.REVIEW_EXCHANGE_NAME,
                MQConstants.REVIEW_CREATE_KEY,
                new ReviewCreatedEvent(formDTO.getItemId())
        );
    }

    @Override
    public PageDTO<ReviewVO> queryReviewsByItemId(Long itemId, PageQuery pageQuery) {
        Page<Review> page = lambdaQuery()
                .eq(Review::getItemId, itemId)
                .eq(Review::getStatus, 1)
                .page(pageQuery.toMpPage("create_time", false));
        return PageDTO.of(page, ReviewVO.class);
    }
}
