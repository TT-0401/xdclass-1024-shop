package net.xdclass.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import net.xdclass.enums.BizCodeEnum;
import net.xdclass.enums.CouponCategoryEnum;
import net.xdclass.enums.CouponPublishEnum;
import net.xdclass.enums.CouponStateEnum;
import net.xdclass.exception.BizException;
import net.xdclass.interceptor.LoginInterceptor;
import net.xdclass.mapper.CouponMapper;
import net.xdclass.mapper.CouponRecordMapper;
import net.xdclass.model.CouponDO;
import net.xdclass.model.CouponRecordDO;
import net.xdclass.model.LoginUser;
import net.xdclass.request.NewUserCouponRequest;
import net.xdclass.service.CouponService;
import net.xdclass.util.CommonUtil;
import net.xdclass.util.JsonData;
import net.xdclass.vo.CouponVO;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.Cacheable;
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
public class CouponServiceImpl implements CouponService {

    @Resource
    private CouponMapper couponMapper;

    @Resource
    private CouponRecordMapper couponRecordMapper;

    @Resource
    private RedissonClient redissonClient;


    // 分页查询优惠券方法
    @Override
    // value：缓存名，它指定了你的缓存存放在哪块命名空间
    // key：缓存的key
    @Cacheable(value = {"coupon"}, key = "#root.methodName + '_' + #page + '_' + #size")
    public Map<String, Object> pageCouponActivity(int page, int size) {

        // MyBatisPlus的分页插件
        Page<CouponDO> pageInfo = new Page<>(page,size);

        // 优惠券第一页
        IPage<CouponDO> couponDOIPage = couponMapper.selectPage(pageInfo, new QueryWrapper<CouponDO>()
            .eq("publish",CouponPublishEnum.PUBLISH)
            .eq("category", CouponCategoryEnum.PROMOTION)
            .orderByDesc("create_time"));


        Map<String,Object> pageMap = new HashMap<>(3);
        //总条数
        pageMap.put("total_record", couponDOIPage.getTotal());
        //总页数
        pageMap.put("total_page",couponDOIPage.getPages());
        //当前页数据
        pageMap.put("current_data",couponDOIPage.getRecords().stream().map(this::beanProcess).collect(Collectors.toList()));


        return pageMap;
    }


    /**
     * 领取促销优惠券 【分布式锁 + 事务管理】
     *
     * * 注意：将加锁的的操作放在事务的外层，保证事务提交成功后，才能进行锁的释放！！！
     *        即可将加锁操作放在 Controller 层
     *
     * 1、获取优惠券是否存在
     * 2、校验优惠券是否可以领取：时间、库存、领取超过限制
     * 3、扣减库存
     * 4、保存领劵记录
     *
     * @param couponId 优惠券ID
     * @param category 优惠券种类
     * @return
     */
    // 有异常就回滚事务！
    @Transactional(rollbackFor=Exception.class, propagation=Propagation.REQUIRED)
    @Override
    public JsonData addCoupon(long couponId, CouponCategoryEnum category) {

        LoginUser loginUser = LoginInterceptor.threadLocal.get();

        // 【使用Redisson分布式锁，防止用户超领优惠券】
        // 为了防止多用户并发领券，使得优惠券超发，lockKey 加上当前用户ID，使锁粒度更细化
//        String lockKey = "lock:coupon:" + couponId + ":" + loginUser.getId();
//        RLock rLock = redissonClient.getLock(lockKey);

        // 多个线程进入，会阻塞等待释放锁，默认30秒，然后有 watch dog 自动续期
//        rLock.lock();
        // 加锁10秒钟过期，没有 watch dog 功能，无法自动续期
        //rLock.lock(10,TimeUnit.SECONDS);

//        log.info("领劵接口加锁成功:{}",Thread.currentThread().getId());

//        try{

            // 在数据库中查询该优惠券
            CouponDO couponDO = couponMapper.selectOne(new QueryWrapper<CouponDO>()
                    .eq("id", couponId)
                    .eq("category", category.name()));

            // 优惠券是否可以领取
            this.checkCoupon(couponDO, loginUser.getId());

            // 构建领劵记录
            CouponRecordDO couponRecordDO = new CouponRecordDO();
            BeanUtils.copyProperties(couponDO, couponRecordDO);
            couponRecordDO.setCreateTime(new Date());
            couponRecordDO.setUseState(CouponStateEnum.NEW.name());
            couponRecordDO.setUserId(loginUser.getId());
            couponRecordDO.setUserName(loginUser.getName());
            couponRecordDO.setCouponId(couponId);
            couponRecordDO.setId(null);

            // 扣减库存
            int rows = couponMapper.reduceStock(couponId);

            // 异常，用于测试事务管理！
            //int flag = 1/0;

            // 库存扣减成功才保存记录
            if (rows == 1) {
                couponRecordMapper.insert(couponRecordDO);
            } else {
                log.warn("发放优惠券失败:{},用户:{}", couponDO, loginUser);
                throw new BizException(BizCodeEnum.COUPON_NO_STOCK);
            }

//        } finally {

//            rLock.unlock();
//            log.info("领劵接口解锁成功");

//        }

        return JsonData.buildSuccess();
    }


