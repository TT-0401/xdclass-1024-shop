package net.xdclass.fegin;

import net.xdclass.request.NewUserCouponRequest;
import net.xdclass.util.JsonData;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "xdclass-coupon-service")
public interface CouponFeignService {

    /**
     * 新用户注册发放优惠券
     *      用户微服务：新用户注册 -> RPC -> 优惠券微服务：发放新人优惠券
     *      新用户注册 UserController(register接口) -> UserService -> UserServiceImpl 中的 register 方法包含 userRegisterInitTask 方法
     *      在该方法中调用 CouponFeignService 接口，即本接口
     *      本接口通过 RPC 调用
     *      发放新人优惠券 CouponController(addNewUserCoupon接口) -> CouponService -> CouponServiceImpl 中的 initNewUserCoupon 方法
     * @param newUserCouponRequest
     */
    @PostMapping("/api/coupon/v1/new_user_coupon")
    JsonData addNewUserCoupon(@RequestBody NewUserCouponRequest newUserCouponRequest);
}