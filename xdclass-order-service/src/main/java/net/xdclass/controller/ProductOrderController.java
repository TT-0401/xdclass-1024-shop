package net.xdclass.controller;

import com.alibaba.fastjson.JSON;
import com.alipay.api.AlipayApiException;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.alipay.api.response.AlipayTradeWapPayResponse;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import net.xdclass.config.AlipayConfig;
import net.xdclass.config.PayUrlConfig;
import net.xdclass.constant.CacheKey;
import net.xdclass.enums.BizCodeEnum;
import net.xdclass.enums.ClientType;
import net.xdclass.enums.ProductOrderPayTypeEnum;
import net.xdclass.interceptor.LoginInterceptor;
import net.xdclass.model.LoginUser;
import net.xdclass.request.ConfirmOrderRequest;
import net.xdclass.request.RepayOrderRequest;
import net.xdclass.service.ProductOrderService;
import net.xdclass.util.CommonUtil;
import net.xdclass.util.JsonData;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Api("订单模块")
@RestController
@RequestMapping("/api/order/v1")
@Slf4j
public class ProductOrderController {

    @Autowired
    private ProductOrderService orderService;

    @Autowired
    private PayUrlConfig payUrlConfig;

    @Autowired
    private StringRedisTemplate redisTemplate;


    @ApiOperation("分页查询订单列表")
    @GetMapping("page")
    public JsonData pagePOrderList(
            @ApiParam(value = "当前页")  @RequestParam(value = "page", defaultValue = "1") int page,
            @ApiParam(value = "每页显示多少条") @RequestParam(value = "size", defaultValue = "10") int size,
            @ApiParam(value = "订单状态") @RequestParam(value = "state",required = false) String  state){

        Map<String,Object> pageResult = orderService.page(page,size,state);

        return JsonData.buildSuccess(pageResult);
    }


    @ApiOperation("获取下单操作的令牌")
    @GetMapping("get_token")
    public JsonData getOrderToken(){
        // 当前用户
        LoginUser loginUser = LoginInterceptor.threadLocal.get();

        // 提交订单操作令牌的key
        String key = String.format(CacheKey.SUBMIT_ORDER_TOKEN_KEY,loginUser.getId());
        // 生成随机令牌
        String token = CommonUtil.getStringNumRandom(32);

        // 存入redis缓存中：30分钟过期
        redisTemplate.opsForValue().set(key,token,30, TimeUnit.MINUTES);

        // 返回该令牌
        return JsonData.buildSuccess(token);
    }


    @ApiOperation("提交订单，创建支付")
    @PostMapping("confirm")
    public void confirmOrder(@ApiParam("订单对象") @RequestBody ConfirmOrderRequest orderRequest, HttpServletResponse response){
        // 提交订单，创建支付
        JsonData jsonData = orderService.confirmOrder(orderRequest);

        // 支付创建成功
        if (jsonData.getCode() == 0){

            String client = orderRequest.getClientType();
            String payType = orderRequest.getPayType();

            // 支付宝支付
            if (payType.equalsIgnoreCase(ProductOrderPayTypeEnum.ALIPAY.name())){
                // 支付宝网页支付
                if (client.equalsIgnoreCase(ClientType.H5.name())){

                    // 打印支付页面的HTML，即返回支付页面
                    writeData(response,jsonData);

                // 支付宝APP支付  TODO
                }else if (client.equalsIgnoreCase(ClientType.APP.name())){

                }

            // 微信支付 TODO
            } else if (payType.equalsIgnoreCase(ProductOrderPayTypeEnum.WECHAT.name())){

            }

        } else {
            log.error("创建支付订单失败：{}", jsonData);
        }
    }


