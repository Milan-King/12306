/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengoofy.index12306.biz.orderservice.service;

import org.opengoofy.index12306.biz.orderservice.dto.domain.OrderStatusReversalDTO;
import org.opengoofy.index12306.biz.orderservice.dto.req.CancelTicketOrderReqDTO;
import org.opengoofy.index12306.biz.orderservice.dto.req.TicketOrderCreateReqDTO;
import org.opengoofy.index12306.biz.orderservice.dto.req.TicketOrderPageQueryReqDTO;
import org.opengoofy.index12306.biz.orderservice.dto.req.TicketOrderSelfPageQueryReqDTO;
import org.opengoofy.index12306.biz.orderservice.dto.resp.TicketOrderDetailRespDTO;
import org.opengoofy.index12306.biz.orderservice.dto.resp.TicketOrderDetailSelfRespDTO;
import org.opengoofy.index12306.biz.orderservice.mq.event.PayResultCallbackOrderEvent;
import org.opengoofy.index12306.framework.starter.convention.page.PageResponse;

/**
 * 订单接口层
 * 公众号：马丁玩编程，回复：加群，添加马哥微信（备注：12306）获取项目资料
 */
public interface OrderService {

    /**
     * 根据订单号查询订单详情（含乘客明细）
     *
     * @param orderSn 订单号
     * @return 订单详情（含车次、座位、乘客信息）
     */
    TicketOrderDetailRespDTO queryTicketOrderByOrderSn(String orderSn);

    /**
     * 根据用户 ID 分页查询订单列表（支持按状态过滤：待支付/已支付+退款/已完成）
     *
     * @param requestParam 分页查询参数（含用户 ID、状态类型 0-待支付 1-已支付/退款 2-已完成）
     * @return 订单分页详情列表
     */
    PageResponse<TicketOrderDetailRespDTO> pageTicketOrder(TicketOrderPageQueryReqDTO requestParam);

    /**
     * 创建火车票订单（核心方法）
     * 流程：
     * 1. 使用基因法生成订单号（雪花ID + userId 取模后缀，便于分库分表路由）
     * 2. 写入订单主表、订单明细表、乘客关联表（三表在一个事务中）
     * 3. 发送 RocketMQ 延迟消息（delayLevel=14，约10分钟），超时未支付则自动关闭订单
     *
     * @param requestParam 订单创建请求（含车次、出发/到达站、乘客明细等）
     * @return 生成的订单号
     */
    String createTicketOrder(TicketOrderCreateReqDTO requestParam);

    /**
     * 关闭订单（支付超时触发）
     * 前置条件：订单状态必须为 PENDING_PAYMENT（待支付），否则直接返回 false
     * 实际上复用 cancelTickOrder 的逻辑，语义上区分"超时关闭"和"主动取消"两个场景
     *
     * @param requestParam 关闭订单请求（含订单号）
     * @return true-关闭成功，false-订单状态不允许关闭（已支付或已取消）
     */
    boolean closeTickOrder(CancelTicketOrderReqDTO requestParam);

    /**
     * 取消订单（用户主动取消）
     * 使用 Redisson 分布式锁（key: order:canal:order_sn_{订单号}）防止并发重复取消
     * 更新订单状态为 CLOSED，同时更新所有子订单状态为 CLOSED
     *
     * @param requestParam 取消订单请求（含订单号）
     * @return true-取消成功
     */
    boolean cancelTickOrder(CancelTicketOrderReqDTO requestParam);

    /**
     * 订单状态反转（支付成功回调触发）
     * 将订单从 PENDING_PAYMENT 翻转为目标状态（如 ALREADY_PAID）
     * 同时更新所有子订单的状态
     * 使用分布式锁防止并发重复反转
     *
     * @param requestParam 状态反转请求（含订单号、目标订单状态、目标子订单状态）
     */
    void statusReversal(OrderStatusReversalDTO requestParam);

    /**
     * 支付回调更新订单支付信息
     * 更新订单的支付时间和支付渠道，标记用户已完成支付
     *
     * @param requestParam 支付回调事件（含订单号、支付时间、支付渠道）
     */
    void payCallbackOrder(PayResultCallbackOrderEvent requestParam);

    /**
     * 查询本人车票订单（通过身份证号关联查询，不限于当前登录用户）
     * 场景：用户为他人购票后，他人登录也能查到自己的车票信息
     * 实现：通过当前用户身份证号查询 t_order_item_passenger 关联表
     *
     * @param requestParam 分页查询参数
     * @return 本人车票订单分页结果
     */
    PageResponse<TicketOrderDetailSelfRespDTO> pageSelfTicketOrder(TicketOrderSelfPageQueryReqDTO requestParam);
}
