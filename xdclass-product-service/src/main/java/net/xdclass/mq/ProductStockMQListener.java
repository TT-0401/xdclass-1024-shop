package net.xdclass.mq;

import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import net.xdclass.model.CouponRecordMessage;
import net.xdclass.model.ProductMessage;
import net.xdclass.service.ProductService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;


@Slf4j
@Component
@RabbitListener(queues = "${mqconfig.stock_release_queue}")
public class ProductStockMQListener {

    @Autowired
    private ProductService productService;


    /**
     * 消费失败，并且重新入队后超过最大重试次数：
     *      如果消费失败，不重新入队，可以记录日志，然后插入数据库，以供人工排查
     *
     * @param productMessage 监听到的消息内容，供调用业务方法时传参使用
     * @param message 序列化后的消息
     * @param channel 信道
     * @throws IOException
     */
    @RabbitHandler
    public void releaseProductStock(ProductMessage productMessage, Message message, Channel channel) throws IOException {

        log.info("监听到消息，msg:{}", productMessage);

        // 获取该消息的ID
        long msgTag = message.getMessageProperties().getDeliveryTag();

        // 调用释放商品库存的业务方法，并根据返回的布尔值判断商品库存是否成功释放，即消息是否消费成功
        boolean flag = productService.releaseProductStock(productMessage);

        try {
            if (flag) {
                // 确认消息消费成功
                channel.basicAck(msgTag, false);
            }else {
                log.error("释放商品库存失败，msg:{}",productMessage);
                channel.basicReject(msgTag,true);
            }

        } catch (IOException e) {
            log.error("释放商品库存异常:{}，msg:{}",e,productMessage);
            channel.basicReject(msgTag,true);
        }

    }

}
