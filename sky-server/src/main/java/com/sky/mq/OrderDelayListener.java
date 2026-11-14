package com.sky.mq;

import com.sky.config.RabbitMQConfiguration;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 订单超时取消监听器 —— 亮点改造②：RabbitMQ 延迟消息
 *
 * <p>替代原 OrderTask#processTimeoutOrders 的方案：
 * 原方案每分钟全表轮询一次（status = 待支付 AND order_time < 15分钟前），
 * 存在三个问题：① 超时判定误差可达 1 分钟；② 订单量大时每分钟全表扫描，压力集中在定时任务；
 * ③ 无论有没有超时订单，轮询本身永远在空转消耗资源。
 *
 * <p>新方案：下单时投递一条 15 分钟后才到达的延迟消息，到点后精准检查这一个订单，无消息则零开销。
 */
@Component
@Slf4j
public class OrderDelayListener {

    @Autowired
    private OrderMapper orderMapper;

    @RabbitListener(queues = RabbitMQConfiguration.ORDER_DELAY_QUEUE)
    public void processTimeoutOrder(Long orderId) {
        log.info("收到订单超时延迟消息，orderId={}", orderId);

        Orders ordersDB = orderMapper.getById(orderId);

        // 幂等防护（关键）：消息在路上走的 15 分钟里，订单可能已经被支付、取消或管理端处理过。
        // 只有查库确认仍处于"待支付"才执行取消，避免把用户刚付完的订单取消掉。
        if (ordersDB == null || !Orders.PENDING_PAYMENT.equals(ordersDB.getStatus())) {
            log.info("订单{}不存在或状态已变更，跳过超时取消", orderId);
            return;
        }

        Orders orders = Orders.builder()
                .id(orderId)
                .status(Orders.CANCELLED)
                .cancelTime(LocalDateTime.now())
                .cancelReason("订单超时未支付，系统自动取消")
                .build();
        orderMapper.update(orders);
        log.info("订单{}超时未支付，已自动取消", orderId);
    }
}
