package net.xdclass.model;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import java.util.Date;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;


// 优惠券锁定任务工作单
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("coupon_task")
public class CouponTaskDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 优惠券记录ID
     */
    private Long couponRecordId;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 关联的订单号
     */
    private String outTradeNo;

    /**
     * 锁定状态：锁定 LOCK 、完成 FINISH 、取消 CANCEL
     */
    private String lockState;

}
