package net.xdclass.controller;

import com.alipay.api.AlipayApiException;
import com.alipay.api.internal.util.AlipaySignature;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import net.xdclass.config.AlipayConfig;
import net.xdclass.enums.ProductOrderPayTypeEnum;
import net.xdclass.service.ProductOrderService;
import net.xdclass.util.JsonData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;


@Api("订单回调通知模块")
@Controller
@RequestMapping("/api/callback/order/v1")
@Slf4j
public class CallbackController {

    @Autowired
    private ProductOrderService productOrderService;


    /**
     * 支付宝异步通知说明：
     * * 对于手机网站支付产生的交易，支付宝会根据原始支付API中传入的异步通知地址notify_url，通过 POST 请求的形式将支付结果作为参数通知到商户系统。
     * * notify_url：order-server/api/callback/order/v1/alipay，即此！
     *
     * @param request 支付宝回调通知发来的 POST 请求
     * @return 收到异步通知后，商家输出success是表示消息获取成功，支付宝就会停止发送异步通知；
     *         如果输出fail，表示消息获取失败，支付宝会重新发送消息到异步通知地址。
     */
    @PostMapping("alipay")
    public String alipayCallback(HttpServletRequest request){
        // 将从支付宝回调通知 POST 请求中收到的所有参数存储到 map 中
        Map<String, String> paramsMap = convertRequestParamsToMap(request);
        log.info("支付宝回调通知的结果：{}", paramsMap);

        try {
            // 调用Alipay的SDK验证签名
            boolean signVerified = AlipaySignature.rsaCheckV1(paramsMap, AlipayConfig.ALIPAY_PUB_KEY, AlipayConfig.CHARSET, AlipayConfig.SIGN_TYPE);

            // 验证通过
            if (signVerified){

                // 【处理支付回调通知，根据返回的支付结果，更新数据库中订单状态】
                JsonData jsonData = productOrderService.handlerOrderCallbackMsg(ProductOrderPayTypeEnum.ALIPAY, paramsMap);

                // 处理成功
                if (jsonData.getCode() == 0){
                    // 程序执行完后必须打印输出 success，如果商户反馈给支付宝的字符不是 success 这 7 个字符，支付宝服务器会不断重发通知
                    return "success";
                }
            }
        } catch (AlipayApiException e) {
            log.info("支付宝回调通知验证签名失败：异常={}，参数={}", e, paramsMap);
        }

        return "fail";
    }


    // 将支付宝回调通知的 POST 请求中的请求参数转换成 Map
    private static Map<String, String> convertRequestParamsToMap(HttpServletRequest request) {
        Map<String, String> paramsMap = new HashMap<>(16);

        Set<Map.Entry<String, String[]>> entrySet = request.getParameterMap().entrySet();
        for (Map.Entry<String, String[]> entry : entrySet) {
            String name = entry.getKey();
            String[] values = entry.getValue();
            int size = values.length;
            if (size == 1) {
                paramsMap.put(name, values[0]);
            } else {
                paramsMap.put(name, "");
            }
        }

        return paramsMap;
    }
}
