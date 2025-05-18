package com.sky.controller.user;

import com.sky.dto.OrdersCancelDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.OrderService;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController("userOrderController")
@RequestMapping("/user/order")
@Slf4j
@Api(tags = "用户订单接口")
public class OrderController {

    @Autowired
    private OrderService orderService;

    /**
     * 用户下单
     * @return
     */
    @RequestMapping("/submit")
    @ApiOperation("用户下单")
    public Result<OrderSubmitVO> submit(@RequestBody OrdersSubmitDTO ordersSubmitDTO){
        log.info("用户下单：{}", ordersSubmitDTO);
        OrderSubmitVO orderSubmitVO = orderService.submitOrder(ordersSubmitDTO);
        return Result.success(orderSubmitVO);
    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    @PutMapping("/payment")
    @ApiOperation("订单支付")
    public Result<OrderPaymentVO> payment(@RequestBody OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        log.info("订单支付：{}", ordersPaymentDTO);
        OrderPaymentVO orderPaymentVO = orderService.payment(ordersPaymentDTO);
        log.info("生成预支付交易单：{}", orderPaymentVO);
        return Result.success(orderPaymentVO);
    }

    /**
     * 历史订单查询
     * @param page
     * @param pageSize
     * @param status
     * @return
     */
    @ApiOperation("历史订单查询")
    @GetMapping("/historyOrders")
    public Result<PageResult> historyOrders(Integer page, Integer pageSize, Integer status){
        log.info("历史订单查询:{},{},{}", page, pageSize, status);
        PageResult pageResults = orderService.historyOrders(page, pageSize, status);
        return Result.success(pageResults);
    }

    /**
     * 订单详情
     * @param id
     * @return
     */
    @ApiOperation("订单详情")
    @GetMapping("/orderDetail/{id}")
    public Result<OrderVO> details(@PathVariable Long id){
        log.info("订单详情:{}", id);
        OrderVO orderVO = orderService.orderDetails(id);
        return Result.success(orderVO);
    }

    /**
     * 取消订单
     * @param id
     * @return
     */
    @ApiOperation("取消订单")
    @PutMapping("cancel/{id}")
    public Result cancelOrder(@PathVariable Long id){
        log.info("取消订单:{}", id);

        OrdersCancelDTO ordersCancelDTO = new OrdersCancelDTO();
        ordersCancelDTO.setId(id);
        ordersCancelDTO.setCancelReason("用户取消");


        orderService.cancelOrder(ordersCancelDTO);
        return Result.success();
    }

    /**
     * 再来一单
     * @param id
     * @return
     */
    @ApiOperation("再来一单")
    @PostMapping("/repetition/{id}")
    public Result repetition(@PathVariable Long id){
        log.info("再来一单:{}", id);
        orderService.repetition(id);
        return Result.success();
    }

    /**
     * 用户催单
     * @param id
     * @return
     */
    @ApiOperation("用户催单")
    @GetMapping("reminder/{id}")
    public Result reminder(@PathVariable Long id){
        log.info("用户催单：{}",id);
        orderService.reminder(id);
        return Result.success();
    }
}
