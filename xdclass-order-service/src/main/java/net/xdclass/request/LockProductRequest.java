package net.xdclass.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * 小滴课堂,愿景：让技术不再难学
 *
 * @Description
 * @Author 二当家小D
 * @Remark 有问题直接联系我，源码-笔记-技术交流群
 * @Version 1.0
 **/

@ApiModel(value = "商品锁定对象",description = "商品锁定对象协议")
@Data
public class LockProductRequest {

    @ApiModelProperty(value = "订单id",example = "12312312312")
    @JsonProperty("order_out_trade_no")
    private String orderOutTradeNo;

    @ApiModelProperty(value = "订单项")
    @JsonProperty("order_item_list")
    private List<OrderItemRequest> orderItemList;
}
