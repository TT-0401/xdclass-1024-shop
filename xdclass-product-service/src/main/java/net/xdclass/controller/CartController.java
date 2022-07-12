package net.xdclass.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import net.xdclass.request.CartItemRequest;
import net.xdclass.service.CartService;
import net.xdclass.util.JsonData;
import net.xdclass.vo.CartItemVO;
import net.xdclass.vo.CartVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api("购物车")
@RestController
@RequestMapping("/api/cart/v1")
public class CartController {

    @Autowired
    private CartService cartService;


    @ApiOperation("添加商品到购物车")
    @PostMapping("add")
    public JsonData addToCart(@ApiParam("购物项") @RequestBody CartItemRequest cartItemRequest){

        cartService.addToCart(cartItemRequest);

        return JsonData.buildSuccess();
    }


    @ApiOperation("查看购物车")
    @GetMapping("/mycart")
    public JsonData findMyCart(){

        CartVO cartVO = cartService.getMyCart();

        return JsonData.buildSuccess(cartVO);
    }


    @ApiOperation("修改购物车中商品数量数量")
    @PostMapping("change")
    public JsonData changeItemNum(@ApiParam("购物项") @RequestBody CartItemRequest cartItemRequest){

        cartService.changeItemNum(cartItemRequest);

        return JsonData.buildSuccess();
    }


    @ApiOperation("删除购物车中某项商品")
    @DeleteMapping("/delete/{product_id}")
    public JsonData deleteItem(@ApiParam(value = "商品id",required = true) @PathVariable("product_id") long productId){

        cartService.deleteItem(productId);

        return JsonData.buildSuccess();
    }


    @ApiOperation("清空购物车")
    @DeleteMapping("/clear")
    public JsonData cleanMyCart(){

        cartService.clear();

        return JsonData.buildSuccess();
    }


    /**
     * 【用于订单微服务】，确认订单中商品项的最新信息，同时也会清空购物车中对应的商品
     *
     * @param productIdList 购物车中的商品ID列表
     * @return JsonData 购物车中的商品子项 VO
     */
    @ApiOperation("获取订单对应的商品信息")
    @PostMapping("confirm_order_cart_items")
    public JsonData confirmOrderCartItems(@ApiParam("商品id列表") @RequestBody List<Long> productIdList){

        List<CartItemVO> cartItemVOList = cartService.confirmOrderCartItems(productIdList);

        // 将购物车中的商品子项 VO 传回
        return JsonData.buildSuccess(cartItemVOList);
    }
}
