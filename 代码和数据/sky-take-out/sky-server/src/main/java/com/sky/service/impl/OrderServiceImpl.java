package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.*;
import com.sky.properties.WeChatProperties;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.HttpClientUtil;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import org.aspectj.weaver.ast.Or;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private AddressBookMapper addressBookMapper;

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private WeChatPayUtil weChatPayUtil;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private WebSocketServer webSocketServer;

    @Value("${sky.shop.address}")
    private String shopAddress;

    @Value("${sky.amap.key}")
    private String key;

    /**
     * 检查客户的收货地址是否超出配送范围
     * @param address
     */
    private void checkAddressBook(String address){
        Map<String ,String> map = new HashMap<>();
        map.put("address",shopAddress);
        map.put("key",key);
        map.put("output","json");

        //获取店铺的经纬度
        String json = HttpClientUtil.doGet("https://restapi.amap.com/v3/geocode/geo", map);

        JSONObject jsonObject = JSONObject.parseObject(json);
        if(jsonObject.getString("status").equals("0")){
            throw new AddressBookBusinessException("店铺地址解析失败");
        }

        //数据解析
        String location = jsonObject.getJSONArray("geocodes").getJSONObject(0).getString("location");

        //获取用户地址的经纬度
        map.put("address",address);

        String userJson = HttpClientUtil.doGet("https://restapi.amap.com/v3/geocode/geo", map);

        JSONObject userJsonObject = JSONObject.parseObject(userJson);
        if(userJsonObject.getString("status").equals("0")){
            throw new AddressBookBusinessException("用户地址解析失败");
        }

        //数据解析
        String userLocation = userJsonObject.getJSONArray("geocodes").getJSONObject(0).getString("location");


        //计算距离
        map = new HashMap<>();
        map.put("origin",location);
        map.put("destination",userLocation);
        map.put("key",key);

        String distance = HttpClientUtil.doGet("https://restapi.amap.com/v4/direction/bicycling", map);
        JSONObject disJsonObject = JSONObject.parseObject(distance);

        if(!disJsonObject.getString("errcode").equals("0")){
            throw new AddressBookBusinessException(disJsonObject.getString("errdetail"));
        }

        Double dis = disJsonObject.getJSONObject("data").getJSONArray("paths").getJSONObject(0).getDouble("distance");

        //配送距离超过5公里
        if(dis > 5000){
            throw new AddressBookBusinessException("超出配送范围");
        }

    }

    /**
     * 用户下单
     * @param ordersSubmitDTO
     */
    @Transactional
    @Override
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        //处理各种业务异常(地址簿为空，购物车为空)
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if(addressBook == null){
            //抛出业务异常
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        //判断店铺和用户的位置距离是否过远
        checkAddressBook(addressBook.getProvinceName()+addressBook.getCityName()+addressBook.getDistrictName()+addressBook.getDetail());

        // 查询购物车数据
        Long userId = BaseContext.getCurrentId();

        ShoppingCart shoppingCart = ShoppingCart.builder()
                .userId(userId)
                .build();
        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);
        if(list == null || list.size() == 0){
            //抛出业务异常
            throw new AddressBookBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        //向订单表插入1条数据
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO,orders);

        orders.setUserId(userId);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setNumber(String.valueOf(System.currentTimeMillis()+""+userId));
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());

        orderMapper.insert(orders);

        //向订单明细表插入n条数据
        List<OrderDetail> orderDetails = new ArrayList<>();

        for (ShoppingCart cart : list){
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart,orderDetail);
            orderDetail.setOrderId(orders.getId());

            orderDetails.add(orderDetail);
        }

        orderDetailMapper.insertBatch(orderDetails);

        //清空用户的购物车数据
        shoppingCartMapper.deleteByUserId(userId);

        //封装VO返回结果
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(orders.getId())
                .orderTime(orders.getOrderTime())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .build();

        return orderSubmitVO;
    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
