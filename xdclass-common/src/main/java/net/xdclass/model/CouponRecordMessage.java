package net.xdclass.model;

import lombok.Data;

@Data
public class CouponRecordMessage {


    /**
     * 消息ID
     */
    private String messageId;

    /**
     * 订单号
     */
    private String outTradeNo;

    /**
     * 库存锁定任务ID
     */
    private Long taskId;

}
