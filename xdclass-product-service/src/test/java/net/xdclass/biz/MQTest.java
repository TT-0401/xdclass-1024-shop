package net.xdclass.biz;

import lombok.extern.slf4j.Slf4j;
import net.xdclass.ProductApplication;
import net.xdclass.model.ProductMessage;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ProductApplication.class)
@Slf4j
public class MQTest {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    public void testSendDelayMsg(){

        rabbitTemplate.convertAndSend("stock.event.exchange","stock.release.delay.routing.key","this is product stock lock msg");

    }


    // 模拟商品库存锁定任务消息，发送至延迟队列
    @Test
    public void testSendProductStockMessage(){

        ProductMessage productMessage = new ProductMessage();
        productMessage.setTaskId(1L);
        productMessage.setOutTradeNo("123456abc");

        rabbitTemplate.convertAndSend("stock.event.exchange","stock.release.delay.routing.key",productMessage);

    }




}



