package net.xdclass.mapper;

import net.xdclass.model.CouponDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.springframework.data.repository.query.Param;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author TT
 * @since 2022-05-26
 */
public interface CouponMapper extends BaseMapper<CouponDO> {

    int reduceStock(@Param("couponID") long couponId);
}
