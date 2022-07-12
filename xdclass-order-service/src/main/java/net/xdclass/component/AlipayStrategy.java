package net.xdclass.component;

import com.alibaba.fastjson.JSON;
import com.alipay.api.AlipayApiException;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeWapPayResponse;
import lombok.extern.slf4j.Slf4j;
import net.xdclass.config.AlipayConfig;
import net.xdclass.config.PayUrlConfig;
import net.xdclass.enums.BizCodeEnum;
import net.xdclass.enums.ClientType;
import net.xdclass.exception.BizException;
import net.xdclass.vo.PayInfoVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


@Slf4j
@Service
public class AlipayStrategy implements PayStrategy {

    @Autowired
    private PayUrlConfig payUrlConfig;


    /**
     * 用户下单，创建支付
     * @param payInfoVO 支付信息
     * @return String 响应信息
     * */
    @Override
    public String unifiedOrder(PayInfoVO payInfoVO) {

        // 请求参数的集合
        Map<String,String> content = new HashMap<>();
        // 商户订单号：64个字符以内、可包含字母、数字、下划线；需保证在商户端不重复
        content.put("out_trade_no", payInfoVO.getOutTradeNo());
        // 销售产品码，商家和支付宝签约的产品码。手机网站支付为：QUICK_WAP_WAY
        content.put("product_code", "FAST_INSTANT_TRADE_PAY");
        // 订单总金额：单位为元，精确到小数点后两位
        content.put("total_amount", payInfoVO.getPayFee().toString());
        // 订单标题，注意：不可使用特殊字符，如 /，=，& 等
        content.put("subject", payInfoVO.getTitle());
        // 商品描述信息，可空
        content.put("body", payInfoVO.getDescription());

        // 订单支付超时时间
        double timeout = Math.floor(payInfoVO.getOrderPayTimeoutMills() / (1000.0 * 60.0));

        // 前端也需要判断订单是否要关闭了，如果要订单快要到期，则不给二次支付
        if(timeout < 1){ throw new BizException(BizCodeEnum.PAY_ORDER_PAY_TIMEOUT); }

        // 该笔订单允许的最晚付款时间，逾期将关闭交易。取值范围：1m～15d。m-分钟，h-小时，d-天，1c-当天（1c-当天的情况下，无论交易何时创建，都在0点关闭）。该参数数值不接受小数点，如 1.5h，可转换为 90m。
        content.put("timeout_express", Double.valueOf(timeout).intValue() + "m");


        // 支付端类型
        String clientType = payInfoVO.getClientType();
        String form = "";

        try{

            // H5手机网页支付
            if (clientType.equalsIgnoreCase(ClientType.H5.name())){
                // 创建支付请求
                AlipayTradeWapPayRequest request = new AlipayTradeWapPayRequest();

                // 请求参数的集合，最大长度不限，除公共参数外所有请求参数都必须放在这个参数中传递
                request.setBizContent(JSON.toJSONString(content));
                // 支付成功的跳转页面
                request.setReturnUrl(payUrlConfig.getAlipaySuccessReturnUrl());
                // 支付成功的回调页面
                request.setNotifyUrl(payUrlConfig.getAlipayCallbackUrl());

                // 获取AlipayClient的实例，执行支付请求，返回响应
                AlipayTradeWapPayResponse alipayResponse = AlipayConfig.getInstance().pageExecute(request);


                log.info("响应日志：alipayResponse={}", alipayResponse);
                // 支付调用成功
                if (alipayResponse.isSuccess()){
                    // 响应信息
                    form = alipayResponse.getBody();
                // 支付调用失败
                } else {
                    log.error("支付宝构建H5表单失败，alipayResponse={}，payInfo={}", alipayResponse, payInfoVO);
                }

            // PC支付
            }else if (clientType.equalsIgnoreCase(ClientType.PC.name())){

                AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();

                request.setBizContent(JSON.toJSONString(content));
                request.setNotifyUrl(payUrlConfig.getAlipayCallbackUrl());
                request.setReturnUrl(payUrlConfig.getAlipaySuccessReturnUrl());

                AlipayTradePagePayResponse alipayResponse = AlipayConfig.getInstance().pageExecute(request);

                log.info("响应日志，alipayResponse={}",alipayResponse);
                if (alipayResponse.isSuccess()){
                    form = alipayResponse.getBody();
                } else {
                    log.error("支付宝构建PC表单失败，alipayResponse={}，payInfo={}",alipayResponse,payInfoVO);
                }

            }

        }catch (AlipayApiException e){
            log.error("支付宝构建表单异常，payInfo={}，异常={}",payInfoVO,e);
        }

        return form;
    }


    @Override
    public String refund(PayInfoVO payInfoVO) {
        return null;
    }


    /**
     * 查询订单状态
     *      支付成功，返回非空；
     *      其他返回空。
     *
     * @param payInfoVO 支付信息
     * @return 订单状态
     */
    @Override
    public String queryPaySuccess(PayInfoVO payInfoVO) {

        // 创建查询订单状态的请求
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();

        // 请求参数的集合
        Map<String,String> content = new HashMap<>();
        // 商户订单号
        content.put("out_trade_no", payInfoVO.getOutTradeNo());

        // 设置请求参数
        request.setBizContent(JSON.toJSONString(content));

        // 获取AlipayClient的实例，执行支付请求，返回响应
        AlipayTradeQueryResponse response = null;
        try {

            response = AlipayConfig.getInstance().execute(request);
            log.info("支付宝订单状态查询的响应：{}", response.getBody());

        } catch (AlipayApiException e) {
            log.error("支付宝订单状态查询异常", e);
        }

        if(response.isSuccess()){
            log.info("支付宝订单状态查询成功：{}",payInfoVO);
            // 状态查询成功，返回订单状态
            return response.getTradeStatus();
        }else {
            log.info("支付宝订单状态查询失败：{}",payInfoVO);
            // 状态查询失败，返回空
            return "";
        }
    }
}
