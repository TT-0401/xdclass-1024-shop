package net.xdclass.component;

import net.xdclass.vo.PayInfoVO;

/**
 * 策略上下文：屏蔽高层模块对策略、算法的直接访问，封装可能存在的变化
 **/
public class PayStrategyContext {

    private PayStrategy payStrategy;

    public PayStrategyContext(PayStrategy payStrategy){
        this.payStrategy = payStrategy;
    }


    /**
     * 根据支付策略，调用不同的支付
     * @param payInfoVO
     * @return
     */
    public String executeUnifiedOrder(PayInfoVO payInfoVO){
        return this.payStrategy.unifiedOrder(payInfoVO);
    }


    /**
     * 根据支付策略，调用不同的查询支付状态
     * @param payInfoVO 支付信息：包括支付类型和订单号
     * @return
     */
    public String executeQueryPaySuccess(PayInfoVO payInfoVO){
        return this.payStrategy.queryPaySuccess(payInfoVO);
    }


}
