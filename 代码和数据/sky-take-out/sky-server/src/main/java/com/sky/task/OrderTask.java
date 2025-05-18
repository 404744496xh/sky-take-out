package com.sky.task;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@Component
@Slf4j
public class OrderTask {
    @Autowired
    private OrderMapper  orderMapper;

    /**
     * 定时处理超时的未支付订单
     */
    @Scheduled(cron = "0 * * * * ? ")
    public void precessTimeOutOrder(){
        log.info("处理超时支付超时订单：{}",new Date());


        LocalDateTime time = LocalDateTime.now().plusMinutes(-15);

        // 查询超时订单
        // select * from orders where status = 1 and order_time < 当前时间-15分钟
        List<Orders> list = orderMapper.getByStatusAndOrderTime(Orders.PENDING_PAYMENT, time);

        //取消超时订单
        if(list != null && list.size() > 0){
            list.forEach(orders -> {
                orders.setStatus(Orders.CANCELLED);
                orders.setCancelReason("支付超时，取消订单");
                orders.setCancelTime(LocalDateTime.now());
                orderMapper.update(orders);
            });
        }
    }

    @Scheduled(cron = "0 0 1 * * ? ")
    public void processDeliveryOrder(){
        log.info("处理前一个工作日，派送中的订单：{}",new Date());

        LocalDateTime time = LocalDateTime.now().plusMinutes(-60);

        // 查询待派送订单
        List<Orders> list = orderMapper.getByStatusAndOrderTime(Orders.DELIVERY_IN_PROGRESS, time);
        if(list != null && list.size() > 0){
            list.forEach(orders -> {
                orders.setStatus(Orders.COMPLETED);
                orderMapper.update(orders);
            });
        }
    }


}
