package net.xdclass.feign;

import net.xdclass.request.LockProductRequest;
import net.xdclass.util.JsonData;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "xdclass-product-service")
public interface ProductFeignService {


    /**
     * 获取购物车中商品的最新价格，同时也会清空购物车中对应的商品
     * @param productIdList 购物车中的商品ID
     * @return
     */
    @PostMapping("/api/cart/v1/confirm_order_cart_items")
    JsonData confirmOrderCartItem(@RequestBody List<Long> productIdList);


    /**
     * 锁定商品购物项库存
     * @param lockProductRequest
     * @return
     */
    @PostMapping("/api/product/v1/lock_products")
    JsonData lockProductStock(@RequestBody  LockProductRequest lockProductRequest);
}
