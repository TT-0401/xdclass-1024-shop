package net.xdclass.mq;

import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import net.xdclass.model.CouponRecordMessage;
import net.xdclass.service.CouponRecordService;
import org.checkerframework.checker.units.qual.A;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.locks.Lock;


/**
 * 死信队列的消息监听器
 * */
@Slf4j
@Component
@RabbitListener(queues = "${mqconfig.coupon_release_queue}")
public class CouponMQListener {

    @Autowired
    private CouponRecordService couponRecordService;

    @Autowired
    private RedissonClient redissonClient;


    /**
     * 消费失败，并且重新入队后超过最大重试次数：
     *      如果消费失败，不重新入队，可以记录日志，然后插入数据库，以供人工排查
     *
     * @param recordMessage 监听到的消息内容，供调用业务方法时传参使用
     * @param message 序列化后的消息
     * @param channel 信道：多路复用连接中的一条独立的双向数据流通道，信道是建立在真实的TCP连接内的虚拟通道，AMQP命令都是通过信道发出去的
     *                不管是发布消息、订阅队列，还是接收消息，都是通过信道完成
     *                因为对于操作系统来说创建和销毁一个TCP连接都是很昂贵的开销，所以使用信道以实现复用一条TCP连接
     */
    @RabbitHandler
    public void releaseCouponRecord(CouponRecordMessage recordMessage, Message message, Channel channel) throws IOException {

        // 防止同个解锁任务并发进入；如果是串行消费不用加锁；加锁有利也有弊，看项目业务逻辑而定
        // Lock lock = redissonClient.getLock("lock:coupon_record_release:" + recordMessage.getTaskId());
        // lock.lock();

        log.info("监听到消息，msg:{}", recordMessage);

        // 获取该消息的ID
        long msgTag = message.getMessageProperties().getDeliveryTag();

        // 调用释放优惠券的业务方法，并根据返回的布尔值判断优惠券是否成功释放，即消息是否消费成功
        boolean flag = couponRecordService.releaseCouponRecord(recordMessage);

        try {
            // 确认消息消费成功
            if (flag) {

                // 对该消息发送确认信号 ack
                // 为了减少网络流量，消息的手动确认可以被批处理，当该参数为 true 时，则可以一次性确认 deliveryTag 小于等于传入值的所有消息
                channel.basicAck(msgTag, false);

            }else {
                log.error("释放优惠券失败，msg:{}", recordMessage);
                // 发送拒绝信号 reject
                channel.basicReject(msgTag,true);
            }
        } catch (IOException e) {
            log.error("释放优惠券异常:{}，msg:{}",e,recordMessage);
            // 发送拒绝信号 reject
            channel.basicReject(msgTag,true);
        }
//        finally {
//            lock.unlock();
//        }

    }


//    @RabbitHandler
//    public void releaseCouponRecord2(String msg, Message message, Channel channel) throws IOException {
//
//        log.info(msg);
//        channel.basicAck(message.getMessageProperties().getDeliveryTag(),true);
//    }

}
