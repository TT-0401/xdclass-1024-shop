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
     * ๅๅๅ้กต
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
     * ๆ?นๆฎIDๆฅ่ฏขๅๅ่ฏฆๆ
     * @param productId
     */
    @Override
    public ProductVO findDetailById(long productId) {

        ProductDO productDO = productMapper.selectById(productId);

        return beanProcess(productDO);
    }


    /**
     * ๆน้ๆฅ่ฏขๅๅ
     * @param productIdList
     */
    @Override
    public List<ProductVO> findProductsByIdBatch(List<Long> productIdList) {

        List<ProductDO> productDOList =  productMapper.selectList(new QueryWrapper<ProductDO>().in("id",productIdList));

        List<ProductVO> productVOList = productDOList.stream().map(this::beanProcess).collect(Collectors.toList());

        return productVOList;
    }


    /**
     * ้ๅฎๅๅๅบๅญ
     *
     * 1) ้ๅ่ฎขๅไธญ็ๆฏไธชๅๅ๏ผ้ๅฎๆฏไธชๅๅ็่ดญไนฐๆฐ้
     * 2) ๆฏไธๆฌก้ๅฎ็ๆถๅ๏ผ้ฝ่ฆๅ้ๅปถ่ฟๆถๆฏ๏ผ
     *
     * @param lockProductRequest ่ฎขๅๅพฎๆๅก็ๅๅปบ่ฎขๅๆฅๅฃๅๆฅ้ๅฎๅๅๅบๅญ่ฏทๆฑ
     */
    @Override
    public JsonData lockProductStock(LockProductRequest lockProductRequest) {

        // ็จๆทไธๅ -> RPC่ฐ็จ้ๅฎๅๅๅบๅญ -> ๅๅๅบๅญ้ๅฎ่ฏทๆฑ
        // ไปๅๅๅบๅญ้ๅฎ่ฏทๆฑไธญๆๅไธๅๅๆฐ๏ผ
        // ่ขซ้ๅฎ็ๅๅๅฏน่ฑก็ๅ่กจ
        List<OrderItemRequest> itemList  = lockProductRequest.getOrderItemList();
        // ไธไนๅณ่็่ฎขๅๅท
        String outTradeNo = lockProductRequest.getOrderOutTradeNo();


        // ไธ่กไปฃ็?๏ผๆๅๅๅๅฏน่ฑก็IDๅนถๅ?ๅฅๅฐ้ๅ้้ข
        List<Long> productIdList = itemList.stream().map(OrderItemRequest::getProductId).collect(Collectors.toList());
        // ๆน้ๆฅ่ฏข่ฟไบๅๅ
        List<ProductVO> productVOList = this.findProductsByIdBatch(productIdList);
        // ไปฅๅๅIDไธบkey๏ผๅฐๅๅๅฏน่ฑก็ List ่ฝฌไธบ Map
        Map<Long,ProductVO> productMap = productVOList.stream().collect(Collectors.toMap(ProductVO::getId, Function.identity()));

        // 1) ้ๅ่ฎขๅไธญ็ๆฏไธชๅๅ๏ผ้ๅฎๆฏไธชๅๅ็่ดญไนฐๆฐ้
        for(OrderItemRequest item : itemList){
            // ้ๅฎๅๅ่ฎฐๅฝ
            int rows = productMapper.lockProductStock(item.getProductId(),item.getBuyNum());

            if(rows != 1){
                // ๅๅๅบๅญ้ๅฎๅคฑ่ดฅ
                throw new BizException(BizCodeEnum.ORDER_CONFIRM_LOCK_PRODUCT_FAIL);
            }else {

                // ๅๅปบๅๅ้ๅฎไปปๅกๅทฅไฝๅ๏ผๅนถ่ฎพ็ฝฎๅๅIDใ่ดญไนฐๆฐ้ใ้ๅฎ็ถๆใ่ฎขๅๅท็ญ
                ProductTaskDO productTaskDO = new ProductTaskDO();
                productTaskDO.setProductId(item.getProductId());
                productTaskDO.setBuyNum(item.getBuyNum());
                productTaskDO.setLockState(StockTaskStateEnum.LOCK.name());
                productTaskDO.setOutTradeNo(outTradeNo);
                ProductVO productVO = productMap.get(item.getProductId());
                productTaskDO.setProductName(productVO.getTitle());

                // ๆๅฅๅๅ้ๅฎไปปๅกๅทฅไฝๅ
                productTaskMapper.insert(productTaskDO);
                log.info("ๅๅๅบๅญ้ๅฎ๏ผๆฐๅขๅๅๅบๅญ้ๅฎไปปๅก:{}",productTaskDO);


                // 2) ๆฏไธๆฌก้ๅฎ็ๆถๅ๏ผ้ฝ่ฆๅ้ๅปถ่ฟๆถๆฏ๏ผ
                // ๅๅปบๅๅๅบๅญ้ๅฎๅปถ่ฟๆถๆฏ๏ผๅนถๅณ่่ฎขๅๅทๅไปปๅกID
                ProductMessage productMessage = new ProductMessage();
                productMessage.setOutTradeNo(outTradeNo);
                productMessage.setTaskId(productTaskDO.getId());
                // ๅ้ๅปถ่ฟๆถๆฏ
                rabbitTemplate.convertAndSend(rabbitMQConfig.getEventExchange(),rabbitMQConfig.getStockReleaseDelayRoutingKey(),productMessage);
                log.info("ๅๅๅบๅญ้ๅฎไฟกๆฏๅปถ่ฟๆถๆฏๅ้ๆๅ:{}",productMessage);
            }

        }

        return JsonData.buildSuccess();
    }


    /**
     * ้ๆพๅๅๅบๅญ๏ผไพ ProductStockMQListener ่ฐ็จๅคๆญๅๅๅบๅญๆฏๅฆๆๅ้ๆพ๏ผๅณๅคๆญๆถๆฏๆฏๅฆๆถ่ดนๆๅ๏ผ
     *
     * 1) ๆฅ่ฏขๅๅๅบๅญ้ๅฎไปปๅกๅทฅไฝๅๆฏๅฆๅญๅจ
     * 2) ๆฅ่ฏขๅๅๅบๅญ็ถๆ๏ผlock็ถๆๆถๆๅค็
     * 3) ๆฅ่ฏข่ฎขๅ็ถๆ
     *
     * ็ๅฌๅฐๅปถ่ฟๆถๆฏ๏ผ้ๆพๅๅๅบๅญ๏ผๅณๅฐๅถๅๅๅบๅญๆขๅค่ณๅบๆ็็ถๆ
     *
     * @param productMessage ๅๅๅบๅญ้ๅฎๆถๆฏ๏ผ็ป่ฟไธๅฎๅปถ่ฟๅ๏ผ็ฑๆญปไฟก้ๅ็ๅฌๅจ็ๅฌๅพๅฐ๏ผ๏ผ๏ผ
     * @return boolean ่งฃ้ๆๅไธๅฆ -> ๆถๆฏๆฏๅฆๆๅๆถ่ดน
     */
    @Override
    public boolean releaseProductStock(ProductMessage productMessage) {

        // 1) ๆฅ่ฏขๅๅๅบๅญ้ๅฎไปปๅกๅทฅไฝๅๆฏๅฆๅญๅจ
        ProductTaskDO taskDO = productTaskMapper.selectOne(new QueryWrapper<ProductTaskDO>().eq("id",productMessage.getTaskId()));
        if(taskDO == null){
            log.warn("ๅๅๅบๅญ้ๅฎไปปๅกๅทฅไฝๅไธๅญๅจ๏ผmsg:{}",productMessage);
            return true;
        }

        // 2) ๆฅ่ฏขๅๅๅบๅญ็ถๆ๏ผlock็ถๆๆถๆๅค็
        if(taskDO.getLockState().equalsIgnoreCase(StockTaskStateEnum.LOCK.name())){

            // 3) ๅๆฅ่ฏข่ฎขๅ็ถๆ๏ผRPC่ฐ็จ่ฎขๅๅพฎๆๅก็ๆฅ่ฏข่ฎขๅ็ถๆๆฅๅฃ๏ผ่ฟๅ่ฎขๅ็ถๆ็JSONๆฐๆฎ
            JsonData jsonData = orderFeignSerivce.queryProductOrderState(productMessage.getOutTradeNo());

            // ๆญฃๅธธๅๅบ๏ผๅคๆญ่ฎขๅ็ถๆ
            if(jsonData.getCode() == 0){
                String state = jsonData.getData().toString();

                // ๅฆๆ่ฎขๅไธบๆฐๅปบ็ถๆ๏ผๅณ NEW ็ถๆ๏ผๅ่ฟๅ็ปๆถๆฏ้ๅ๏ผ้ๆฐๆ้
                if(state.equalsIgnoreCase(ProductOrderStateEnum.NEW.name())){
                    log.warn("่ฎขๅ็ถๆไธบNEW๏ผ่ฟๅ็ปๆถๆฏ้ๅ๏ผ้ๆฐๆ้๏ผmsg={}",productMessage);
                    return false;
                }

                // ๅฆๆ่ฎขๅๅทฒ็ปๆฏไป๏ผๅณ PAY ็ถๆ
                if(ProductOrderStateEnum.PAY.name().equalsIgnoreCase(state)){
                    // ๅฆๆๅทฒ็ปๆฏไป๏ผไฟฎๆนๅๅๅบๅญ้ๅฎไปปๅก็ถๆไธบ FINISH
                    taskDO.setLockState(StockTaskStateEnum.FINISH.name());
                    productTaskMapper.update(taskDO,new QueryWrapper<ProductTaskDO>().eq("id",productMessage.getTaskId()));

                    log.info("่ฎขๅๅทฒ็ปๆฏไป๏ผไฟฎๆนๅๅๅบๅญ้ๅฎไปปๅก็ถๆไธบFINISH๏ผmsg={}",productMessage);
                    return true;
                }
            }

            // ๆชๆญฃๅธธๅๅบ๏ผ่ฏดๆ่ฎขๅไธๅญๅจ๏ผๆ่่ฎขๅ่ขซๅๆถ
            // ไฟฎๆนๅๅๅบๅญ้ๅฎไปปๅก็ถๆไธบ CANCEL
            taskDO.setLockState(StockTaskStateEnum.CANCEL.name());
            productTaskMapper.update(taskDO,new QueryWrapper<ProductTaskDO>().eq("id",productMessage.getTaskId()));

            // ๆขๅคๅๅๅบๅญ๏ผๅณ ้ๅฎๅบๅญ็ๅผ - ๅฝๅ่ดญไนฐ็ๆฐ้
            productMapper.unlockProductStock(taskDO.getProductId(),taskDO.getBuyNum());

            log.warn("่ฎขๅไธๅญๅจ๏ผๆ่่ฎขๅ่ขซๅๆถ๏ผ็กฎ่ฎคๆถๆฏ๏ผไฟฎๆนๅๅๅบๅญ้ๅฎไปปๅก็ถๆไธบCANCEL๏ผๆขๅคๅๅๅบๅญ๏ผmsg={}",productMessage);


        } else {
            log.warn("ๅทฅไฝๅ็ถๆไธๆฏLOCK,state={},ๆถๆฏไฝ={}",taskDO.getLockState(),productMessage);
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
