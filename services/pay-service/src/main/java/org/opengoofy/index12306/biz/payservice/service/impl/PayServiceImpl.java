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

package org.opengoofy.index12306.biz.payservice.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opengoofy.index12306.biz.payservice.common.enums.TradeStatusEnum;
import org.opengoofy.index12306.biz.payservice.convert.RefundRequestConvert;
import org.opengoofy.index12306.biz.payservice.dao.entity.PayDO;
import org.opengoofy.index12306.biz.payservice.dao.mapper.PayMapper;
import org.opengoofy.index12306.biz.payservice.dto.PayCallbackReqDTO;
import org.opengoofy.index12306.biz.payservice.dto.PayInfoRespDTO;
import org.opengoofy.index12306.biz.payservice.dto.PayRespDTO;
import org.opengoofy.index12306.biz.payservice.dto.RefundCommand;
import org.opengoofy.index12306.biz.payservice.dto.RefundReqDTO;
import org.opengoofy.index12306.biz.payservice.dto.RefundRespDTO;
import org.opengoofy.index12306.biz.payservice.dto.base.PayRequest;
import org.opengoofy.index12306.biz.payservice.dto.base.PayResponse;
import org.opengoofy.index12306.biz.payservice.dto.base.RefundRequest;
import org.opengoofy.index12306.biz.payservice.dto.base.RefundResponse;
import org.opengoofy.index12306.biz.payservice.handler.AliPayNativeHandler;
import org.opengoofy.index12306.biz.payservice.handler.AliRefundNativeHandler;
import org.opengoofy.index12306.biz.payservice.mq.event.PayResultCallbackOrderEvent;
import org.opengoofy.index12306.biz.payservice.mq.produce.PayResultCallbackOrderSendProduce;
import org.opengoofy.index12306.biz.payservice.service.PayService;
import org.opengoofy.index12306.biz.payservice.service.payid.PayIdGeneratorManager;
import org.opengoofy.index12306.framework.starter.cache.DistributedCache;
import org.opengoofy.index12306.framework.starter.common.toolkit.BeanUtil;
import org.opengoofy.index12306.framework.starter.convention.exception.ServiceException;
import org.opengoofy.index12306.framework.starter.designpattern.strategy.AbstractStrategyChoose;
import org.opengoofy.index12306.framework.starter.idempotent.annotation.Idempotent;
import org.opengoofy.index12306.framework.starter.idempotent.enums.IdempotentTypeEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.opengoofy.index12306.biz.payservice.common.constant.RedisKeyConstant.ORDER_PAY_RESULT_INFO;

