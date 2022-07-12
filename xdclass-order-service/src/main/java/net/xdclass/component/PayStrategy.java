package net.xdclass.component;

import net.xdclass.vo.PayInfoVO;


public interface PayStrategy {


    /**
     * 下单
     * @param payInfoVO
     */
    String unifiedOrder(PayInfoVO payInfoVO);


    /**
     *  退款
     * @param payInfoVO
     */
    default String refund(PayInfoVO payInfoVO){return "";}


    /**
     * 查询支付是否成功
     * @param payInfoVO
     */
    default String queryPaySuccess(PayInfoVO payInfoVO){return "";}


}
