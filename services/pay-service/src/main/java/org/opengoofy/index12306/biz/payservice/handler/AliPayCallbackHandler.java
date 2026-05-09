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

package org.opengoofy.index12306.biz.payservice.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opengoofy.index12306.biz.payservice.common.enums.PayChannelEnum;
import org.opengoofy.index12306.biz.payservice.common.enums.TradeStatusEnum;
import org.opengoofy.index12306.biz.payservice.dto.PayCallbackReqDTO;
import org.opengoofy.index12306.biz.payservice.dto.base.AliPayCallbackRequest;
import org.opengoofy.index12306.biz.payservice.dto.base.PayCallbackRequest;
import org.opengoofy.index12306.biz.payservice.handler.base.AbstractPayCallbackHandler;
import org.opengoofy.index12306.biz.payservice.service.PayService;
import org.opengoofy.index12306.framework.starter.designpattern.strategy.AbstractExecuteStrategy;
import org.springframework.stereotype.Service;

/**
 * 阿里支付回调组件
 * 公众号：马丁玩编程，回复：加群，添加马哥微信（备注：12306）获取项目资料
 */
@Slf4j
@Service
@RequiredArgsConstructor
public final class AliPayCallbackHandler extends AbstractPayCallbackHandler implements AbstractExecuteStrategy<PayCallbackRequest, Void> {

    private final PayService payService;

    /**
     * 处理支付宝异步支付回调
     * 将支付宝回调参数转换为内部 PayCallbackReqDTO：
     * - tradeStatus 映射为内部交易状态码（如 WAIT_BUYER_PAY -> 内部编码）
     * - buyerPayAmount 为用户实际支付金额
     * - tradeNo 为支付宝交易流水号
     * 最终委托 PayService.callbackPay 完成后续支付状态更新和订单通知
     */
    @Override
    public void callback(PayCallbackRequest payCallbackRequest) {
        AliPayCallbackRequest aliPayCallBackRequest = payCallbackRequest.getAliPayCallBackRequest();
        PayCallbackReqDTO payCallbackRequestParam = PayCallbackReqDTO.builder()
                .status(TradeStatusEnum.queryActualTradeStatusCode(aliPayCallBackRequest.getTradeStatus()))
                .payAmount(aliPayCallBackRequest.getBuyerPayAmount())
                .tradeNo(aliPayCallBackRequest.getTradeNo())
                .gmtPayment(aliPayCallBackRequest.getGmtPayment())
                .orderSn(aliPayCallBackRequest.getOrderRequestId())
                .build();
        payService.callbackPay(payCallbackRequestParam);
    }

    @Override
    public String mark() {
        return PayChannelEnum.ALI_PAY.name();
    }

    public void execute(PayCallbackRequest requestParam) {
        callback(requestParam);
    }
}
