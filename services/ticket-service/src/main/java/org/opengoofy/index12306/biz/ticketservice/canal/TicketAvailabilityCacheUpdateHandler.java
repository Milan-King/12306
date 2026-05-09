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

package org.opengoofy.index12306.biz.ticketservice.canal;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import org.opengoofy.index12306.biz.ticketservice.common.enums.CanalExecuteStrategyMarkEnum;
import org.opengoofy.index12306.biz.ticketservice.common.enums.SeatStatusEnum;
import org.opengoofy.index12306.biz.ticketservice.mq.event.CanalBinlogEvent;
import org.opengoofy.index12306.framework.starter.cache.DistributedCache;
import org.opengoofy.index12306.framework.starter.designpattern.strategy.AbstractExecuteStrategy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.opengoofy.index12306.biz.ticketservice.common.constant.RedisKeyConstant.TRAIN_STATION_REMAINING_TICKET;

/**
 * 列车余票缓存更新组件
 * 公众号：马丁玩编程，回复：加群，添加马哥微信（备注：12306）获取项目资料
 */
@Component
@RequiredArgsConstructor
public class TicketAvailabilityCacheUpdateHandler implements AbstractExecuteStrategy<CanalBinlogEvent, Void> {

    private final DistributedCache distributedCache;

    /**
     * 监听 t_seat 表的 binlog 变更，实时更新 Redis 余票缓存
     * 增量计算逻辑：
     * - 对比每条记录的 old.seat_status 和 new.seat_status
     * - 只有 old_status 在 0(AVAILABLE)/1(LOCKED) 且 new_status 也在 0/1 时才计算变更
     * - 增量规则：old_status=0(可售) 表示原来可用现在被占用 => increment=-1
     *            old_status=1(锁定) 表示原来被锁现在释放 => increment=+1
     * 将变更按 trainId + 站点区间 + 座位类型分组后批量 Redis HINCRBY 更新
     */
    @Override
    public void execute(CanalBinlogEvent message) {
        List<Map<String, Object>> messageDataList = new ArrayList<>();
        List<Map<String, Object>> actualOldDataList = new ArrayList<>();
        for (int i = 0; i < message.getOld().size(); i++) {
            Map<String, Object> oldDataMap = message.getOld().get(i);
            if (oldDataMap.get("seat_status") != null && StrUtil.isNotBlank(oldDataMap.get("seat_status").toString())) {
                Map<String, Object> currentDataMap = message.getData().get(i);
                if (StrUtil.equalsAny(currentDataMap.get("seat_status").toString(), String.valueOf(SeatStatusEnum.AVAILABLE.getCode()), String.valueOf(SeatStatusEnum.LOCKED.getCode()))) {
                    actualOldDataList.add(oldDataMap);
                    messageDataList.add(currentDataMap);
                }
            }
        }
        if (CollUtil.isEmpty(messageDataList) || CollUtil.isEmpty(actualOldDataList)) {
            return;
        }
        Map<String, Map<Integer, Integer>> cacheChangeKeyMap = new HashMap<>();
        for (int i = 0; i < messageDataList.size(); i++) {
            Map<String, Object> each = messageDataList.get(i);
            Map<String, Object> actualOldData = actualOldDataList.get(i);
            String seatStatus = actualOldData.get("seat_status").toString();
            int increment = Objects.equals(seatStatus, "0") ? -1 : 1;
            String trainId = each.get("train_id").toString();
            String hashCacheKey = TRAIN_STATION_REMAINING_TICKET + trainId + "_" + each.get("start_station") + "_" + each.get("end_station");
            Map<Integer, Integer> seatTypeMap = cacheChangeKeyMap.get(hashCacheKey);
            if (CollUtil.isEmpty(seatTypeMap)) {
                seatTypeMap = new HashMap<>();
            }
            Integer seatType = Integer.parseInt(each.get("seat_type").toString());
            Integer num = seatTypeMap.get(seatType);
            seatTypeMap.put(seatType, num == null ? increment : num + increment);
            cacheChangeKeyMap.put(hashCacheKey, seatTypeMap);
        }
        StringRedisTemplate instance = (StringRedisTemplate) distributedCache.getInstance();
        cacheChangeKeyMap.forEach((cacheKey, cacheVal) -> cacheVal.forEach((seatType, num) -> instance.opsForHash().increment(cacheKey, String.valueOf(seatType), num)));
    }

    @Override
    public String mark() {
        return CanalExecuteStrategyMarkEnum.T_SEAT.getActualTable();
    }
}