    @ApiOperation("订单重新支付")
    @PostMapping("repay")
    public void repay(@ApiParam("订单对象") @RequestBody RepayOrderRequest repayOrderRequest, HttpServletResponse response){

        JsonData jsonData = orderService.repay(repayOrderRequest);

        if (jsonData.getCode() == 0){

            String client = repayOrderRequest.getClientType();
            String payType = repayOrderRequest.getPayType();

            // 支付宝支付
            if (payType.equalsIgnoreCase(ProductOrderPayTypeEnum.ALIPAY.name())){

                log.info("创建重新支付订单成功：{}", repayOrderRequest);

                // 支付宝网页支付
                if (client.equalsIgnoreCase(ClientType.H5.name())){

                    // 打印支付页面的HTML
                    writeData(response,jsonData);

                // 支付宝APP支付  TODO
                }else if (client.equalsIgnoreCase(ClientType.APP.name())){

                }

            // 微信支付 TODO
            } else if (payType.equalsIgnoreCase(ProductOrderPayTypeEnum.WECHAT.name())){

            }

        } else {
            log.error("创建重新支付订单失败{}",jsonData.toString());
            CommonUtil.sendJsonMessage(response,jsonData);
        }
    }

    // 打印第三方支付返回的支付页面
    private void writeData(HttpServletResponse response, JsonData jsonData) {
        try {
            response.setContentType("text/html;charset=UTF8");
            // 第三方支付页面
            response.getWriter().write(jsonData.getData().toString());
            response.getWriter().flush();
            response.getWriter().close();
        }catch (IOException e){
            log.error("打印HTML出现异常：{}", e);
        }
    }


    /**
     * 用于给优惠券微服务和商品微服务RPC调用
     * */
    @ApiOperation("查询订单状态")
    @GetMapping("query_state")
    public JsonData queryProductOrderState(@ApiParam("订单号") @RequestParam("out_trade_no") String outTradeNo){

        String state = orderService.queryProductOrderState(outTradeNo);

        // state = "" ，则说明订单不存在，否则返回具体状态
        return StringUtils.isBlank(state) ? JsonData.buildResult(BizCodeEnum.ORDER_CONFIRM_NOT_EXIST) : JsonData.buildSuccess(state);
    }


    /**
     * 支付测试
     */
    @GetMapping("test_pay")
    public void testAlipay(HttpServletResponse response) throws AlipayApiException, IOException {
        // 存入订单信息
        Map<String, String> content = new HashMap<>();

        // 订单号：64个字符以内、可包含字母、数字、下划线；需保证在商户端不重复。
        String no = UUID.randomUUID().toString();
        content.put("out_trade_no", no);

        // 商品码
        content.put("product_code", "FAST_INSTANT_TRADE_PAY");

        // 订单总金额：单位为元，精确到小数点后两位
        content.put("total_amount", String.valueOf("111.99"));

        // 商品标题/交易标题/订单标题/订单关键字等。 注意：不可使用特殊字符，如 /，=，&amp; 等。
        content.put("subject", "杯子");

        // 商品描述，可空
        content.put("body", "好的杯子");

        // 该笔订单允许的最晚付款时间，逾期将关闭交易。取值范围：1m～15d。m-分钟，h-小时，d-天，1c-当天（1c-当天的情况下，无论交易何时创建，都在0点关闭）。 该参数数值不接受小数点， 如 1.5h，可转换为 90m。
        content.put("timeout_express", "5m");


        // 创建支付请求
        AlipayTradeWapPayRequest request = new AlipayTradeWapPayRequest();
        // 传入订单信息：Map -> String
        request.setBizContent(JSON.toJSONString(content));
        // 支付成功的跳转页面
        request.setReturnUrl(payUrlConfig.getAlipaySuccessReturnUrl());
        // 支付成功的回调页面
        request.setNotifyUrl(payUrlConfig.getAlipayCallbackUrl());

        // 获取AlipayClient的实例，执行支付请求，返回响应
        AlipayTradeWapPayResponse alipayResponse = AlipayConfig.getInstance().pageExecute(request);

        // 支付调用成功
        if (alipayResponse.isSuccess()){

            System.out.println("支付调用成功");

            // 打印第三方支付返回的支付页面
            String form = alipayResponse.getBody();
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write(form);
            response.getWriter().flush();
            response.getWriter().close();

        } else {
            System.out.println("支付调用失败");
        }
    }


}

