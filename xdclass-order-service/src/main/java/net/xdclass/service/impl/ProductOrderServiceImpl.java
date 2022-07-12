package net.xdclass.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import net.xdclass.component.PayFactory;
import net.xdclass.config.RabbitMQConfig;
import net.xdclass.constant.CacheKey;
import net.xdclass.constant.TimeConstant;
import net.xdclass.enums.*;
import net.xdclass.exception.BizException;
import net.xdclass.feign.CouponFeignSerivce;
import net.xdclass.feign.ProductFeignService;
import net.xdclass.feign.UserFeignService;
import net.xdclass.interceptor.LoginInterceptor;
import net.xdclass.mapper.ProductOrderItemMapper;
import net.xdclass.mapper.ProductOrderMapper;
import net.xdclass.model.LoginUser;
import net.xdclass.model.OrderMessage;
import net.xdclass.model.ProductOrderDO;
import net.xdclass.model.ProductOrderItemDO;
import net.xdclass.request.*;
import net.xdclass.service.ProductOrderService;
import net.xdclass.util.CommonUtil;
import net.xdclass.util.JsonData;
import net.xdclass.vo.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;


@Service
@Slf4j
public class ProductOrderServiceImpl implements ProductOrderService {

    @Resource
    private ProductOrderMapper productOrderMapper;

    @Resource
    private ProductOrderItemMapper orderItemMapper;

    @Autowired
    private UserFeignService userFeignService;

    @Autowired
    private CouponFeignSerivce couponFeignSerivce;

    @Autowired
    private ProductFeignService productFeignService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitMQConfig rabbitMQConfig;

    @Autowired
    private PayFactory payFactory;

    @Autowired
    private StringRedisTemplate redisTemplate;


    /**
     * 查询订单状态
     * @param outTradeNo 订单号
     * @return String 订单状态
     */
    @Override
    public String queryProductOrderState(String outTradeNo) {
        // 根据订单号查找到对应的订单
        ProductOrderDO productOrderDO = productOrderMapper.selectOne(new QueryWrapper<ProductOrderDO>().eq("out_trade_no",outTradeNo));

        if (productOrderDO == null){
            return "";
        }else {
            // 获取订单状态
            return productOrderDO.getState();
        }
    }


    /**
     * 分页查询订单列表：DO -> VO
     * @param page 当前页
     * @param size 每页显示多少条
     * @param state 订单状态
     */
    @Override
    public Map<String, Object> page(int page, int size, String state) {
        // 当前用户
        LoginUser loginUser = LoginInterceptor.threadLocal.get();

        // 分页信息
        Page<ProductOrderDO> pageInfo = new Page<>(page,size);

        // 订单列表的分页查询结果
        IPage<ProductOrderDO> orderDOPage = null;
        if (StringUtils.isBlank(state)){
            orderDOPage = productOrderMapper.selectPage(pageInfo,new QueryWrapper<ProductOrderDO>().eq("user_id",loginUser.getId()));
        }else {
            orderDOPage = productOrderMapper.selectPage(pageInfo,new QueryWrapper<ProductOrderDO>().eq("user_id",loginUser.getId()).eq("state",state));
        }

        // 分页查询获取订单列表
        List<ProductOrderDO> productOrderDOList = orderDOPage.getRecords();
        // productOrderDOList -> productOrderVOList
        List<ProductOrderVO> productOrderVOList = productOrderDOList.stream().map(productOrderDO -> {
            ProductOrderVO productOrderVO = new ProductOrderVO();
            // productOrderDO -> productOrderVO
            BeanUtils.copyProperties(productOrderDO,productOrderVO);

            // 子订单列表
            List<ProductOrderItemDO> itemDOList = orderItemMapper.selectList(new QueryWrapper<ProductOrderItemDO>().eq("product_order_id",productOrderDO.getId()));
            // itemDOList -> itemVOList
            List<OrderItemVO> itemVOList = itemDOList.stream().map(itemDO -> {
                OrderItemVO itemVO = new OrderItemVO();
                // itemDO -> itemVO
                BeanUtils.copyProperties(itemDO,itemVO);
                return itemVO;
            }).collect(Collectors.toList());
            productOrderVO.setOrderItemList(itemVOList);

            return productOrderVO;
        }).collect(Collectors.toList());

        Map<String,Object> pageMap = new HashMap<>(3);
        pageMap.put("total_record",orderDOPage.getTotal());
        pageMap.put("total_page",orderDOPage.getPages());
        pageMap.put("current_data",productOrderVOList);

        return pageMap;
    }


