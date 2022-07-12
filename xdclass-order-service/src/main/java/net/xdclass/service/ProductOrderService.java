package net.xdclass.service;

import net.xdclass.enums.ProductOrderPayTypeEnum;
import net.xdclass.model.OrderMessage;
import net.xdclass.request.ConfirmOrderRequest;
import net.xdclass.request.RepayOrderRequest;
import net.xdclass.util.JsonData;

import java.util.Map;

public interface ProductOrderService {

    /**
     * 查询订单状态
     * @param outTradeNo 订单号
     * @return String 订单状态
     */
    String queryProductOrderState(String outTradeNo);


    /**
     * 分页查询订单列表
     * @param page 当前页
     * @param size 每页显示多少条
     * @param state 订单状态
     */
    Map<String,Object> page(int page, int size, String state);


    /**
     * 提交订单，创建支付
     * @param orderRequest 下单请求
     * @return 支付结果
     */
    JsonData confirmOrder(ConfirmOrderRequest orderRequest);


    /**
     * 订单重新支付
     * @param repayOrderRequest 重新支付请求
     */
    JsonData repay(RepayOrderRequest repayOrderRequest);


    /**
     * 支付结果回调通知
     * @param payType 支付方式
     * @param paramsMap 异步通知中收到的所有参数
     * @return JsonData.code = 0 表示更新数据库中订单支付状态成功
     */
    JsonData handlerOrderCallbackMsg(ProductOrderPayTypeEnum payType, Map<String, String> paramsMap);


    /**
     * 监听队列，定时关单
     * @param orderMessage 订单消息
     * @return 是否关单
     */
    boolean closeProductOrder(OrderMessage orderMessage);


}
