package net.xdclass.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;


@ApiModel(value = "商品库存锁定请求",description = "商品库存锁定协议")
@Data
public class LockProductRequest {

    @ApiModelProperty(value = "订单ID",example = "12312312312")
    @JsonProperty("order_out_trade_no")
    private String orderOutTradeNo;

    @ApiModelProperty(value = "订单项")
    @JsonProperty("order_item_list")
    private List<OrderItemRequest> orderItemList;
}