    /**
     * 下单支付
     *
     * * 防重提交：任何提交表单的时候，都可以采用token令牌机制避免重复点击
     *
     * * 用户微服务 -> 确认收货地址 (定义私有方法)
     * * 商品微服务 -> 获取购物车中的商品项，同时也会清空购物车中对应的商品
     * * 优惠券微服务 -> 获取优惠券 -> 订单验价 (定义私有方法)
     *
     * * 优惠券微服务 -> 锁定优惠券
     * * 商品微服务 -> 锁定商品库存
     *
     * * 创建订单对象 (定义私有方法)
     * * 创建子订单对象 (定义私有方法)
     *
     * * 发送延迟消息 -> 用于自动关单
     * * 创建支付信息 -> 对接第三方支付
     *
     * @param orderRequest 下单请求
     * @return payResult 支付结果
     */
    @Override
    public JsonData confirmOrder(ConfirmOrderRequest orderRequest) {
        // 当前登录用户
        LoginUser loginUser = LoginInterceptor.threadLocal.get();

        // 采用token令牌机制避免重复提交订单请求
        String orderToken = orderRequest.getToken();
        // 令牌为空
        if (StringUtils.isBlank(orderToken)) throw new BizException(BizCodeEnum.ORDER_CONFIRM_TOKEN_NOT_EXIST);
        // 校验令牌，删除令牌。
        // 下单时需要提交token并检验和删除，使用 lua脚本 实现原子操作
        String script = "if redis.call('get',KEYS[1]) == ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end";
        // 执行该 lua 脚本，成功，则返回1，失败，则返回0
        Long result = redisTemplate.execute(new DefaultRedisScript<>(script,Long.class), List.of(String.format(CacheKey.SUBMIT_ORDER_TOKEN_KEY,loginUser.getId())), orderToken);
        // 令牌校验失败
        if (result == 0L) throw new BizException(BizCodeEnum.ORDER_CONFIRM_TOKEN_EQUAL_FAIL);

        // 生成订单号
        String orderOutTradeNo = CommonUtil.getStringNumRandom(32);

        // 确认收货地址
        ProductOrderAddressVO addressVO = this.getUserAddress(orderRequest.getAddressId());
        log.info("收货地址信息：{}",addressVO);

        // cartItemVOList -> cartItemData -> orderItemVOList
        // 获取用户加入购物车中的商品ID列表
        List<Long> productIdList = orderRequest.getProductIdList();
        // 获取购物车中的商品子项 VO 的 json 格式数据，同时也会清空购物车中对应的商品
        JsonData cartItemData = productFeignService.confirmOrderCartItem(productIdList);
        // 将购物车中的商品子项 VO 的 json 格式数据再转回为 VO 对象
        // new TypeReference<>(){} 作用：根据左边的数据类型，转为对应的数据类型！！！
        List<OrderItemVO> orderItemVOList = cartItemData.getData(new TypeReference<>(){});
        log.info("获取到的购物车中商品：{}",orderItemVOList);
        // 若购物车中商品不存在，抛出异常
        if(orderItemVOList == null) throw new BizException(BizCodeEnum.ORDER_CONFIRM_CART_ITEM_NOT_EXIST);

        // 验证价格，减去商品优惠券
        this.checkPrice(orderItemVOList,orderRequest);


        // 锁定优惠券：RPC调用优惠券微服务
        this.lockCouponRecords(orderRequest,orderOutTradeNo);

        // 锁定商品库存：RPC调用商品微服务
        this.lockProductStocks(orderItemVOList,orderOutTradeNo);


        // 创建订单，并返回订单对象
        ProductOrderDO productOrderDO = this.saveProductOrder(orderRequest,loginUser,orderOutTradeNo,addressVO);

        // 创建每件商品的子订单
        this.saveProductOrderItems(orderOutTradeNo, productOrderDO.getId(), orderItemVOList);


        // 发送关单延迟消息，用于自动关单
        OrderMessage orderMessage = new OrderMessage();
        // 消息中只包含订单号
        orderMessage.setOutTradeNo(orderOutTradeNo);
        // 发送消息
        rabbitTemplate.convertAndSend(rabbitMQConfig.getEventExchange(),rabbitMQConfig.getOrderCloseDelayRoutingKey(),orderMessage);


        // 创建支付
        // 配置支付信息
        PayInfoVO payInfoVO = new PayInfoVO(orderOutTradeNo,
                productOrderDO.getPayAmount(),orderRequest.getPayType(),
                orderRequest.getClientType(), orderItemVOList.get(0).getProductTitle(),"", TimeConstant.ORDER_PAY_TIMEOUT_MILLS);
        // 调用支付工厂类，传入支付信息，返回支付结果
        String payResult = payFactory.pay(payInfoVO);
        // 支付结果不为空，说明创建支付成功，返回第三方支付页面
        if (StringUtils.isNotBlank(payResult)){
            log.info("创建支付订单成功:payInfoVO={},payResult={}",payInfoVO,payResult);
            return JsonData.buildSuccess(payResult);
        }else {
            log.error("创建支付订单失败:payInfoVO={},payResult={}",payInfoVO,payResult);
            return JsonData.buildResult(BizCodeEnum.PAY_ORDER_FAIL);
        }
    }

