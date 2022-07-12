package net.xdclass.enums;

/**
 * 库存状态：优惠券库存 或 商品库存
* */
public enum StockTaskStateEnum {


    /**
     * 锁定
     */
    LOCK,

    /**
     * 完成
     */
    FINISH,

    /**
     * 取消，释放库存
     */
    CANCEL;

}
