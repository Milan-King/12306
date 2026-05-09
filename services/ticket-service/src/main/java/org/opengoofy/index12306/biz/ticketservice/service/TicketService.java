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

package org.opengoofy.index12306.biz.ticketservice.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.opengoofy.index12306.biz.ticketservice.dao.entity.TicketDO;
import org.opengoofy.index12306.biz.ticketservice.dto.req.CancelTicketOrderReqDTO;
import org.opengoofy.index12306.biz.ticketservice.dto.req.PurchaseTicketReqDTO;
import org.opengoofy.index12306.biz.ticketservice.dto.req.RefundTicketReqDTO;
import org.opengoofy.index12306.biz.ticketservice.dto.req.TicketPageQueryReqDTO;
import org.opengoofy.index12306.biz.ticketservice.dto.resp.RefundTicketRespDTO;
import org.opengoofy.index12306.biz.ticketservice.dto.resp.TicketPageQueryRespDTO;
import org.opengoofy.index12306.biz.ticketservice.dto.resp.TicketPurchaseRespDTO;
import org.opengoofy.index12306.biz.ticketservice.remote.dto.PayInfoRespDTO;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 车票接口
 * 公众号：马丁玩编程，回复：加群，添加马哥微信（备注：12306）获取项目资料
 */
public interface TicketService extends IService<TicketDO> {

    /**
     * 根据条件分页查询车票（V1版本，使用 DCL 双重检查锁保证缓存加载的线程安全）
     * V1 版本在高并发场景存在性能瓶颈，原因是单次查询多次 Redis 交互，建议使用 V2 版本
     *
     * @param requestParam 分页查询车票请求参数，包含出发站、到达站、出发日期等
     * @return 车票查询结果，包含车次列表、座位类型、价格及余票信息
     */
    TicketPageQueryRespDTO pageListTicketQueryV1(TicketPageQueryReqDTO requestParam);

    /**
     * 根据条件分页查询车票（V2 高性能版本，使用 Redis Pipeline 批量查询减少网络往返）
     * 相比 V1 版本：将分散的票价和余票查询合并为两条 Pipeline 命令，减少 Redis 网络 IO 次数，性能提升 3-5 倍
     *
     * @param requestParam 分页查询车票请求参数，包含出发站、到达站、出发日期等
     * @return 车票查询结果，包含车次列表、座位类型、价格及余票信息
     */
    TicketPageQueryRespDTO pageListTicketQueryV2(TicketPageQueryReqDTO requestParam);

    /**
     * 购买车票（V1版本，使用单一 Redisson 分布式锁，锁定整列车防止并发超卖）
     * 锁粒度较大，高并发场景下性能受限，适合初期快速实现
     *
     * @param requestParam 车票购买请求参数，包含车次、出发站、到达站、乘车人及座位偏好
     * @return 订单号及订单详情
     */
    TicketPurchaseRespDTO purchaseTicketsV1(@RequestBody PurchaseTicketReqDTO requestParam);

    /**
     * 购买车票（V2 高性能版本，采用细粒度锁 + 本地锁 + 令牌桶三层并发控制）
     * 相比 V1 的改进：
     * 1. 按座位类型分组，每组使用独立的本地 ReentrantLock + Redisson 公平锁，降低锁竞争
     * 2. 引入令牌桶预校验，提前拦截无票请求，减少无效的锁竞争
     * 3. 令牌耗尽后有缓存刷新机制，避免重复查询数据库
     *
     * @param requestParam 车票购买请求参数，包含车次、出发站、到达站、乘车人及座位偏好
     * @return 订单号及订单详情
     */
    TicketPurchaseRespDTO purchaseTicketsV2(@RequestBody PurchaseTicketReqDTO requestParam);

    /**
     * 执行购买车票的核心事务逻辑（被 V1/V2 版本内部调用）
     * 使用 Spring 自注入方式（self-injection）解决 AOP 代理下 @Transactional 失效问题：
     * TicketServiceImpl 通过 ApplicationContextHolder 获取自身代理对象，再用代理调用本方法以确保事务生效
     * 核心步骤：座位选择 -> 保存车票记录 -> 远程创建订单 -> 返回订单号
     *
     * @param requestParam 车票购买请求参数
     * @return 订单号及乘客订单详情
     */
    TicketPurchaseRespDTO executePurchaseTickets(@RequestBody PurchaseTicketReqDTO requestParam);

    /**
     * 根据订单号查询支付单详情（远程调用 pay-service）
     *
     * @param orderSn 订单号
     * @return 支付单详情，包含支付状态、金额、支付渠道等
     */
    PayInfoRespDTO getPayInfo(String orderSn);

    /**
     * 取消车票订单，执行补偿操作：
     * 1. 调用订单服务关闭订单
     * 2. 解锁沿途座位（DB + Redis 缓存）
     * 3. 回滚令牌桶计数
     * 当 ticket.availability.cache-update.type=binlog 时，缓存更新由 Canal binlog 同步完成，此处跳过直接缓存更新
     *
     * @param requestParam 取消车票订单入参，包含订单号
     */
    void cancelTicketOrder(CancelTicketOrderReqDTO requestParam);

    /**
     * 公共退款接口，支持全额退款和部分退款：
     * 1. 责任链验证参数
     * 2. 查询订单详情，区分全额/部分退款
     * 3. 调用支付服务执行退款
     * 注意：当前返回 null 为占位实现，退款结果暂未完整返回
     *
     * @param requestParam 退款请求参数，包含订单号、退款类型、退款子订单记录等
     * @return 退款返回详情（当前为占位空值）
     */
    RefundTicketRespDTO commonTicketRefund(RefundTicketReqDTO requestParam);
}
