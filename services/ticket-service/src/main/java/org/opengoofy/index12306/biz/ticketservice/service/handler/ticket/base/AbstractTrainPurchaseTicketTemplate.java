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

package org.opengoofy.index12306.biz.ticketservice.service.handler.ticket.base;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import org.opengoofy.index12306.biz.ticketservice.dto.domain.RouteDTO;
import org.opengoofy.index12306.biz.ticketservice.dto.domain.TrainSeatBaseDTO;
import org.opengoofy.index12306.biz.ticketservice.service.TrainStationService;
import org.opengoofy.index12306.biz.ticketservice.service.handler.ticket.dto.SelectSeatDTO;
import org.opengoofy.index12306.biz.ticketservice.service.handler.ticket.dto.TrainPurchaseTicketRespDTO;
import org.opengoofy.index12306.framework.starter.bases.ApplicationContextHolder;
import org.opengoofy.index12306.framework.starter.cache.DistributedCache;
import org.opengoofy.index12306.framework.starter.designpattern.strategy.AbstractExecuteStrategy;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.opengoofy.index12306.biz.ticketservice.common.constant.RedisKeyConstant.TRAIN_STATION_REMAINING_TICKET;

/**
 * 抽象高铁购票模板基础服务
 * 公众号：马丁玩编程，回复：加群，添加马哥微信（备注：12306）获取项目资料
 */
public abstract class AbstractTrainPurchaseTicketTemplate implements IPurchaseTicket, CommandLineRunner, AbstractExecuteStrategy<SelectSeatDTO, List<TrainPurchaseTicketRespDTO>> {

    private DistributedCache distributedCache;
    private String ticketAvailabilityCacheUpdateType;
    private TrainStationService trainStationService;

    /**
     * 选择座位的模板方法，由子类实现具体的座位选择算法
     * 各子类的座位布局和选择策略不同：
     * - 商务座：小矩阵，相邻优先
     * - 一等座：中等矩阵，相邻优先
     * - 二等座：大矩阵（18行x5列），多种选择策略（相邻、同车厢非同排、跨车厢）
     *
     * @param requestParam 购票请求入参（含座位类型、乘客明细、选座偏好）
     * @return 每位乘客分配到的座位详情（车厢号、座位号）
     */
    protected abstract List<TrainPurchaseTicketRespDTO> selectSeats(SelectSeatDTO requestParam);

    protected TrainSeatBaseDTO buildTrainSeatBaseDTO(SelectSeatDTO requestParam) {
        return TrainSeatBaseDTO.builder()
                .trainId(requestParam.getRequestParam().getTrainId())
                .departure(requestParam.getRequestParam().getDeparture())
                .arrival(requestParam.getRequestParam().getArrival())
                .chooseSeatList(requestParam.getRequestParam().getChooseSeats())
                .passengerSeatDetails(requestParam.getPassengerSeatDetails())
                .build();
    }

    /**
     * 执行座位选择并扣减 Redis 余票缓存
     * 当 ticket.availability.cache-update.type = "binlog" 时跳过缓存扣减，
     * 因为缓存数据将由 Canal 监听数据库 binlog 异步更新，保证最终一致性
     */
    @Override
    public List<TrainPurchaseTicketRespDTO> executeResp(SelectSeatDTO requestParam) {
        List<TrainPurchaseTicketRespDTO> actualResult = selectSeats(requestParam);
        // 扣减车厢余票缓存，扣减站点余票缓存
        if (CollUtil.isNotEmpty(actualResult) && !StrUtil.equals(ticketAvailabilityCacheUpdateType, "binlog")) {
            String trainId = requestParam.getRequestParam().getTrainId();
            String departure = requestParam.getRequestParam().getDeparture();
            String arrival = requestParam.getRequestParam().getArrival();
            StringRedisTemplate stringRedisTemplate = (StringRedisTemplate) distributedCache.getInstance();
            List<RouteDTO> routeDTOList = trainStationService.listTakeoutTrainStationRoute(trainId, departure, arrival);
            routeDTOList.forEach(each -> {
                String keySuffix = StrUtil.join("_", trainId, each.getStartStation(), each.getEndStation());
                stringRedisTemplate.opsForHash().increment(TRAIN_STATION_REMAINING_TICKET + keySuffix, String.valueOf(requestParam.getSeatType()), -actualResult.size());
            });
        }
        return actualResult;
    }

    @Override
    public void run(String... args) throws Exception {
        distributedCache = ApplicationContextHolder.getBean(DistributedCache.class);
        trainStationService = ApplicationContextHolder.getBean(TrainStationService.class);
        ConfigurableEnvironment configurableEnvironment = ApplicationContextHolder.getBean(ConfigurableEnvironment.class);
        ticketAvailabilityCacheUpdateType = configurableEnvironment.getProperty("ticket.availability.cache-update.type", "");
    }
}
