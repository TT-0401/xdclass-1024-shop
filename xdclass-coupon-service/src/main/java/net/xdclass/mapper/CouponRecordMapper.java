package net.xdclass.mapper;

import net.xdclass.model.CouponRecordDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CouponRecordMapper extends BaseMapper<CouponRecordDO> {
    /**
     * 批量更新优惠券使用状态
     */
    int lockUseStateBatch(@Param("userId") Long userId, @Param("useState") String useState, @Param("lockCouponRecordIds") List<Long> lockCouponRecordIds);

    /**
     * 更新优惠券使用状态
     */
    void updateState(@Param("couponRecordId") Long couponRecordId, @Param("useState") String useState);
}