    /*
     * 获取收货地址详情
     */
    private ProductOrderAddressVO getUserAddress(long addressId) {
        // RPC调用用户微服务，根据地址ID查询收货地址，返回 收货地址VO 的 json 格式数据
        JsonData addressData = userFeignService.detailAddress(addressId);

        // 地址不存在，抛出异常
        if (addressData.getCode() != 0){
            log.error("获取收货地址失败，msg:{}", addressData);
            throw new BizException(BizCodeEnum.ADDRESS_NO_EXITS);
        }

        // 将 收货地址VO 的 json 格式数据再转回为 收货地址VO
        // new TypeReference<>(){} 会根据需要的类型自动指定返回的类型！！！
        return addressData.getData(new TypeReference<>(){});
    }

    /*
     * 验证价格：后端计算的实付价格是否与前端传来的实付价格一致
     *
     * 1) 统计订单商品总价格
     * 2) 获取优惠券(需先判断优惠券是否可用以及是否满足使用门槛条件)，并计算 最终的价格 = 总价 - 优惠券的价格
     * 3) 将后端计算的实付价格与前端传来的实付价格相比较，返回验价结果
     *
     * @param orderItemVOList 订单中的商品列表
     * @param orderRequest 下单请求
     */
    private void checkPrice(List<OrderItemVO> orderItemVOList, ConfirmOrderRequest orderRequest) {

        // 1) 统计订单商品总价格
        BigDecimal realPayAmount = new BigDecimal("0");
        if (orderItemVOList != null){
            for (OrderItemVO orderItemVO : orderItemVOList){
                // 获取每个商品子项的实付价格，即 商品现价 X 购买量
                BigDecimal itemRealPayAmount = orderItemVO.getTotalAmount();
                // 累加订单中所有商品的实付价格，得到最终的订单总实付价格
                realPayAmount = realPayAmount.add(itemRealPayAmount);
            }
        }

        // 2) 获取优惠券，先判断是否可以使用，再使用优惠券
        // 调用自定义方法，获取优惠券，并判断是否可用
        CouponRecordVO couponRecordVO = getCartCouponRecord(orderRequest.getCouponRecordId());
        // 计算订单实付价格是否满足优惠券使用条件
        if (couponRecordVO != null){
            // 实付价格 < 优惠券门槛，无法使用优惠券
            if (realPayAmount.compareTo(couponRecordVO.getConditionPrice()) < 0){
                throw new BizException(BizCodeEnum.ORDER_CONFIRM_COUPON_FAIL);
            }
            // 实付价格 < 优惠券面额，实付 0
            if (realPayAmount.compareTo(couponRecordVO.getPrice()) < 0){
                realPayAmount = BigDecimal.ZERO;
            // 实付价格 > 优惠券面额，实付 = 实付 - 优惠券面额
            }else {
                realPayAmount = realPayAmount.subtract(couponRecordVO.getPrice());
            }
        }

        // 3) 如果此处后端计算的实付价格不等于前端传来的实付价格，则说明验价失败！
        if (realPayAmount.compareTo(orderRequest.getRealPayAmount()) != 0){
            log.error("订单验价失败：{}", orderRequest);
            throw new BizException(BizCodeEnum.ORDER_CONFIRM_PRICE_FAIL);
        }
    }

