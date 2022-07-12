package net.xdclass.mq;

import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import net.xdclass.model.OrderMessage;
import net.xdclass.service.ProductOrderService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RabbitListener(queues = "${mqconfig.order_close_queue}")
public class ProductOrderMQListener {

    @Autowired
    private ProductOrderService productOrderService;


    /**
     * 监听定时关单消息
     *
     * * 消费消息，保证幂等性
     * * 并发情况下如何保证安全
     *
     * @param orderMessage 关单消息
     * @param message 消息
     * @param channel 信道
     */
    @RabbitHandler
    public void closeProductOrder(OrderMessage orderMessage, Message message, Channel channel) throws IOException {

        log.info("监听到关单消息，msg:{}",orderMessage);

        // 消息ID
        long msgTag = message.getMessageProperties().getDeliveryTag();

        try{
            // 【查询是否关单成功】
            boolean flag = productOrderService.closeProductOrder(orderMessage);

            // 关单成功
            if (flag){
                channel.basicAck(msgTag,false);
            }else {
                channel.basicReject(msgTag,true);
            }

        }catch (IOException e){
            log.error("定时关单失败，异常：{}，msg：{}", e, orderMessage);
            channel.basicReject(msgTag,true);
        }

    }

}
