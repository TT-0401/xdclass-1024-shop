package net.xdclass.service.impl;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import net.xdclass.constant.CacheKey;
import net.xdclass.enums.BizCodeEnum;
import net.xdclass.exception.BizException;
import net.xdclass.interceptor.LoginInterceptor;
import net.xdclass.model.LoginUser;
import net.xdclass.request.CartItemRequest;
import net.xdclass.service.CartService;
import net.xdclass.service.ProductService;
import net.xdclass.vo.CartItemVO;
import net.xdclass.vo.CartVO;
import net.xdclass.vo.ProductVO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CartServiceImpl implements CartService {

    @Autowired
    private ProductService productService;

    @Autowired
    private RedisTemplate redisTemplate;


    /**
     * 添加商品到购物车
     * @param cartItemRequest
     */
    @Override
    public void addToCart(CartItemRequest cartItemRequest) {

        long productId = cartItemRequest.getProductId();
        int buyNum = cartItemRequest.getBuyNum();

        // 获取当前用户的购物车：Redis中的 hash 类型
        BoundHashOperations<String,Object,Object> myCart = getMyCartOps();

        // 获取请求加入购物车的商品对象
        Object cacheObj = myCart.get(productId);
        String result = "";

        if(cacheObj != null){
           result = (String) cacheObj;
        }

        if(StringUtils.isBlank(result)){
            // 若购物车中不存在该商品，则新建一个该商品
            CartItemVO cartItemVO = new CartItemVO();

            // 调用商品服务找到该商品的详细信息
            ProductVO productVO = productService.findDetailById(productId);
            // 如果没有该商品，返回添加购物车失败
            if(productVO == null){
                throw new BizException(BizCodeEnum.CART_FAIL);
            }

            cartItemVO.setAmount(productVO.getAmount());
            cartItemVO.setBuyNum(buyNum);
            cartItemVO.setProductId(productId);
            cartItemVO.setProductImg(productVO.getCoverImg());
            cartItemVO.setProductTitle(productVO.getTitle());

            // 并将其加入购物车
            myCart.put(productId,JSON.toJSONString(cartItemVO));

        }else {
            // 若购物车中不存在该商品，则修改数量
            CartItemVO cartItem = JSON.parseObject(result, CartItemVO.class);
            cartItem.setBuyNum(cartItem.getBuyNum() + buyNum);
            myCart.put(productId,JSON.toJSONString(cartItem));
        }

    }


    /**
     * 查看购物车
     *
     */
    @Override
    public CartVO getMyCart() {

        // 获取全部购物项
        List<CartItemVO> cartItemVOList = buildCartItem(false);

        // 封装成cartVO
        CartVO cartVO = new CartVO();
        cartVO.setCartItems(cartItemVOList);

        return cartVO;
    }


    /**
     * 修改购物车中商品数量
     * @param cartItemRequest
     */
    @Override
    public void changeItemNum(CartItemRequest cartItemRequest) {

        BoundHashOperations<String,Object,Object> mycart = getMyCartOps();

        Object cacheObj = mycart.get(cartItemRequest.getProductId());

        if(cacheObj == null){
            throw new BizException(BizCodeEnum.CART_FAIL);
        }

        String obj = (String) cacheObj;

        CartItemVO cartItemVO = JSON.parseObject(obj, CartItemVO.class);
        // 设置商品数量
        cartItemVO.setBuyNum(cartItemRequest.getBuyNum());
        mycart.put(cartItemRequest.getProductId(), JSON.toJSONString(cartItemVO));
    }


    /**
     * 删除购物车中某项商品
     * @param productId
     */
    @Override
    public void deleteItem(long productId) {

        BoundHashOperations<String,Object,Object> mycart = getMyCartOps();

        mycart.delete(productId);
    }


    /**
     * 清空购物车
     */
    @Override
    public void clear() {
        String cartKey = getCartKey();
        redisTemplate.delete(cartKey);
    }


    /**
     * 确认购物车商品信息
     * @param productIdList
     * @return
     */
    @Override
    public List<CartItemVO> confirmOrderCartItems(List<Long> productIdList) {

        //获取全部购物车的购物项
        List<CartItemVO> cartItemVOList =  buildCartItem(true);

        //根据需要的商品id进行过滤，并清空对应的购物项
        List<CartItemVO> resultList =  cartItemVOList.stream().filter(obj->{

            // 用户选中的商品
            if(productIdList.contains(obj.getProductId())){
                // 删掉
                this.deleteItem(obj.getProductId());
                return true;
            }
            return false;

        }).collect(Collectors.toList());

        return resultList;
    }


    // 获取最新的全部购物项
    private List<CartItemVO> buildCartItem(boolean latestPrice) {

        // 获取我的购物车
        BoundHashOperations<String,Object,Object> myCart = getMyCartOps();

        // 全部购物项
        List<Object> itemList = myCart.values();

        List<CartItemVO> cartItemVOList = new ArrayList<>();

        //拼接id列表查询最新价格
        List<Long> productIdList = new ArrayList<>();

        for(Object item: itemList){
            CartItemVO cartItemVO = JSON.parseObject((String)item,CartItemVO.class);
            cartItemVOList.add(cartItemVO);

            productIdList.add(cartItemVO.getProductId());
        }

        // 是否查询并设置购物车里商品的最新价格
        if(latestPrice){
            setProductLatestPrice(cartItemVOList, productIdList);
        }

        return cartItemVOList;
    }


    // 查询并设置购物车里商品的最新信息：标题、封面图片、价格
    private void setProductLatestPrice(List<CartItemVO> cartItemVOList, List<Long> productIdList) {

        // 批量查询商品
        List<ProductVO> productVOList = productService.findProductsByIdBatch(productIdList);

        // 以 商品ID 为 key，将 productVOList 转成 Map
        Map<Long,ProductVO> maps = productVOList.stream().collect(Collectors.toMap(ProductVO::getId,Function.identity()));

        // 根据购物车中商品的ID在商品库中搜索，并更新相关信息
        cartItemVOList.forEach(item -> {
            ProductVO productVO = maps.get(item.getProductId());
            item.setProductTitle(productVO.getTitle());
            item.setProductImg(productVO.getCoverImg());
            item.setAmount(productVO.getAmount());
        });
    }


    // 获取我的购物车的通用方法
    private BoundHashOperations<String,Object,Object> getMyCartOps(){
        String cartKey = getCartKey();
        return redisTemplate.boundHashOps(cartKey);
    }


    // 当前购物车在 Redis 中的key
    private String getCartKey(){
        LoginUser loginUser = LoginInterceptor.threadLocal.get();
        String cartKey = String.format(CacheKey.CART_KEY,loginUser.getId());
        return cartKey;
    }
}