    // 获取优惠券
    private CouponRecordVO getCartCouponRecord(Long couponRecordId) {

        if (couponRecordId == null || couponRecordId < 0) return null;

        // 调用优惠券微服务 -> 获取优惠券
        JsonData couponData = couponFeignSerivce.findUserCouponRecordById(couponRecordId);

        // 优惠券获取失败
        if (couponData.getCode() != 0) throw new BizException(BizCodeEnum.ORDER_CONFIRM_COUPON_FAIL);

        // 优惠券获取成功
        if (couponData.getCode() == 0){

            CouponRecordVO couponRecordVO = couponData.getData(new TypeReference<>(){});

            // 优惠券不可用
            if (!couponAvailable(couponRecordVO)){
                log.error("优惠券不可用");
                throw new BizException(BizCodeEnum.COUPON_UNAVAILABLE);
            }

            return couponRecordVO;
        }

        return null;
    }

    // 判断优惠券是否可用
    private boolean couponAvailable(CouponRecordVO couponRecordVO) {
        // 先判断使用状态
        if (couponRecordVO.getUseState().equalsIgnoreCase(CouponStateEnum.NEW.name())){
            long currentTimestamp = CommonUtil.getCurrentTimestamp();
            long end = couponRecordVO.getEndTime().getTime();
            long start = couponRecordVO.getStartTime().getTime();
            // 再判断优惠券是否在有效期内
            return currentTimestamp >= start && currentTimestamp <= end;
        }
        return false;
    }

    /*
     * 锁定优惠券
     * @param orderRequest 下单请求
     * @param orderOutTradeNo 订单号
     */
    private void lockCouponRecords(ConfirmOrderRequest orderRequest, String orderOutTradeNo) {
        // 订单可能使用了多个优惠券，都需要锁定
        List<Long> lockCouponRecordIds = new ArrayList<>();
        // 注意：如果优惠券记录ID为空或者小于0，则不用优惠券
        if (orderRequest.getCouponRecordId() > 0){

            // 从下单请求中提取出优惠券记录ID
            lockCouponRecordIds.add(orderRequest.getCouponRecordId());

            // 创建优惠券锁定请求
            LockCouponRecordRequest lockCouponRecordRequest = new LockCouponRecordRequest();
            lockCouponRecordRequest.setLockCouponRecordIds(lockCouponRecordIds);
            lockCouponRecordRequest.setOrderOutTradeNo(orderOutTradeNo);

            // 向优惠券微服务发起锁定优惠券请求
            JsonData jsonData = couponFeignSerivce.lockCouponRecords(lockCouponRecordRequest);

            if (jsonData.getCode() != 0){
                log.error("优惠券锁定失败：{}",lockCouponRecordRequest);
                throw new BizException(BizCodeEnum.COUPON_RECORD_LOCK_FAIL);
            }
        }
    }

