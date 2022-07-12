package net.xdclass.service;

import net.xdclass.request.CartItemRequest;
import net.xdclass.vo.CartItemVO;
import net.xdclass.vo.CartVO;

import java.util.List;

public interface CartService {

    /**
     * 添加商品到购物车
     * @param cartItemRequest
     */
    void addToCart(CartItemRequest cartItemRequest);


    /**
     * 查看购物车
     */
    CartVO getMyCart();


    /**
     * 修改购物车中商品数量
     * @param cartItemRequest
     */
    void changeItemNum(CartItemRequest cartItemRequest);


    /**
     * 删除购物车中某项商品
     * @param productId
     */
    void deleteItem(long productId);


    /**
     * 清空购物车
     */
    void clear();


    /**
     * 确认购物车商品信息
     * @param productIdList
     * @return
     */
    List<CartItemVO> confirmOrderCartItems(List<Long> productIdList);
}
