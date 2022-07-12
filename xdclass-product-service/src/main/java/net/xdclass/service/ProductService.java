package net.xdclass.service;

import net.xdclass.model.ProductMessage;
import net.xdclass.request.LockProductRequest;
import net.xdclass.util.JsonData;
import net.xdclass.vo.ProductVO;

import java.util.List;
import java.util.Map;

public interface ProductService {

    /**
     * 分页查询商品列表
     * @param page
     * @param size
     * @return
     */
    Map<String,Object> page(int page, int size);

    /**
     * 根据ID查询商品详情
     * @param productId
     * @return
     */
    ProductVO findDetailById(long productId);

    /**
     * 根据ID批量查询商品
     * @param productIdList
     * @return
     */
    List<ProductVO> findProductsByIdBatch(List<Long> productIdList);

    /**
     * 锁定商品库存
     * @param lockProductRequest
     * @return
     */
    JsonData lockProductStock(LockProductRequest lockProductRequest);

    /**
     * 释放商品库存
     * @param productMessage
     * @return
     */
    boolean releaseProductStock(ProductMessage productMessage);
}