    /*
     * 锁定商品库存
     * @param orderItemList 订单中的商品子项
     * @param orderOutTradeNo 订单号
     */
    private void lockProductStocks(List<OrderItemVO> orderItemList, String orderOutTradeNo) {
        // OrderItemVO -> Request：只需要商品的ID和购买数量
        List<OrderItemRequest> itemRequestList = orderItemList.stream().map(obj -> {
            OrderItemRequest request = new OrderItemRequest();
            request.setProductId(obj.getProductId());
            request.setBuyNum(obj.getBuyNum());
            return request;
        }).collect(Collectors.toList());

        // 创建商品库存锁定请求
        LockProductRequest lockProductRequest = new LockProductRequest();
        lockProductRequest.setOrderOutTradeNo(orderOutTradeNo);
        lockProductRequest.setOrderItemList(itemRequestList);

        // 向商品微服务发起锁定商品库存请求
        JsonData jsonData = productFeignService.lockProductStock(lockProductRequest);

        if(jsonData.getCode() != 0){
            log.error("商品库存锁定失败：{}",lockProductRequest);
            throw new BizException(BizCodeEnum.ORDER_CONFIRM_LOCK_PRODUCT_FAIL);
        }
    }

    /*
     * 创建订单，并保存至数据库
     * @param orderRequest 下单请求
     * @param loginUser 用户
     * @param orderOutTradeNo 订单号
     * @param addressVO 收货地址
     * @return ProductOrderDO
     */
    private ProductOrderDO saveProductOrder(ConfirmOrderRequest orderRequest, LoginUser loginUser, String orderOutTradeNo, ProductOrderAddressVO addressVO) {

        // 创建订单实例
        ProductOrderDO productOrderDO = new ProductOrderDO();

        // 用户信息
        productOrderDO.setUserId(loginUser.getId());
        productOrderDO.setHeadImg(loginUser.getHeadImg());
        productOrderDO.setNickname(loginUser.getName());

        // 订单信息
        productOrderDO.setOutTradeNo(orderOutTradeNo);
        productOrderDO.setCreateTime(new Date());
        productOrderDO.setDel(0);
        productOrderDO.setOrderType(ProductOrderTypeEnum.DAILY.name());

        // 未使用优惠券的价格
        productOrderDO.setTotalAmount(orderRequest.getTotalAmount());
        // 实际支付的价格
        productOrderDO.setPayAmount(orderRequest.getRealPayAmount());

        // 订单状态
        productOrderDO.setState(ProductOrderStateEnum.NEW.name());

        // 支付类型
        productOrderDO.setPayType(ProductOrderPayTypeEnum.valueOf(orderRequest.getPayType()).name());

        // 收货地址
        productOrderDO.setReceiverAddress(JSON.toJSONString(addressVO));

        // 插入订单对象至数据库
        productOrderMapper.insert(productOrderDO);

        return productOrderDO;
    }

    /*
     * 创建每件商品的子订单，并保存至数据库
     * @param orderOutTradeNo 订单号
     * @param orderRecordId 订单记录在数据库中的ID
     * @param orderItemList 订单中的商品子项
     */
    private void saveProductOrderItems(String orderOutTradeNo, Long orderRecordId, List<OrderItemVO> orderItemList) {
        // OrderItemVOList -> ProductOrderItemDOList
        List<ProductOrderItemDO> list = orderItemList.stream().map(obj -> {
                    ProductOrderItemDO itemDO = new ProductOrderItemDO();
                    itemDO.setBuyNum(obj.getBuyNum());
                    itemDO.setProductId(obj.getProductId());
                    itemDO.setProductImg(obj.getProductImg());
                    itemDO.setProductName(obj.getProductTitle());
                    itemDO.setOutTradeNo(orderOutTradeNo);
                    itemDO.setCreateTime(new Date());
                    itemDO.setAmount(obj.getAmount());
                    itemDO.setTotalAmount(obj.getTotalAmount());
                    itemDO.setProductOrderId(orderRecordId);
                    return itemDO;
                }
        ).collect(Collectors.toList());

        // 批量插入每个商品项的子订单对象
        orderItemMapper.insertBatch(list);
    }


