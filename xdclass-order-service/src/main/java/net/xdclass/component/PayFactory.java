package net.xdclass.component;

import lombok.extern.slf4j.Slf4j;
import net.xdclass.enums.ProductOrderPayTypeEnum;
import net.xdclass.vo.PayInfoVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
@Slf4j
public class PayFactory {

    @Autowired
    private AlipayStrategy alipayStrategy;

    @Autowired
    private WechatPayStrategy wechatPayStrategy;


    /**
     * 创建支付（简单工厂模式）
     * @param payInfoVO 支付信息
     * @return String
     */
    public String pay(PayInfoVO payInfoVO) {
        // 支付类型
        String payType = payInfoVO.getPayType();

        // 支付宝支付
        if(ProductOrderPayTypeEnum.ALIPAY.name().equalsIgnoreCase(payType)){

            // 创建支付策略上下文，使用支付宝策略
            PayStrategyContext payStrategyContext = new PayStrategyContext(alipayStrategy);

            // 用户下单，创建支付
            return payStrategyContext.executeUnifiedOrder(payInfoVO);

        // 微信支付 TODO
        } else if(ProductOrderPayTypeEnum.WECHAT.name().equalsIgnoreCase(payType)){
            PayStrategyContext payStrategyContext = new PayStrategyContext(wechatPayStrategy);
            return payStrategyContext.executeUnifiedOrder(payInfoVO);
        }

        return "";
    }


    /**
     * 查询订单支付状态
     *
     * 支付成功返回非空，其他返回空
     *
     * @param payInfoVO 支付信息
     * @return String
     */
    public String queryPaySuccess(PayInfoVO payInfoVO){
        // 支付类型
        String payType = payInfoVO.getPayType();

        // 支付宝支付
        if(ProductOrderPayTypeEnum.ALIPAY.name().equalsIgnoreCase(payType)){

            // 创建支付策略上下文，使用支付宝策略
            PayStrategyContext payStrategyContext = new PayStrategyContext(alipayStrategy);

            // 查询订单支付状态
            return payStrategyContext.executeQueryPaySuccess(payInfoVO);

        // 微信支付 暂未实现
        } else if(ProductOrderPayTypeEnum.WECHAT.name().equalsIgnoreCase(payType)){
            PayStrategyContext payStrategyContext = new PayStrategyContext(wechatPayStrategy);
            return payStrategyContext.executeQueryPaySuccess(payInfoVO);
        }

        return "";
    }

}
