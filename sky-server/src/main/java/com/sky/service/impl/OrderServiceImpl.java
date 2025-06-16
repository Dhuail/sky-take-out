package com.sky.service.impl;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.xiaoymin.knife4j.core.util.CollectionUtils;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersConfirmDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersRejectionDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.AddressBook;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.entity.ShoppingCart;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.AddressBookMapper;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
    private ShoppingCartMapper  shoppingCartMapper;

    /**
     * 用户下单
     * @param ordersSubmitDTO
     * @return
     */
    @Override
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {

        //处理各种业务异常（地址簿为空、购物车数据为空）
        AddressBook byId = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if(byId == null){
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }
        Long currentId = BaseContext.getCurrentId();
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(currentId);
        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);
        if(list == null || list.size() == 0){
            throw new AddressBookBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        //向订单表插入1条数据
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setNumber(String.valueOf(System.currentTimeMillis()));
        orders.setPhone(byId.getPhone());
        orders.setConsignee(byId.getConsignee());
        orders.setUserId(currentId);

        orderMapper.insert(orders);

        List<OrderDetail> orderDetails = new ArrayList<>();
        //向订单明细表插入n条数据
        for (ShoppingCart cart : list) {
            OrderDetail  orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setOrderId(orders.getId());//设置订单明细关联的订单id
            orderDetails.add(orderDetail);
        }
        orderDetailMapper.insertBatch(orderDetails);

        //清空当前用户购物车
        shoppingCartMapper.deleteByUserId(currentId);

        //返回订单数据
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(orders.getId())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .orderTime(orders.getOrderTime())
                .build();

        return orderSubmitVO;
    }

    /**
     * 订单分页查询
     * @param
     * @param pageSize
     * @param status
     * @return
     */
    @Override
    public PageResult pageQuery4User(int pageNum, int pageSize, Integer status) {
        PageHelper.startPage(pageNum, pageSize);
        OrdersPageQueryDTO  ordersPageQueryDTO = new OrdersPageQueryDTO();
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        ordersPageQueryDTO.setStatus(status);
        //分页条件查询
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);
        List<OrderVO> list = new ArrayList<>();
        if(page!=null && page.size()>0){
            for (Orders orders : page) {
                Long orderId = orders.getId();
                //查询订单详情
                List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orderId);
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                orderVO.setOrderDetailList(orderDetailList);
                list.add(orderVO);
            }
        }

        return new PageResult(page.getTotal(), list);
    }

    /**
     * 订单详情
     * @param id
     * @return
     */

    @Override
    public OrderVO details(Long id) {
        //根据订单号查询对应的菜品
        Orders orders = orderMapper.getById(id);

        //查询该订单对应的菜品/套餐明细
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());
        //将该订单及其详情封装到VO中并返回
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);
        orderVO.setOrderDetailList(orderDetailList);

        return orderVO;
    }

    /**
     * 用户取消订单
     * @param id
     */
    @Override
    public void cancel(Long id) {
        //根据id查询顶订单
        Orders ordersDB = orderMapper.getById(id);
        if(ordersDB == null){
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        //订单状态 1.待付款 2.待接单 3.待派送 4.派送中 5.已完成 6.已取消
        if(ordersDB.getStatus() > 2){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders orders = new Orders();
        orders.setId(ordersDB.getId());
        if(ordersDB.getStatus().equals(Orders.TO_BE_CONFIRMED)){
            //支付状态修改为 退款
            orders.setPayStatus(Orders.REFUND);

        }
        // 更新订单状态、取消原因、取消时间
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason("用户取消");
        orders.setCancelTime(LocalDateTime.now());
        orderMapper.update(orders);
    }

    @Override
    public void repetition(Long id) {
        // 查询当前用户信息
        Long currentId = BaseContext.getCurrentId();
        //根据订单id查询当前订单数数据
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);
        //将订单详情转为购物车对象

        List<ShoppingCart> shoppingCartList = orderDetailList.stream()  // 1. 将订单明细列表转为流（Stream）
                .map(x -> {  // 2. 对每个订单明细（OrderDetail）进行转换
                    ShoppingCart shoppingCart = new ShoppingCart();  // 3. 创建新的购物车对象

                    // 4. 复制 OrderDetail 的属性到 ShoppingCart（排除 "id" 字段）
                    BeanUtils.copyProperties(x, shoppingCart, "id");

                    // 5. 设置当前用户ID和创建时间
                    shoppingCart.setUserId(currentId);
                    shoppingCart.setCreateTime(LocalDateTime.now());

                    return shoppingCart;  // 6. 返回转换后的购物车对象
                })
                .collect(Collectors.toList());  // 7. 将流收集为 List<ShoppingCart>

        //将购物车对象批量添加到数据库中
        shoppingCartMapper.insertBatch(shoppingCartList);

    }
    /**
     * 条件搜索订单
     * @param ordersPageQueryDTO
     * @return
     */
    @Override
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);
        // 部分订单状态，需要额外返回订单菜品信息，将Orders转化为OrderVO
        List<OrderVO> orderVOList = getOrderVOList(page);

        return new PageResult(page.getTotal(), orderVOList);
    }
    /**
     * 订单完成
     * @param id
     */
    @Override
    public void complete(Long id) {
        Orders orderDB = orderMapper.getById(id);
        // 判断订单是否存在
        if(orderDB == null || !orderDB.getStatus().equals(Orders.DELIVERY_IN_PROGRESS))
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        Orders orders = new Orders();
        orders.setId(orderDB.getId());
        orders.setStatus(Orders.COMPLETED);
        orders.setDeliveryTime(LocalDateTime.now());
        orderMapper.update(orders);
    }

    /**
     * 统计订单数据
     * @return
     */
    @Override
    public OrderStatisticsVO statistics() {
        //根据状态分别查询出待接单，带派送，派送中的订单数量
        Integer toBeConfirmed = orderMapper.countStatus(Orders.TO_BE_CONFIRMED);
        Integer confirmed = orderMapper.countStatus(Orders.CONFIRMED);
        Integer deliveryInProgress = orderMapper.countStatus(Orders.DELIVERY_IN_PROGRESS);
        //将查询出的数据封装到OrderStatisticsVO中
        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        orderStatisticsVO.setToBeConfirmed(toBeConfirmed);
        orderStatisticsVO.setConfirmed(confirmed);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress);

        return orderStatisticsVO;
    }

    private List<OrderVO> getOrderVOList(Page<Orders> page) {
        // 需要返回订单菜品信息，自定义OrderVO响应结果
        List<OrderVO> orderVOList = new ArrayList<>();

        List<Orders> ordersList = page.getResult();
        if (!CollectionUtils.isEmpty(ordersList)) {
            for (Orders orders : ordersList) {
                // 将共同字段复制到OrderVO
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                String orderDishes = getOrderDishesStr(orders);

                // 将订单菜品信息封装到orderVO中，并添加到orderVOList
                orderVO.setOrderDishes(orderDishes);
                orderVOList.add(orderVO);
            }
        }
        return orderVOList;
    }

    private String getOrderDishesStr(Orders orders) {
        // 查询订单菜品详情信息（订单中的菜品和数量）
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());

        // 将每一条订单菜品信息拼接为字符串（格式：宫保鸡丁*3；）
        List<String> orderDishList = orderDetailList.stream().map(x -> {
            String orderDish = x.getName() + "*" + x.getNumber() + ";";
            return orderDish;
        }).collect(Collectors.toList());

        // 将该订单对应的所有菜品信息拼接在一起
        return String.join("", orderDishList);
    }

    /**
     * 接单
     * @param ordersConfirmDTO
     */
    @Override
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        Orders orders = Orders.builder()
                .id(ordersConfirmDTO.getId())
                .status(Orders.CONFIRMED)
                .build();
        orderMapper.update(orders);
    }

    /**
     * 拒单
     * @param ordersRejectionDTO
     */
    @Override
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) {
        //商家拒单其实就是将订单状态修改为“已取消”,只有订单处于“待接单”状态时可以执行拒单操作
        Orders orders = orderMapper.getById(ordersRejectionDTO.getId());
        if(orders== null || !orders.getStatus().equals(Orders.TO_BE_CONFIRMED))
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        Orders orderVO = new Orders();
        orderVO.setId(ordersRejectionDTO.getId());
        orderVO.setStatus(Orders.CANCELLED);
        orderVO.setRejectionReason(ordersRejectionDTO.getRejectionReason());
        orderMapper.update(orderVO);
        //商家拒单时需要指定拒单原因

        //商家拒单时，如果用户已经完成了支付，需要为用户退款
    }

    /**
     * 派送订单
     * @param id
     */
    @Override
    public void delivery(Long id) {
        //根据id查询订单
        Orders orders = orderMapper.getById(id);
        //判断订单是否存在
        if(orders == null || !orders.getStatus().equals(Orders.CONFIRMED)){
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        Orders orderVO = new Orders();
        orderVO.setId(id);
        orderVO.setStatus(Orders.DELIVERY_IN_PROGRESS);
        orderMapper.update(orderVO);
        //更新订单状态，状态转为派送中
    }

}