    /**
     * 订单重新支付：同样的订单，只是剩余支付时间一直在减少
     *            先从数据库中查到已经创建好的订单，再重新支付!!!
     * @param repayOrderRequest 重新支付请求
     * */
    @Override
    @Transactional
    public JsonData repay(RepayOrderRequest repayOrderRequest) {
        // 当前用户
        LoginUser loginUser = LoginInterceptor.threadLocal.get();

        // 查询订单
        ProductOrderDO productOrderDO = productOrderMapper.selectOne(new QueryWrapper<ProductOrderDO>().eq("out_trade_no",repayOrderRequest.getOutTradeNo()).eq("user_id",loginUser.getId()));

        log.info("订单状态:{}", productOrderDO);

        // 订单不存在
        if (productOrderDO == null){
            return JsonData.buildResult(BizCodeEnum.PAY_ORDER_NOT_EXIST);
        }

        // 订单状态不对：不是NEW状态
        if (!productOrderDO.getState().equalsIgnoreCase(ProductOrderStateEnum.NEW.name())){
            return JsonData.buildResult(BizCodeEnum.PAY_ORDER_STATE_ERROR);
        }else {
            // 订单从创建到现在的存活时间
            long orderLiveTime = CommonUtil.getCurrentTimestamp() - productOrderDO.getCreateTime().getTime();

            // 创建订单是临界点，所以需要再增加1分钟多几秒，如订单已创建29分钟时，也不能支付了
            orderLiveTime = orderLiveTime + 70*1000;

            // 订单存活时间大于订单超时时间，则订单失效
            if (orderLiveTime > TimeConstant.ORDER_PAY_TIMEOUT_MILLS){
                return JsonData.buildResult(BizCodeEnum.PAY_ORDER_PAY_TIMEOUT);
            }else {
                // 订单剩下的有效时间 = 总时间 - 存活的时间
                long timeout = TimeConstant.ORDER_PAY_TIMEOUT_MILLS - orderLiveTime;

                // 配置支付信息：【更新支付超时时间】
                PayInfoVO payInfoVO = new PayInfoVO(productOrderDO.getOutTradeNo(), productOrderDO.getPayAmount(),
                        repayOrderRequest.getPayType(), repayOrderRequest.getClientType(),
                        productOrderDO.getOutTradeNo(),"",timeout);

                // 调用支付工厂类，传入支付信息，返回支付结果
                String payResult = payFactory.pay(payInfoVO);
                // 支付结果不为空，创建支付成功，返回第三方支付页面
                if (StringUtils.isNotBlank(payResult)){
                    log.info("创建二次支付订单成功：payInfoVO={},payResult={}",payInfoVO,payResult);
                    return JsonData.buildSuccess(payResult);
                }else {
                    log.error("创建二次支付订单失败：payInfoVO={},payResult={}",payInfoVO,payResult);
                    return JsonData.buildResult(BizCodeEnum.PAY_ORDER_FAIL);
                }
            }
        }
    }


