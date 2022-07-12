package net.xdclass.feign;

import net.xdclass.util.JsonData;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "xdclass-order-service")
public interface ProductOrderFeignService {

    /**
     * RPC调用订单微服务，查询订单状态
     * @param outTradeNo 订单ID
     * @return JsonData 订单状态
     */
    @GetMapping("/api/order/v1/query_state")
    JsonData queryProductOrderState(@RequestParam("out_trade_no") String outTradeNo);

}