/**
 * 支付接口层实现
 * 公众号：马丁玩编程，回复：加群，添加马哥微信（备注：12306）获取项目资料
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayServiceImpl implements PayService {

    private final PayMapper payMapper;
    private final AbstractStrategyChoose abstractStrategyChoose;
    private final PayResultCallbackOrderSendProduce payResultCallbackOrderSendProduce;
    private final DistributedCache distributedCache;

    /**
     * 创建支付单实现
     * 1. 先查缓存，已支付过的订单直接返回缓存结果（防止重复支付）
     * 2. 通过策略模式选择支付渠道（当前为支付宝页面支付）并调用第三方 API
     * 3. 金额转换：元 -> 分（乘以100向上取整，适配支付宝金额单位）
     * 4. 生成支付流水号（使用基因法融入订单号后缀，便于分库分表路由）
     * 5. 将支付结果缓存 10 分钟，防止短期内重复查询造成资源浪费
     */
    @Idempotent(
            type = IdempotentTypeEnum.SPEL,
            uniqueKeyPrefix = "index12306-pay:lock_create_pay:",
            key = "#requestParam.getOutOrderSn()"
    )
    @Transactional(rollbackFor = Exception.class)
    @Override
    public PayRespDTO commonPay(PayRequest requestParam) {
        PayRespDTO cacheResult = distributedCache.get(ORDER_PAY_RESULT_INFO + requestParam.getOrderSn(), PayRespDTO.class);
        if (cacheResult != null) {
            return cacheResult;
        }
        /**
         * {@link AliPayNativeHandler}
         */
        // 策略模式：通过策略模式封装支付渠道和支付场景，用户支付时动态选择对应的支付组件
        PayResponse result = abstractStrategyChoose.chooseAndExecuteResp(requestParam.buildMark(), requestParam);
        PayDO insertPay = BeanUtil.convert(requestParam, PayDO.class);
        String paySn = PayIdGeneratorManager.generateId(requestParam.getOrderSn());
        insertPay.setPaySn(paySn);
        insertPay.setStatus(TradeStatusEnum.WAIT_BUYER_PAY.tradeCode());
        insertPay.setTotalAmount(requestParam.getTotalAmount().multiply(new BigDecimal("100")).setScale(0, BigDecimal.ROUND_HALF_UP).intValue());
        int insert = payMapper.insert(insertPay);
        if (insert <= 0) {
            log.error("支付单创建失败，支付聚合根：{}", JSON.toJSONString(requestParam));
            throw new ServiceException("支付单创建失败");
        }
        distributedCache.put(ORDER_PAY_RESULT_INFO + requestParam.getOrderSn(), JSON.toJSONString(result), 10, TimeUnit.MINUTES);
        return BeanUtil.convert(result, PayRespDTO.class);
    }

    /**
     * 处理支付宝回调通知
     * 1. 查询支付单是否存在
     * 2. 更新支付状态、交易流水号、支付时间、支付金额
     * 3. 当支付状态为 TRADE_SUCCESS 时，通过 RocketMQ 通知订单服务更新订单状态
     * 注意：支付宝可能会多次发送回调通知，依靠 @Idempotent 注解保证幂等性
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void callbackPay(PayCallbackReqDTO requestParam) {
        LambdaQueryWrapper<PayDO> queryWrapper = Wrappers.lambdaQuery(PayDO.class)
                .eq(PayDO::getOrderSn, requestParam.getOrderSn());
        PayDO payDO = payMapper.selectOne(queryWrapper);
        if (Objects.isNull(payDO)) {
            log.error("支付单不存在，orderRequestId：{}", requestParam.getOrderRequestId());
            throw new ServiceException("支付单不存在");
        }
        payDO.setTradeNo(requestParam.getTradeNo());
        payDO.setStatus(requestParam.getStatus());
        payDO.setPayAmount(requestParam.getPayAmount());
        payDO.setGmtPayment(requestParam.getGmtPayment());
        LambdaUpdateWrapper<PayDO> updateWrapper = Wrappers.lambdaUpdate(PayDO.class)
                .eq(PayDO::getOrderSn, requestParam.getOrderSn());
        int result = payMapper.update(payDO, updateWrapper);
        if (result <= 0) {
            log.error("修改支付单支付结果失败，支付单信息：{}", JSON.toJSONString(payDO));
            throw new ServiceException("修改支付单支付结果失败");
        }
        // 交易成功，回调订单服务告知支付结果，修改订单流转状态
        if (Objects.equals(requestParam.getStatus(), TradeStatusEnum.TRADE_SUCCESS.tradeCode())) {
            payResultCallbackOrderSendProduce.sendMessage(BeanUtil.convert(payDO, PayResultCallbackOrderEvent.class));
        }
    }

    @Override
    public PayInfoRespDTO getPayInfoByOrderSn(String orderSn) {
        LambdaQueryWrapper<PayDO> queryWrapper = Wrappers.lambdaQuery(PayDO.class)
                .eq(PayDO::getOrderSn, orderSn);
        PayDO payDO = payMapper.selectOne(queryWrapper);
        return BeanUtil.convert(payDO, PayInfoRespDTO.class);
    }

    @Override
    public PayInfoRespDTO getPayInfoByPaySn(String paySn) {
        LambdaQueryWrapper<PayDO> queryWrapper = Wrappers.lambdaQuery(PayDO.class)
                .eq(PayDO::getPaySn, paySn);
        PayDO payDO = payMapper.selectOne(queryWrapper);
        return BeanUtil.convert(payDO, PayInfoRespDTO.class);
    }

    /**
     * 公共退款接口实现（简化版）
     * 1. 查询支付单并校验存在性
     * 2. 通过策略模式选择退款渠道（当前为支付宝退款）并调用第三方 API
     * 3. 更新支付单状态为退款结果状态
     * 注意：当前返回 null 为占位实现，后续需构造完整 RefundRespDTO 返回退款详情
     */
    @Override
    public RefundRespDTO commonRefund(RefundReqDTO requestParam) {
        LambdaQueryWrapper<PayDO> queryWrapper = Wrappers.lambdaQuery(PayDO.class)
                .eq(PayDO::getOrderSn, requestParam.getOrderSn());
        PayDO payDO = payMapper.selectOne(queryWrapper);
        if (Objects.isNull(payDO)) {
            log.error("支付单不存在，orderSn：{}", requestParam.getOrderSn());
            throw new ServiceException("支付单不存在");
        }
        /**
         * {@link AliRefundNativeHandler}
         */
        // 策略模式：通过策略模式封装退款渠道和退款场景，用户退款时动态选择对应的退款组件
        RefundCommand refundCommand = BeanUtil.convert(payDO, RefundCommand.class);
        RefundRequest refundRequest = RefundRequestConvert.command2RefundRequest(refundCommand);
        RefundResponse result = abstractStrategyChoose.chooseAndExecuteResp(refundRequest.buildMark(), refundRequest);
        payDO.setStatus(result.getStatus());
        LambdaUpdateWrapper<PayDO> updateWrapper = Wrappers.lambdaUpdate(PayDO.class)
                .eq(PayDO::getOrderSn, requestParam.getOrderSn());
        int updateResult = payMapper.update(payDO, updateWrapper);
        if (updateResult <= 0) {
            log.error("修改支付单退款结果失败，支付单信息：{}", JSON.toJSONString(payDO));
            throw new ServiceException("修改支付单退款结果失败");
        }
        return null;
    }
}
