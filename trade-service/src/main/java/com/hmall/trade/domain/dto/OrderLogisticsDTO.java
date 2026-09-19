package com.hmall.trade.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "发货物流信息")
public class OrderLogisticsDTO {
    @Schema(description = "物流单号")
    private String logisticsNumber;
    @Schema(description = "物流公司名称")
    private String logisticsCompany;
    @Schema(description = "收件人")
    private String contact;
    @Schema(description = "收件人手机号码")
    private String mobile;
    @Schema(description = "省")
    private String province;
    @Schema(description = "市")
    private String city;
    @Schema(description = "区")
    private String town;
    @Schema(description = "街道")
    private String street;
}
