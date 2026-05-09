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
import org.opengoofy.index12306.biz.ticketservice.dao.entity.SeatDO;
import org.opengoofy.index12306.biz.ticketservice.dto.domain.SeatTypeCountDTO;
import org.opengoofy.index12306.biz.ticketservice.service.handler.ticket.dto.TrainPurchaseTicketRespDTO;

import java.util.List;

/**
 * 座位接口层
 * 公众号：马丁玩编程，回复：加群，添加马哥微信（备注：12306）获取项目资料
 */
public interface SeatService extends IService<SeatDO> {

    /**
     * 获取指定车厢中出发站到到达站区间内所有可售座位号
     * 查询条件：列车ID + 车厢号 + 座位类型 + 出发/到达站区间 + 座位状态为可售
     *
     * @param trainId        列车 ID
     * @param carriageNumber 车厢号
     * @param seatType       座位类型（0-商务座 1-一等座 2-二等座 等）
     * @param departure      出发站
     * @param arrival        到达站
     * @return 可用座位号集合
     */
    List<String> listAvailableSeat(String trainId, String carriageNumber, Integer seatType, String departure, String arrival);

    /**
     * 获取指定列车各车厢在出发站到到达站区间的余票数量
     * 优先查询 Redis 缓存，缓存未命中则回源数据库
     *
     * @param trainId           列车 ID
     * @param departure         出发站
     * @param arrival           到达站
     * @param trainCarriageList 车厢编号集合
     * @return 各车厢对应的余票数量
     */
    List<Integer> listSeatRemainingTicket(String trainId, String departure, String arrival, List<String> trainCarriageList);

    /**
     * 查询指定列车中某座位类型下所有有余票的车厢号（去重）
     * 按车厢号分组查询，只返回存在可售座位的车厢
     *
     * @param trainId      列车 ID
     * @param carriageType 座位类型（也即车厢类型）
     * @param departure    出发站
     * @param arrival      到达站
     * @return 有余票的车厢号集合
     */
    List<String> listUsableCarriageNumber(String trainId, Integer carriageType, String departure, String arrival);

    /**
     * 按座位类型统计指定列车在起止站区间内的可用座位数量
     * 供令牌桶初始化时使用，返回每种座位类型的剩余数量
     *
     * @param trainId      列车 ID
     * @param startStation 出发站
     * @param endStation   到达站
     * @param seatTypes    需要查询的座位类型集合
     * @return 每种座位类型对应的剩余可用数量
     */
    List<SeatTypeCountDTO> listSeatTypeCount(Long trainId, String startStation, String endStation, List<Integer> seatTypes);

    /**
     * 锁定指定乘客在沿途所有站点区间的座位（将座位状态改为 LOCKED）
     * 锁定范围：不仅锁定出发-到达站区间，还锁定经过的所有中间站区间，防止区间复用导致超卖
     * 锁定依据：通过 listTakeoutTrainStationRoute 获取所有需要扣减的站点区间
     *
     * @param trainId                     列车 ID
     * @param departure                   出发站
     * @param arrival                     到达站
     * @param trainPurchaseTicketRespList 乘车人及分配的座位信息（含车厢号、座位号）
     */
    void lockSeat(String trainId, String departure, String arrival, List<TrainPurchaseTicketRespDTO> trainPurchaseTicketRespList);

    /**
     * 解锁指定乘客在沿途所有站点区间的座位（将座位状态恢复为 AVAILABLE）
     * 用于订单取消、退款或支付超时等场景，释放已锁定的座位资源
     * 解锁范围与 lockSeat 相同，覆盖沿途所有中间站区间
     *
     * @param trainId                    列车 ID
     * @param departure                  出发站
     * @param arrival                    到达站
     * @param trainPurchaseTicketResults 需要解锁的乘车人及座位信息
     */
    void unlock(String trainId, String departure, String arrival, List<TrainPurchaseTicketRespDTO> trainPurchaseTicketResults);
}
