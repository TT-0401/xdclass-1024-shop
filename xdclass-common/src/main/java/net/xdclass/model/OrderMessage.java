package net.xdclass.model;

import lombok.Data;

@Data
public class OrderMessage {

    /**
     * 消息ID
     */
    private Long messageId;

    /**
     * 订单号
     */
    private String outTradeNo;

}
