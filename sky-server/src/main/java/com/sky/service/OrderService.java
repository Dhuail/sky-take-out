package com.sky.service;

import com.sky.dto.OrdersConfirmDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersRejectionDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.result.PageResult;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import org.springframework.stereotype.Service;

@Service
public interface OrderService {

    /**
     * 用户下单
     * @param ordersSubmitDTO
     * @return
     */
    OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO);

    /**
     * 分页查询订单
     * @param page
     * @param pageSize
     * @param status
     * @return
     */
    PageResult pageQuery4User(int page, int pageSize, Integer status);

    /**
     * 订单详情
     * @param id
     * @return
     */
    OrderVO details(Long id);

    /**
     * 取消订单
     * @param id
     */
    void cancel(Long id);

    /**
     * 再来一单
     * @param id
     */
    void repetition(Long id);

    /**
     * 条件搜索订单
     * @param ordersPageQueryDTO
     * @return
     */
    PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 订单完成
     * @param
     * @return
     */
    void complete(Long id);

    /**
     * 统计订单数据
     * @return
     */
    OrderStatisticsVO statistics();

    /**
     * 接单
     * @param ordersConfirmDTO
     * @return
     */
    void confirm(OrdersConfirmDTO ordersConfirmDTO);

    /**
     * 拒单
     * @param ordersRejectionDTO
     * @return
     */
    void rejection(OrdersRejectionDTO ordersRejectionDTO);

    /**
     * 派送订单
     * @param id
     * @return
     */
    void delivery(Long id);
}
