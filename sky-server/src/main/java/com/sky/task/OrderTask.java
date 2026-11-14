package com.sky.task;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务类
 * 【改造②】"订单超时取消"已由 mq/OrderDelayListener（RabbitMQ 延迟消息）替代：
 * 原每分钟轮询全表的方案存在超时判定误差大、订单量大时全表扫描压力大、无超时订单时也在空转等问题。
 * 此处保留"配送中超时置完成"任务——它是对存量数据的兜底处理，不属于延迟语义。
 */
@Slf4j
@Component
public class OrderTask {

    @Autowired
    private OrderMapper orderMapper;

    /**
     * 处理一直处于配送中的订单
     */
    @Scheduled(cron = "0 0 1 * * ?") // 每天凌晨一点执行
    public void processDeliveryOrders() {
        log.info("定时处理配送订单：{}", LocalDateTime.now());

        LocalDateTime time = LocalDateTime.now().plusHours(-1);

        List<Orders> ordersList = orderMapper.getByStatusAndOrderTimeLT(Orders.DELIVERY_IN_PROGRESS, time);
        if (ordersList != null && ordersList.size() > 0) {
            for (Orders orders : ordersList) {
                orders.setStatus(Orders.COMPLETED);
                orderMapper.update(orders);
            }
        }
    }

}
