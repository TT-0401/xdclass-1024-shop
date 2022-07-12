package net.xdclass.controller;


import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import net.xdclass.enums.BizCodeEnum;
import net.xdclass.request.LockCouponRecordRequest;
import net.xdclass.service.CouponRecordService;
import net.xdclass.util.JsonData;
import net.xdclass.vo.CouponRecordVO;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

@Api("优惠券记录模块")
@RestController
@RequestMapping("/api/coupon_record/v1")
public class CouponRecordController {

    @Resource
    private CouponRecordService couponRecordService;


    @ApiOperation("分页查询个人领券记录")
    @GetMapping("page")
    public JsonData page(@ApiParam(value = "当前页")  @RequestParam(value = "page", defaultValue = "1") int page,
                         @ApiParam(value = "每页显示多少条") @RequestParam(value = "size", defaultValue = "10") int size)
    {
        Map<String,Object> pageResult = couponRecordService.page(page,size);

        return JsonData.buildSuccess(pageResult);
    }


    @ApiOperation("查询领券记录详情")
    @GetMapping("detail/{record_id}")
    public JsonData getCouponRecordDetail(@ApiParam(value = "记录id")  @PathVariable("record_id") long recordId){

        CouponRecordVO couponRecordVO = couponRecordService.findById(recordId);

        return couponRecordVO == null ? JsonData.buildResult(BizCodeEnum.COUPON_NO_EXITS):JsonData.buildSuccess(couponRecordVO);
    }


    /**
     * 创建订单，暂时锁定优惠券记录（被订单微服务RPC调用）
     *      订单微服务：创建订单 -> RPC -> 优惠券微服务：锁定优惠券记录
     */
    @ApiOperation("创建订单 -> RPC -> 锁定优惠券记录")
    @PostMapping("lock_records")
    public JsonData lockCouponRecords(@ApiParam("锁定优惠券请求对象") @RequestBody LockCouponRecordRequest recordRequest){

        // 锁定优惠券请求包含：被锁定优惠券的ID列表、与之关联的订单号
        JsonData jsonData = couponRecordService.lockCouponRecords(recordRequest);

        return jsonData;
    }

}

