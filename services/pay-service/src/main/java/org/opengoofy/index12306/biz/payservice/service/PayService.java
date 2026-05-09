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

package org.opengoofy.index12306.biz.payservice.service;

import org.opengoofy.index12306.biz.payservice.dto.PayCallbackReqDTO;
import org.opengoofy.index12306.biz.payservice.dto.PayInfoRespDTO;
import org.opengoofy.index12306.biz.payservice.dto.PayRespDTO;
import org.opengoofy.index12306.biz.payservice.dto.RefundReqDTO;
import org.opengoofy.index12306.biz.payservice.dto.RefundRespDTO;
import org.opengoofy.index12306.biz.payservice.dto.base.PayRequest;

/**
 * 支付接口层
 * 公众号：马丁玩编程，回复：加群，添加马哥微信（备注：12306）获取项目资料
 */
public interface PayService {

    /**
     * 创建支付单（通用支付接口）
     * 通过策略模式动态选择支付渠道（当前支持支付宝页面支付）
     * 流程：检查缓存 -> 策略分发 -> 调用支付渠道 API -> 创建支付记录 -> 缓存支付结果
     * 使用 @Idempotent 注解防止重复创建支付单
     *
     * @param requestParam 创建支付单实体（含订单号、金额、支付渠道等）
     * @return 支付返回详情（含支付表单 HTML 页面）
     */
    PayRespDTO commonPay(PayRequest requestParam);

    /**
     * 处理支付回调通知（支付宝异步通知）
     * 流程：查询支付单 -> 更新支付状态和流水号 -> 支付成功时发送 MQ 通知订单服务
     * 注意：只有支付成功（TRADE_SUCCESS）时才触发订单状态流转通知
     *
     * @param requestParam 回调支付单实体（含订单号、交易流水号、支付状态、支付时间、金额）
     */
    void callbackPay(PayCallbackReqDTO requestParam);

    /**
     * 根据订单号查询支付单详情
     *
     * @param orderSn 订单号
     * @return 支付单详情（支付状态、金额、支付时间等）
     */
    PayInfoRespDTO getPayInfoByOrderSn(String orderSn);

    /**
     * 根据支付流水号查询支付单详情
     *
     * @param paySn 支付单流水号
     * @return 支付单详情（支付状态、金额、支付时间等）
     */
    PayInfoRespDTO getPayInfoByPaySn(String paySn);

    /**
     * 公共退款接口（通用退款接口，当前为简化实现）
     * 流程：查询支付单 -> 构建退款请求 -> 策略分发 -> 调用退款渠道 API -> 更新支付单状态
     * 注意：当前返回 null 为占位实现，退款结果暂未完整返回
     *
     * @param requestParam 退款请求参数（含订单号、退款金额、退款类型等）
     * @return 退款返回详情（当前为占位空值）
     */
    RefundRespDTO commonRefund(RefundReqDTO requestParam);
}