    /**
     * 由 CallbackController 调用
     *
     * 处理支付回调通知，根据返回的支付结果，更新数据库中的订单状态
     *
     * @param payType 支付方式
     * @param paramsMap 支付回调通知中返回的所有参数
     * @return JsonData.code = 0 表示更新数据库中订单支付状态成功
     */
    @Override
    public JsonData handlerOrderCallbackMsg(ProductOrderPayTypeEnum payType, Map<String, String> paramsMap) {

        // 支付宝支付
        if (payType.name().equalsIgnoreCase(ProductOrderPayTypeEnum.ALIPAY.name())){
            // 根据支付回调通知 POST 请求传回的参数：
            // 获取商户订单号
            String outTradeNo = paramsMap.get("out_trade_no");
            // 获取交易的状态
            String tradeStatus = paramsMap.get("trade_status");

            // 如果得到的状态为支付成功或交易完成
            if("TRADE_SUCCESS".equalsIgnoreCase(tradeStatus) || "TRADE_FINISHED".equalsIgnoreCase(tradeStatus)){
                // 则【更新数据库中该订单状态】：NEW -> PAY
                productOrderMapper.updateOrderPayState(outTradeNo,ProductOrderStateEnum.PAY.name(),ProductOrderStateEnum.NEW.name());
                // 更新成功，JsonData.code = 0
                return JsonData.buildSuccess();
            }

        // 微信支付  TODO
        } else if (payType.name().equalsIgnoreCase(ProductOrderPayTypeEnum.WECHAT.name())){

        }

        return JsonData.buildResult(BizCodeEnum.PAY_ORDER_CALLBACK_NOT_SUCCESS);
    }


    /**
     * 由 ProductOrderMQListener 调用
     *
     * 定时关单：用于MQ监听到关单消息后，调用此方法查询是否关单成功
     *         先查询数据库中的订单状态，若显示未支付，再向第三方支付查询订单是否真的尚未支付
     *         即：显示订单未支付时需要二次确认！！！
     *
     * @param orderMessage 关单消息
     * @return boolean true 关单成功
     */
    @Override
    public boolean closeProductOrder(OrderMessage orderMessage) {

        // 根据关单消息，查询对应订单
        ProductOrderDO productOrderDO = productOrderMapper.selectOne(new QueryWrapper<ProductOrderDO>().eq("out_trade_no",orderMessage.getOutTradeNo()));

        // 订单不存在
        if (productOrderDO == null){
            log.warn("直接确认消息，订单不存在，msg:{}",orderMessage);
            return true;
        }

        // 先向数据库查询订单状态，若显示订单已经支付，则【直接确认消息】，订单已经支付
        if (productOrderDO.getState().equalsIgnoreCase(ProductOrderStateEnum.PAY.name())){
            log.info("直接确认消息，订单已经支付，msg:{}",orderMessage);
            return true;
        }

        // 若数据库中显示订单尚未支付，再向第三方支付查询订单是否真的尚未支付
        // 配置支付信息
        PayInfoVO payInfoVO = new PayInfoVO();
        payInfoVO.setPayType(productOrderDO.getPayType());
        payInfoVO.setOutTradeNo(orderMessage.getOutTradeNo());
        // 根据支付信息，查询支付是否成功
        String payResult = payFactory.queryPaySuccess(payInfoVO);

        // 支付结果为空，则未支付成功，本地取消订单
        if (StringUtils.isBlank(payResult)){
            // 【更新数据库中该订单状态】：NEW -> CANCEL
            productOrderMapper.updateOrderPayState(productOrderDO.getOutTradeNo(),ProductOrderStateEnum.CANCEL.name(),ProductOrderStateEnum.NEW.name());
            log.info("支付结果为空，未支付成功，本地取消订单，msg:{}", orderMessage);

        // 支付成功，主动地把订单状态改为已经支付，造成该原因的情况可能是支付回调通知有问题，没有及时正确的通知数据库更改订单状态！
        }else {
            // 【更新数据库中该订单状态】：NEW -> PAY
            productOrderMapper.updateOrderPayState(productOrderDO.getOutTradeNo(),ProductOrderStateEnum.PAY.name(),ProductOrderStateEnum.NEW.name());
            log.warn("支付成功，主动地把订单状态改为已经支付，造成该原因的情况可能是支付回调通知有问题，msg:{}", orderMessage);
        }

        return true;
    }


}
