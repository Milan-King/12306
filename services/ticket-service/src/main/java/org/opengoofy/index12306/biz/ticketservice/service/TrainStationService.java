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

import org.opengoofy.index12306.biz.ticketservice.dto.domain.RouteDTO;
import org.opengoofy.index12306.biz.ticketservice.dto.resp.TrainStationQueryRespDTO;

import java.util.List;

/**
 * 列车站点接口层
 * 公众号：马丁玩编程，回复：加群，添加马哥微信（备注：12306）获取项目资料
 */
public interface TrainStationService {

    /**
     * 根据列车 ID 查询该列车所有的经停站信息
     *
     * @param trainId 列车 ID
     * @return 列车经停站信息列表（含站点名称、到站/离站时间等）
     */
    List<TrainStationQueryRespDTO> listTrainStationQuery(String trainId);

    /**
     * 计算从出发站到到达站之间经过的所有站点路线（含首尾站）
     * 用于余票查询场景：需要知道用户乘车区间覆盖了哪些站点段
     * 示例：北京->上海（经停天津、济南、南京）=> 北京-天津、天津-济南、济南-南京、南京-上海
     *
     * @param trainId   列车 ID
     * @param departure 出发站
     * @param arrival   到达站
     * @return 出发站到到达站之间所有相邻站点组成的路线关系
     */
    List<RouteDTO> listTrainStationRoute(String trainId, String departure, String arrival);

    /**
     * 获取购票时需要扣减余票的所有站点区间（含首尾站、中间站及关联站点）
     * 与 listTrainStationRoute 的区别：此方法返回的区间集合更大，包含了因区间组合使用而需要额外扣减的路段
     * 用于购票和取消场景：确保所有受影响的站点区间余票都被正确更新
     * 示例：购买北京->济南的票，需扣减北京-天津、北京-济南、天津-济南 三个区间的余票
     *
     * @param trainId   列车 ID
     * @param departure 出发站
     * @param arrival   到达站
     * @return 所有需要扣减余票的站点区间
     */
    List<RouteDTO> listTakeoutTrainStationRoute(String trainId, String departure, String arrival);
}
