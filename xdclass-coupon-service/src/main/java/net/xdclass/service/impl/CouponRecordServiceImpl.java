package net.xdclass.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import net.xdclass.config.RabbitMQConfig;
import net.xdclass.enums.BizCodeEnum;
import net.xdclass.enums.CouponStateEnum;
import net.xdclass.enums.ProductOrderStateEnum;
import net.xdclass.enums.StockTaskStateEnum;
import net.xdclass.exception.BizException;
import net.xdclass.feign.ProductOrderFeignService;
import net.xdclass.interceptor.LoginInterceptor;
import net.xdclass.mapper.CouponRecordMapper;
import net.xdclass.mapper.CouponTaskMapper;
import net.xdclass.model.CouponRecordDO;
import net.xdclass.model.CouponRecordMessage;
import net.xdclass.model.CouponTaskDO;
import net.xdclass.model.LoginUser;
import net.xdclass.request.LockCouponRecordRequest;
import net.xdclass.service.CouponRecordService;
import net.xdclass.util.JsonData;
import net.xdclass.vo.CouponRecordVO;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CouponRecordServiceImpl implements CouponRecordService {

    @Resource
    private CouponRecordMapper couponRecordMapper;

    @Resource
    private CouponTaskMapper couponTaskMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitMQConfig rabbitMQConfig;

    @Autowired
    private ProductOrderFeignService orderFeignService;


    // 分页查询个人领券记录
    @Override
    public Map<String, Object> page(int page, int size) {

        LoginUser loginUser = LoginInterceptor.threadLocal.get();

        // 使用MyBatisPlus封装分页信息
        Page<CouponRecordDO> pageInfo = new Page<>(page,size);
        IPage<CouponRecordDO> recordDOIPage = couponRecordMapper.selectPage(pageInfo,new QueryWrapper<CouponRecordDO>()
                .eq("user_id",loginUser.getId()).orderByDesc("create_time"));

        Map<String,Object> pageMap = new HashMap<>(3);
        pageMap.put("total_record",recordDOIPage.getTotal());
        pageMap.put("total_page",recordDOIPage.getPages());
        pageMap.put("current_data",recordDOIPage.getRecords().stream().map(this::beanProcess).collect(Collectors.toList()));

        return pageMap;
    }


    // 查询领券记录详情
    @Override
    public CouponRecordVO findById(long recordId) {

        LoginUser loginUser = LoginInterceptor.threadLocal.get();

        CouponRecordDO couponRecordDO = couponRecordMapper.selectOne(new QueryWrapper<CouponRecordDO>()
                .eq("id",recordId).eq("user_id",loginUser.getId()));

        if(couponRecordDO == null){
            return null;
        }

        return beanProcess(couponRecordDO);
    }


    /**
     * 锁定优惠券
     *
     * 1）锁定优惠券：即更新优惠券使用状态至锁定状态
     * 2）在优惠券锁定任务表（couponTask表）中插入锁定记录
     * 3）发送优惠券库存已锁定的延迟消息，等待订单状态
     *
     * @param recordRequest 订单微服务的创建订单接口发来锁定优惠券请求
     */
    @Override
    public JsonData lockCouponRecords(LockCouponRecordRequest recordRequest) {
        // 当前登录用户
        LoginUser loginUser = LoginInterceptor.threadLocal.get();

        // 用户下单 -> RPC调用锁定优惠券 -> 优惠券锁定请求
        // 从优惠券锁定请求中提取下列参数：
        // 被锁定优惠券的ID列表
        List<Long> lockCouponRecordIds = recordRequest.getLockCouponRecordIds();
        // 与之关联的订单号
        String orderOutTradeNo = recordRequest.getOrderOutTradeNo();


        // 1) 锁定优惠券
        // 更新优惠券使用状态至【锁定状态】
        // 注意：此处的更新SQL语句要自己写！
        int updateRows = couponRecordMapper.lockUseStateBatch(loginUser.getId(), CouponStateEnum.USED.name(), lockCouponRecordIds);


        // 2) 在优惠券锁定任务表（couponTask表）中插入锁定记录
        // 将相关信息封装成一个优惠券锁定任务工作单列表！
        List<CouponTaskDO> couponTaskDOList = lockCouponRecordIds.stream().map(obj -> {
            CouponTaskDO couponTaskDO = new CouponTaskDO();
            couponTaskDO.setCreateTime(new Date());
            // 与之关联的订单号
            couponTaskDO.setOutTradeNo(orderOutTradeNo);
            // 将 被锁定优惠券的ID列表 依次传过来
            couponTaskDO.setCouponRecordId(obj);
            // 设置优惠券为锁定状态
            couponTaskDO.setLockState(StockTaskStateEnum.LOCK.name());
            return couponTaskDO;
        }).collect(Collectors.toList());

        // 批量插入优惠券锁定任务工作单
        int insertRows = couponTaskMapper.insertBatch(couponTaskDOList);

        log.info("锁定优惠券：updateRows={}",updateRows);
        log.info("新增优惠券锁定任务：insertRows={}",insertRows);


        // 3) 发送延迟消息，等待订单状态
        if(lockCouponRecordIds.size()==insertRows && insertRows==updateRows){

            // 【发送延迟消息】
            // 遍历优惠券锁定任务工作单
            for (CouponTaskDO couponTaskDO : couponTaskDOList){
                // 首先，生成优惠券库存锁定消息
                CouponRecordMessage couponRecordMessage = new CouponRecordMessage();

                // 消息内容：
                // 与之关联的订单号
                couponRecordMessage.setOutTradeNo(orderOutTradeNo);
                // 优惠券锁定任务的ID
                couponRecordMessage.setTaskId(couponTaskDO.getId());

                // 发送延迟消息：需要对应的交换机、延迟队列的路由key、具体消息
                rabbitTemplate.convertAndSend(rabbitMQConfig.getEventExchange(), rabbitMQConfig.getCouponReleaseDelayRoutingKey(), couponRecordMessage);
                log.info("优惠券锁定消息发送成功:{}", couponRecordMessage);
            }
            return JsonData.buildSuccess();
        }else {
            // 优惠券锁定失败
            throw new BizException(BizCodeEnum.COUPON_RECORD_LOCK_FAIL);
        }
    }


    /**
     * 释放优惠券：供 CouponMQListener 调用判断优惠券是否成功释放，即判断消息是否消费成功！
     *
     * 需要事务管理：
     * 1) 查询优惠券锁定任务工作单是否存在
     * 2) 查询优惠券状态，lock状态时才处理
     * 3) 查询订单状态
     *
     * @param recordMessage 优惠券库存锁定消息，经过一定延迟后，由死信队列监听器监听得到！！！
     * @return boolean 解锁成功与否 -> 消息是否成功消费
     */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public boolean releaseCouponRecord(CouponRecordMessage recordMessage) {

        // 1) 查询该优惠券锁定任务工作单是否存在
        CouponTaskDO taskDO = couponTaskMapper.selectOne(new QueryWrapper<CouponTaskDO>().eq("id",recordMessage.getTaskId()));
        if(taskDO == null){
            log.warn("优惠券锁定任务工作单不存在，msg={}",recordMessage);
            return true;
        }


        // 2) 查询优惠券状态，lock状态时才处理
        if(taskDO.getLockState().equalsIgnoreCase(StockTaskStateEnum.LOCK.name())){

            // 3) 再查询订单状态：RPC调用订单微服务的查询订单状态接口，返回订单状态的JSON数据
            JsonData jsonData = orderFeignService.queryProductOrderState(recordMessage.getOutTradeNo());

            // 正常响应，判断订单状态
            if(jsonData.getCode() == 0){
                String state = jsonData.getData().toString();

                // 如果订单为新建状态，即 NEW 状态，则返回给消息队列，重新投递
                if(state.equalsIgnoreCase(ProductOrderStateEnum.NEW.name())){
                    log.warn("订单状态为NEW，返回给消息队列，重新投递，msg={}",recordMessage);
                    return false;
                }

                // 如果订单已经支付，即 PAY 状态
                if(state.equalsIgnoreCase(ProductOrderStateEnum.PAY.name())){
                    // 如果已经支付，修改优惠券锁定任务状态为 FINISH
                    taskDO.setLockState(StockTaskStateEnum.FINISH.name());
                    couponTaskMapper.update(taskDO,new QueryWrapper<CouponTaskDO>().eq("id",recordMessage.getTaskId()));

                    log.info("订单已经支付，修改优惠券锁定任务状态为FINISH，msg={}",recordMessage);
                    return true;
                }
            }

            // 未正常响应，说明订单不存在，或者订单被取消
            // 修改优惠券锁定任务状态为 CANCEL
            taskDO.setLockState(StockTaskStateEnum.CANCEL.name());
            couponTaskMapper.update(taskDO,new QueryWrapper<CouponTaskDO>().eq("id",recordMessage.getTaskId()));

            // 恢复优惠券使用记录为 NEW
            couponRecordMapper.updateState(taskDO.getCouponRecordId(),CouponStateEnum.NEW.name());

            log.warn("订单不存在，或者订单被取消，确认消息，修改优惠券锁定任务状态为CANCEL，恢复优惠券使用记录为NEW，msg={}",recordMessage);

        }else {
            log.warn("优惠券状态不是LOCK，而是{}状态，msg={}",taskDO.getLockState(),recordMessage);
        }

        return true;
    }


    // DO -> VO
    private CouponRecordVO beanProcess(CouponRecordDO couponRecordDO) {
        CouponRecordVO couponRecordVO = new CouponRecordVO();
        BeanUtils.copyProperties(couponRecordDO,couponRecordVO);
        return couponRecordVO;
    }
}