//        paySuccess(ordersPaymentDTO.getOrderNumber());
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);

        //调用微信支付接口，生成预支付交易单
        JSONObject jsonObject = weChatPayUtil.pay(
                ordersPaymentDTO.getOrderNumber(), //商户订单号
                new BigDecimal(0.01), //支付金额，单位 元
                "苍穹外卖订单", //商品描述
                user.getOpenid() //微信用户的openid
        );

        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }

        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        return vo;
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();
        ////////////////////////////////////////
        Map map = new HashMap();
        map.put("type",1);
        map.put("orderId",ordersDB.getId());
        map.put("content","订单号:"+ outTradeNo);

        webSocketServer.sendToAllClient(JSON.toJSONString(map));
        ////////////////////////////////////////
        orderMapper.update(orders);
    }

    /**
     * 历史订单查询
     * @param page
     * @param pageSize
     * @param status
     * @return
     */
    @Override
    public PageResult historyOrders(Integer page, Integer pageSize, Integer status) {
        //  分页查询
        PageHelper.startPage(page, pageSize);

        //获取当前登录用户id
        Long userId = BaseContext.getCurrentId();

        OrdersPageQueryDTO ordersPageQueryDTO = new OrdersPageQueryDTO();
        ordersPageQueryDTO.setUserId(userId);
        ordersPageQueryDTO.setStatus(status);
        ordersPageQueryDTO.setPage(page);
        ordersPageQueryDTO.setPageSize(pageSize);

        // 条件查询
        Page<Orders> pageInfo = orderMapper.pageQuery(ordersPageQueryDTO);

        List<OrderVO> orderVOList = new ArrayList<>();
        if(pageInfo.getTotal()!=0 && pageInfo.getResult()!=null)
            for (Orders orders : pageInfo) {
                Long orderId = orders.getId();

                // 根据订单id查询订单明细
                List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orderId);
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                orderVO.setOrderDetailList(orderDetailList);

                orderVOList.add(orderVO);
            }

        return new PageResult(pageInfo.getTotal(), orderVOList);
    }

    /**
     * 订单详情
     * @param id
     * @return
     */
    @Override
    public OrderVO orderDetails(Long id) {
        // 根据订单id查询订单
        Orders order = orderMapper.getById(id);

        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(order, orderVO);
        // 根据订单id查询订单明细
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);
        orderVO.setOrderDetailList(orderDetailList);


        return orderVO;
    }

    /**
     * 取消订单
     * @param ordersCancelDTO
     */
    @Override
    public void cancelOrder(OrdersCancelDTO ordersCancelDTO) {
        //根据id查询订单
        Orders order = orderMapper.getById(ordersCancelDTO.getId());

        //判断订单是否为空
        if(order == null){
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        //判断订单状态 订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
        if(order.getStatus()>2){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        //更新订单状态，取消原因，取消时间
        order.setStatus(Orders.CANCELLED);
        order.setCancelReason(ordersCancelDTO.getCancelReason());
        order.setCancelTime(LocalDateTime.now());

        //更新订单状态
        orderMapper.update(order);
    }

    /**
     * 再来一单
     * @param id
     */

    @Transactional
    @Override
    public void repetition(Long id) {
        //获取当前用户id
        Long userId = BaseContext.getCurrentId();

        //查询订单明细
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);

        //将订单详情对象转换为购物车对象
        List<ShoppingCart> shoppingCartList = orderDetailList.stream().map(x -> {
            ShoppingCart shoppingCart = new ShoppingCart();

            // 将原订单详情里面的菜品信息重新复制到购物车对象中
            BeanUtils.copyProperties(x, shoppingCart, "id");
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());

            return shoppingCart;
        }).collect(Collectors.toList());

        //把购物车对象批量添加到数据库
        shoppingCartMapper.insertBatch(shoppingCartList);

    }

    /**
     * 条件查询订单
     * @param ordersPageQueryDTO
     * @return
     */
    @Override
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(),ordersPageQueryDTO.getPageSize());
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        List<Orders> orderList = page.getResult();

        return new PageResult(page.getTotal(),orderList);
    }

    /**
     * 订单统计
     * @return
     */
    @Override
    public OrderStatisticsVO statistics() {
        //根据状态查询出待接单数量，带派送数量，派送中数量，派送中数量
        Integer toBeConfirmed = orderMapper.countStatus(Orders.TO_BE_CONFIRMED);
        Integer confirmed = orderMapper.countStatus(Orders.CONFIRMED);
        Integer deliveryInProgress = orderMapper.countStatus(Orders.DELIVERY_IN_PROGRESS);

        //组装数据
        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        orderStatisticsVO.setToBeConfirmed(toBeConfirmed);
        orderStatisticsVO.setConfirmed(confirmed);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress);
        return orderStatisticsVO;
    }

    /**
     * 接单
     * @param ordersConfirmDTO
     */
    @Override
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        //订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersConfirmDTO,orders);
        orders.setStatus(Orders.CONFIRMED);
        orderMapper.update(orders);
    }

    /**
     * 拒单
     * @param ordersRejectionDTO
     */
    @Override
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) {
        //订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersRejectionDTO,orders);
        orders.setStatus(Orders.CANCELLED);
        orderMapper.update(orders);
    }

    /**
     * 派送
     * @param id
     */
    @Override
    public void delivery(Long id) {
        Orders orders  = new Orders();
        orders.setId(id);
        //订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
        orders.setStatus(Orders.DELIVERY_IN_PROGRESS);
        orderMapper.update(orders);
    }

    /**
     * 完成
     * @param id
     */
    @Override
    public void complete(Long id) {
        Orders orders  = new Orders();
        orders.setId(id);
        orders.setStatus(Orders.COMPLETED);
        orderMapper.update(orders);
    }

    /**
     * 催单
     * @param id
     */
    @Override
    public void reminder(Long id) {
        //查询订单是否存在
        Orders orders = orderMapper.getById(id);
        if(orders == null){
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        //基于Websocket 实现催单功能
        Map map = new HashMap();
        map.put("type", 2);
        map.put("orderId", id);
        map.put("content", "订单号:"+orders.getNumber());

        webSocketServer.sendToAllClient(JSON.toJSONString(map));

    }
}