    /**
     * 新用户注册 -> RPC -> 发放新人优惠券
     * 注意：
     *      用户微服务进行新用户注册时，尚未生成 token，优惠券微服务需要根据该请求构建一个登录用户
     *      本地直接调用上面的领取优惠券的方法时，需要构造一个登录用户存储在 threadLocal
     *
     * @param newUserCouponRequest 新用户领券请求
     * @return
     */
    @Transactional(rollbackFor=Exception.class, propagation=Propagation.REQUIRED)
    @Override
    public JsonData initNewUserCoupon(NewUserCouponRequest newUserCouponRequest) {
        // 用户微服务进行新用户注册时，尚未生成 token，优惠券微服务需要根据该新人优惠券发放请求构建一个登录用户！！！
        LoginUser loginUser = new LoginUser();
        loginUser.setId(newUserCouponRequest.getUserId());
        loginUser.setName(newUserCouponRequest.getName());

        // 将该登录用户存到 threadLocal 中，方便本地直接调用上面的领取优惠券的方法时使用！！！
        LoginInterceptor.threadLocal.set(loginUser);

        // 查询新用户有哪些优惠券可领
        List<CouponDO> couponDOList = couponMapper.selectList(new QueryWrapper<CouponDO>()
                .eq("category", CouponCategoryEnum.NEW_USER.name()));

        for(CouponDO couponDO : couponDOList){
            // 调用上面的领取优惠券方法，领取新人优惠券
            // 幂等操作，调用需要加锁
            this.addCoupon(couponDO.getId(), CouponCategoryEnum.NEW_USER);
        }

        return JsonData.buildSuccess();
    }


    // 校验优惠券是否可以领取
    private void checkCoupon(CouponDO couponDO, Long userId) {

        //优惠券是否存在
        if(couponDO == null){
            throw new BizException(BizCodeEnum.COUPON_NO_EXITS);
        }

        //库存是否足够
        if(couponDO.getStock() <= 0){
            throw new BizException(BizCodeEnum.COUPON_NO_STOCK);
        }

        //判断是否为发布状态
        if(!couponDO.getPublish().equals(CouponPublishEnum.PUBLISH.name())){
            throw new BizException(BizCodeEnum.COUPON_GET_FAIL);
        }

        //是否在领取时间范围内
        long time = CommonUtil.getCurrentTimestamp();
        long start = couponDO.getStartTime().getTime();
        long end = couponDO.getEndTime().getTime();
        if(time<start || time>end){
            throw new BizException(BizCodeEnum.COUPON_OUT_OF_TIME);
        }

        //用户是否超额领取
        int recordNum =  couponRecordMapper.selectCount(new QueryWrapper<CouponRecordDO>()
                .eq("coupon_id",couponDO.getId())
                .eq("user_id",userId));
        if(recordNum >= couponDO.getUserLimit()){
            throw new BizException(BizCodeEnum.COUPON_OUT_OF_LIMIT);
        }

    }


    // 将DO对象转换为VO对象
    private CouponVO beanProcess(CouponDO couponDO) {
        CouponVO couponVO = new CouponVO();
        BeanUtils.copyProperties(couponDO,couponVO);
        return couponVO;
    }
}
