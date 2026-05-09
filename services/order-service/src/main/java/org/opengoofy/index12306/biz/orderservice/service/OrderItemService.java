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

import com.baomidou.mybatisplus.extension.service.IService;
import org.opengoofy.index12306.biz.orderservice.dao.entity.OrderItemDO;
import org.opengoofy.index12306.biz.orderservice.dto.domain.OrderItemStatusReversalDTO;
import org.opengoofy.index12306.biz.orderservice.dto.req.TicketOrderItemQueryReqDTO;
import org.opengoofy.index12306.biz.orderservice.dto.resp.TicketOrderPassengerDetailRespDTO;
import java.util.List;

/**
 * 订单明细接口层
 * 公众号：马丁玩编程，回复：加群，添加马哥微信（备注：12306）获取项目资料
 */
public interface OrderItemService extends IService<OrderItemDO> {

    /**
     * 子订单状态反转（退款场景使用）
     * 同时更新订单主表状态和指定的子订单行状态
     * 支持部分退款：只翻转退款涉及的那部分子订单，而非全部
     * 通过 realName 匹配子订单行的乘客姓名，确保只操作退款目标的子订单
     *
     * @param requestParam 状态反转请求（含订单号、目标订单状态、目标子订单状态、待反转的子订单列表）
     */
    void orderItemStatusReversal(OrderItemStatusReversalDTO requestParam);

    /**
     * 根据子订单记录 ID 列表批量查询对应的乘客车票详情
     * 用于退款场景：从订单中精确获取需要退款的具体子订单信息
     *
     * @param requestParam 查询请求（含订单号和子订单 ID 列表）
     * @return 乘客车票详情列表
     */
    List<TicketOrderPassengerDetailRespDTO> queryTicketItemOrderById(TicketOrderItemQueryReqDTO requestParam);
}
