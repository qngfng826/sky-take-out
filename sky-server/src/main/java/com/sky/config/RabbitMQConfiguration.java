package com.sky.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 延迟消息配置 —— 亮点改造②：订单超时取消由定时任务轮询改为延迟消息
 *
 * <p>采用「延迟插件」（rabbitmq_delayed_message_exchange）方案：
 * 交换机类型为 x-delayed-message，发送消息时通过 x-delay 头指定延迟时长（毫秒），
 * 消息在交换机内暂存，到期后才路由到队列，消费者拿到手的都是"已经到点"的消息。
 *
 * <p>对比方案（面试考点）：TTL + 死信队列（DLX）无需装插件，但队列内消息按先进先出依次过期，
 * 队头一条 15 分钟的消息会阻塞其后 1 分钟到期的消息（队头阻塞问题）；延迟插件没有这个问题。
 *
 * <p>本地启动：
 * <pre>
 * docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3.13-management
 * docker exec rabbitmq rabbitmq-plugins enable rabbitmq_delayed_message_exchange
 * docker restart rabbitmq
 * </pre>
 */
@Configuration
public class RabbitMQConfiguration {

    /** 订单延迟取消交换机（x-delayed-message 类型） */
    public static final String ORDER_DELAY_EXCHANGE = "sky.order.delay.exchange";

    /** 订单延迟取消队列 */
    public static final String ORDER_DELAY_QUEUE = "sky.order.delay.queue";

    /** 路由 key */
    public static final String ORDER_DELAY_ROUTING_KEY = "sky.order.delay.cancel";

    @Bean
    public CustomExchange orderDelayExchange() {
        Map<String, Object> args = new HashMap<>();
        // x-delayed-type：延迟交换机到期后按 direct 语义路由
        args.put("x-delayed-type", "direct");
        return new CustomExchange(ORDER_DELAY_EXCHANGE, "x-delayed-message", true, false, args);
    }

    @Bean
    public Queue orderDelayQueue() {
        // 持久化队列，防止 RabbitMQ 重启丢消息
        return new Queue(ORDER_DELAY_QUEUE, true);
    }

    @Bean
    public Binding orderDelayBinding() {
        // 直接用构造器声明绑定，语义清晰：queue <- exchange(routingKey)
        return new Binding(ORDER_DELAY_QUEUE, Binding.DestinationType.QUEUE,
                ORDER_DELAY_EXCHANGE, ORDER_DELAY_ROUTING_KEY, null);
    }
}
