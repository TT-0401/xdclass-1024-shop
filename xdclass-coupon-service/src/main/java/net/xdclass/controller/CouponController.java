package net.xdclass.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import net.xdclass.enums.CouponCategoryEnum;
import net.xdclass.interceptor.LoginInterceptor;
import net.xdclass.model.LoginUser;
import net.xdclass.request.NewUserCouponRequest;
import net.xdclass.service.CouponService;
import net.xdclass.util.JsonData;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Api("优惠券模块")
@Slf4j
@RestController
@RequestMapping("/api/coupon/v1")
public class CouponController {

    @Autowired
    private CouponService couponService;

    @Autowired
    private RedissonClient redissonClient;


    /**
     * 分页查询优惠券
     * @param page
     * @param size
     * @return
     */
    @ApiOperation("分页查询优惠券")
    @GetMapping("page_coupon")
    public JsonData pageCouponList(
            @ApiParam(value = "当前页")  @RequestParam(value = "page", defaultValue = "1") int page,
            @ApiParam(value = "每页显示多少条") @RequestParam(value = "size", defaultValue = "10") int size
    ) {

        Map<String,Object> pageMap = couponService.pageCouponActivity(page,size);

        return JsonData.buildSuccess(pageMap);
    }


    /**
     * 领取促销优惠券
     * @param couponId
     * @return
     */
    @ApiOperation("领取优惠券")
    @GetMapping("/add/promotion/{coupon_id}")
    public JsonData addPromotionCoupon(@ApiParam(value = "优惠券id",required = true) @PathVariable("coupon_id") long couponId){

        LoginUser loginUser = LoginInterceptor.threadLocal.get();

        // 【使用Redisson分布式锁，防止用户超领优惠券】
        // 为了防止多用户并发领券，使得优惠券超发，lockKey 加上当前用户ID，使锁粒度更细化
        String lockKey = "lock:coupon:" + couponId + ":" + loginUser.getId();
        RLock rLock = redissonClient.getLock(lockKey);

        // 多个线程进入，会阻塞等待释放锁，默认30秒，然后有 watch dog 自动续期
        rLock.lock();

        try {

            // 执行业务方法，并提交事务
            JsonData jsonData = couponService.addCoupon(couponId,CouponCategoryEnum.PROMOTION);
            return jsonData;

        } finally {
            // 等待事务提交成功后，才能进行锁的释放！！！
            rLock.unlock();
            log.info("领券接口解锁成功");
        }

    }


    /**
     * 新用户注册发放新人优惠券接口（被用户微服务RPC调用）
     *      用户微服务：新用户注册 -> RPC -> 优惠券微服务：发放新人优惠券
     */
    @ApiOperation("新用户注册接口 -> RPC -> 发放新人优惠券接口")
    @PostMapping("/new_user_coupon")
    public JsonData addNewUserCoupon(@ApiParam("新注册用户对象") @RequestBody NewUserCouponRequest newUserCouponRequest){

        JsonData jsonData = couponService.initNewUserCoupon(newUserCouponRequest);

        return jsonData;
    }
}

