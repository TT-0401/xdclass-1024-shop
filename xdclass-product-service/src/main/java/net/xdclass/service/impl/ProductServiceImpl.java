package net.xdclass.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import net.xdclass.config.RabbitMQConfig;
import net.xdclass.enums.BizCodeEnum;
import net.xdclass.enums.ProductOrderStateEnum;
import net.xdclass.enums.StockTaskStateEnum;
import net.xdclass.exception.BizException;
import net.xdclass.feign.ProductOrderFeignSerivce;
import net.xdclass.mapper.ProductMapper;
import net.xdclass.mapper.ProductTaskMapper;
import net.xdclass.model.ProductDO;
import net.xdclass.model.ProductMessage;
import net.xdclass.model.ProductTaskDO;
import net.xdclass.request.LockProductRequest;
import net.xdclass.request.OrderItemRequest;
import net.xdclass.service.ProductService;
import net.xdclass.util.JsonData;
import net.xdclass.vo.ProductVO;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProductServiceImpl implements ProductService {

    @Resource
    private ProductMapper productMapper;

    @Resource
    private ProductTaskMapper productTaskMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitMQConfig rabbitMQConfig;

    @Autowired
    private ProductOrderFeignSerivce orderFeignSerivce;


    /**
     * 商品分页
     * @param page
     * @param size
     */
    @Override
    public Map<String, Object> page(int page, int size) {

        Page<ProductDO> pageInfo = new Page<>(page,size);
        IPage<ProductDO> productDOIPage =  productMapper.selectPage(pageInfo,null);

        Map<String,Object> pageMap = new HashMap<>(3);

        pageMap.put("total_record",productDOIPage.getTotal());
        pageMap.put("total_page",productDOIPage.getPages());
        pageMap.put("current_data",productDOIPage.getRecords().stream().map(this::beanProcess).collect(Collectors.toList()));

        return pageMap;
    }


    /**
     * 根据ID查询商品详情
     * @param productId
     */
    @Override
    public ProductVO findDetailById(long productId) {

        ProductDO productDO = productMapper.selectById(productId);

        return beanProcess(productDO);
    }


    /**
     * 批量查询商品
     * @param productIdList
     */
    @Override
    public List<ProductVO> findProductsByIdBatch(List<Long> productIdList) {

        List<ProductDO> productDOList =  productMapper.selectList(new QueryWrapper<ProductDO>().in("id",productIdList));

        List<ProductVO> productVOList = productDOList.stream().map(this::beanProcess).collect(Collectors.toList());

        return productVOList;
    }


    /**
     * 锁定商品库存
     *
     * 1) 遍历订单中的每个商品，锁定每个商品的购买数量
     * 2) 每一次锁定的时候，都要发送延迟消息！
     *
     * @param lockProductRequest 订单微服务的创建订单接口发来锁定商品库存请求
     */
    @Override
    public JsonData lockProductStock(LockProductRequest lockProductRequest) {

        // 用户下单 -> RPC调用锁定商品库存 -> 商品库存锁定请求
        // 从商品库存锁定请求中提取下列参数：
        // 被锁定的商品对象的列表
        List<OrderItemRequest> itemList  = lockProductRequest.getOrderItemList();
        // 与之关联的订单号
        String outTradeNo = lockProductRequest.getOrderOutTradeNo();


        // 一行代码，提取商品对象的ID并加入到集合里面
        List<Long> productIdList = itemList.stream().map(OrderItemRequest::getProductId).collect(Collectors.toList());
        // 批量查询这些商品
        List<ProductVO> productVOList = this.findProductsByIdBatch(productIdList);
        // 以商品ID为key，将商品对象的 List 转为 Map
        Map<Long,ProductVO> productMap = productVOList.stream().collect(Collectors.toMap(ProductVO::getId, Function.identity()));

        // 1) 遍历订单中的每个商品，锁定每个商品的购买数量
        for(OrderItemRequest item : itemList){
            // 锁定商品记录
            int rows = productMapper.lockProductStock(item.getProductId(),item.getBuyNum());

            if(rows != 1){
                // 商品库存锁定失败
                throw new BizException(BizCodeEnum.ORDER_CONFIRM_LOCK_PRODUCT_FAIL);
            }else {

                // 创建商品锁定任务工作单，并设置商品ID、购买数量、锁定状态、订单号等
                ProductTaskDO productTaskDO = new ProductTaskDO();
                productTaskDO.setProductId(item.getProductId());
                productTaskDO.setBuyNum(item.getBuyNum());
                productTaskDO.setLockState(StockTaskStateEnum.LOCK.name());
                productTaskDO.setOutTradeNo(outTradeNo);
                ProductVO productVO = productMap.get(item.getProductId());
                productTaskDO.setProductName(productVO.getTitle());

                // 插入商品锁定任务工作单
                productTaskMapper.insert(productTaskDO);
                log.info("商品库存锁定，新增商品库存锁定任务:{}",productTaskDO);


                // 2) 每一次锁定的时候，都要发送延迟消息！
                // 创建商品库存锁定延迟消息，并关联订单号和任务ID
                ProductMessage productMessage = new ProductMessage();
                productMessage.setOutTradeNo(outTradeNo);
                productMessage.setTaskId(productTaskDO.getId());
                // 发送延迟消息
                rabbitTemplate.convertAndSend(rabbitMQConfig.getEventExchange(),rabbitMQConfig.getStockReleaseDelayRoutingKey(),productMessage);
                log.info("商品库存锁定信息延迟消息发送成功:{}",productMessage);
            }

        }

        return JsonData.buildSuccess();
    }


    /**
     * 释放商品库存：供 ProductStockMQListener 调用判断商品库存是否成功释放，即判断消息是否消费成功！
     *
     * 1) 查询商品库存锁定任务工作单是否存在
     * 2) 查询商品库存状态，lock状态时才处理
     * 3) 查询订单状态
     *
     * 监听到延迟消息，释放商品库存，即将其商品库存恢复至应有的状态
     *
     * @param productMessage 商品库存锁定消息，经过一定延迟后，由死信队列监听器监听得到！！！
     * @return boolean 解锁成功与否 -> 消息是否成功消费
     */
    @Override
    public boolean releaseProductStock(ProductMessage productMessage) {

        // 1) 查询商品库存锁定任务工作单是否存在
        ProductTaskDO taskDO = productTaskMapper.selectOne(new QueryWrapper<ProductTaskDO>().eq("id",productMessage.getTaskId()));
        if(taskDO == null){
            log.warn("商品库存锁定任务工作单不存在，msg:{}",productMessage);
            return true;
        }

        // 2) 查询商品库存状态，lock状态时才处理
        if(taskDO.getLockState().equalsIgnoreCase(StockTaskStateEnum.LOCK.name())){

            // 3) 再查询订单状态：RPC调用订单微服务的查询订单状态接口，返回订单状态的JSON数据
            JsonData jsonData = orderFeignSerivce.queryProductOrderState(productMessage.getOutTradeNo());

            // 正常响应，判断订单状态
            if(jsonData.getCode() == 0){
                String state = jsonData.getData().toString();

                // 如果订单为新建状态，即 NEW 状态，则返回给消息队列，重新投递
                if(state.equalsIgnoreCase(ProductOrderStateEnum.NEW.name())){
                    log.warn("订单状态为NEW，返回给消息队列，重新投递，msg={}",productMessage);
                    return false;
                }

                // 如果订单已经支付，即 PAY 状态
                if(ProductOrderStateEnum.PAY.name().equalsIgnoreCase(state)){
                    // 如果已经支付，修改商品库存锁定任务状态为 FINISH
                    taskDO.setLockState(StockTaskStateEnum.FINISH.name());
                    productTaskMapper.update(taskDO,new QueryWrapper<ProductTaskDO>().eq("id",productMessage.getTaskId()));

                    log.info("订单已经支付，修改商品库存锁定任务状态为FINISH，msg={}",productMessage);
                    return true;
                }
            }

            // 未正常响应，说明订单不存在，或者订单被取消
            // 修改商品库存锁定任务状态为 CANCEL
            taskDO.setLockState(StockTaskStateEnum.CANCEL.name());
            productTaskMapper.update(taskDO,new QueryWrapper<ProductTaskDO>().eq("id",productMessage.getTaskId()));

            // 恢复商品库存，即 锁定库存的值 - 当前购买的数量
            productMapper.unlockProductStock(taskDO.getProductId(),taskDO.getBuyNum());

            log.warn("订单不存在，或者订单被取消，确认消息，修改商品库存锁定任务状态为CANCEL，恢复商品库存，msg={}",productMessage);


        } else {
            log.warn("工作单状态不是LOCK,state={},消息体={}",taskDO.getLockState(),productMessage);
        }

        return true;
    }


    // DO -> VO
    private ProductVO beanProcess(ProductDO productDO) {
        ProductVO productVO = new ProductVO();
        BeanUtils.copyProperties(productDO,productVO);
        productVO.setStock(productDO.getStock() - productDO.getLockStock());
        return productVO;
    }
}
