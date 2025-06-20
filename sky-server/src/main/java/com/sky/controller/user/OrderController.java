package com.sky.controller.user;

import com.sky.dto.OrdersConfirmDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.OrderService;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController("userOrderController")
@RequestMapping("/user/order")
@Api(tags = "C端-订单接口")
@Slf4j
public class OrderController {


    @Autowired
    private OrderService orderService;

    /**
     * 用户下单
     * @param ordersSubmitDTO
     * @return
     */
    @PostMapping("/submit")
    @ApiOperation("用户下单")
    public Result<OrderSubmitVO> submit(@RequestBody OrdersSubmitDTO ordersSubmitDTO){
        log.info("用户下单：{}", ordersSubmitDTO);
        OrderSubmitVO orderSubmitVO = orderService.submitOrder(ordersSubmitDTO);

        return Result.success(orderSubmitVO);
    }

    /**
     * 订单列表
     * @param page
     * @param pageSize
     * @param status
     * @return
     */
    @GetMapping("/historyOrders")
    @ApiOperation("订单列表")
    public Result<PageResult> page(int page , int pageSize , Integer status){
        log.info("订单分页查询");
        PageResult pageResult = orderService.pageQuery4User(page,pageSize,status);
        return Result.success(pageResult);

    }

    @GetMapping("orderDetail/{id}")
    @ApiOperation("订单详情")
    public Result<OrderVO> details(@PathVariable("id") Long id){
        log.info("订单详情");
        OrderVO orderVO = orderService.details(id);
        return Result.success(orderVO);
    }
    @PutMapping("/cancel/{id}")
    @ApiOperation("取消订单")
    public Result cancel(@PathVariable Long id){
        log.info("用户取消订单", id);
        orderService.cancel(id);
        return Result.success();
    }
    @PostMapping("/repetition/{id}")
    @ApiOperation("再来一单")
    public Result repetition(@PathVariable Long id){
        log.info("再来一单");
        orderService.repetition(id);
        return Result.success();
    }



}
