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

package org.opengoofy.index12306.biz.ticketservice.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opengoofy.index12306.biz.ticketservice.common.enums.RefundTypeEnum;
import org.opengoofy.index12306.biz.ticketservice.common.enums.SourceEnum;
import org.opengoofy.index12306.biz.ticketservice.common.enums.TicketChainMarkEnum;
import org.opengoofy.index12306.biz.ticketservice.common.enums.TicketStatusEnum;
import org.opengoofy.index12306.biz.ticketservice.common.enums.VehicleTypeEnum;
import org.opengoofy.index12306.biz.ticketservice.dao.entity.StationDO;
import org.opengoofy.index12306.biz.ticketservice.dao.entity.TicketDO;
import org.opengoofy.index12306.biz.ticketservice.dao.entity.TrainDO;
import org.opengoofy.index12306.biz.ticketservice.dao.entity.TrainStationPriceDO;
import org.opengoofy.index12306.biz.ticketservice.dao.entity.TrainStationRelationDO;
import org.opengoofy.index12306.biz.ticketservice.dao.mapper.StationMapper;
import org.opengoofy.index12306.biz.ticketservice.dao.mapper.TicketMapper;
import org.opengoofy.index12306.biz.ticketservice.dao.mapper.TrainMapper;
import org.opengoofy.index12306.biz.ticketservice.dao.mapper.TrainStationPriceMapper;
import org.opengoofy.index12306.biz.ticketservice.dao.mapper.TrainStationRelationMapper;
import org.opengoofy.index12306.biz.ticketservice.dto.domain.PurchaseTicketPassengerDetailDTO;
import org.opengoofy.index12306.biz.ticketservice.dto.domain.RouteDTO;
import org.opengoofy.index12306.biz.ticketservice.dto.domain.SeatClassDTO;
import org.opengoofy.index12306.biz.ticketservice.dto.domain.SeatTypeCountDTO;
import org.opengoofy.index12306.biz.ticketservice.dto.domain.TicketListDTO;
import org.opengoofy.index12306.biz.ticketservice.dto.req.CancelTicketOrderReqDTO;
import org.opengoofy.index12306.biz.ticketservice.dto.req.PurchaseTicketReqDTO;
import org.opengoofy.index12306.biz.ticketservice.dto.req.RefundTicketReqDTO;
import org.opengoofy.index12306.biz.ticketservice.dto.req.TicketOrderItemQueryReqDTO;
import org.opengoofy.index12306.biz.ticketservice.dto.req.TicketPageQueryReqDTO;
import org.opengoofy.index12306.biz.ticketservice.dto.resp.RefundTicketRespDTO;
import org.opengoofy.index12306.biz.ticketservice.dto.resp.TicketOrderDetailRespDTO;
import org.opengoofy.index12306.biz.ticketservice.dto.resp.TicketPageQueryRespDTO;
import org.opengoofy.index12306.biz.ticketservice.dto.resp.TicketPurchaseRespDTO;
import org.opengoofy.index12306.biz.ticketservice.remote.PayRemoteService;
import org.opengoofy.index12306.biz.ticketservice.remote.TicketOrderRemoteService;
import org.opengoofy.index12306.biz.ticketservice.remote.dto.PayInfoRespDTO;
import org.opengoofy.index12306.biz.ticketservice.remote.dto.RefundReqDTO;
import org.opengoofy.index12306.biz.ticketservice.remote.dto.RefundRespDTO;
import org.opengoofy.index12306.biz.ticketservice.remote.dto.TicketOrderCreateRemoteReqDTO;
import org.opengoofy.index12306.biz.ticketservice.remote.dto.TicketOrderItemCreateRemoteReqDTO;
import org.opengoofy.index12306.biz.ticketservice.remote.dto.TicketOrderPassengerDetailRespDTO;
import org.opengoofy.index12306.biz.ticketservice.service.SeatService;
import org.opengoofy.index12306.biz.ticketservice.service.TicketService;
import org.opengoofy.index12306.biz.ticketservice.service.TrainStationService;
import org.opengoofy.index12306.biz.ticketservice.service.cache.SeatMarginCacheLoader;
import org.opengoofy.index12306.biz.ticketservice.service.handler.ticket.dto.TokenResultDTO;
import org.opengoofy.index12306.biz.ticketservice.service.handler.ticket.dto.TrainPurchaseTicketRespDTO;
import org.opengoofy.index12306.biz.ticketservice.service.handler.ticket.select.TrainSeatTypeSelector;
import org.opengoofy.index12306.biz.ticketservice.service.handler.ticket.tokenbucket.TicketAvailabilityTokenBucket;
import org.opengoofy.index12306.biz.ticketservice.toolkit.DateUtil;
import org.opengoofy.index12306.biz.ticketservice.toolkit.TimeStringComparator;
import org.opengoofy.index12306.framework.starter.bases.ApplicationContextHolder;
import org.opengoofy.index12306.framework.starter.cache.DistributedCache;
import org.opengoofy.index12306.framework.starter.cache.toolkit.CacheUtil;
import org.opengoofy.index12306.framework.starter.common.toolkit.BeanUtil;
import org.opengoofy.index12306.framework.starter.convention.exception.ServiceException;
import org.opengoofy.index12306.framework.starter.convention.result.Result;
import org.opengoofy.index12306.framework.starter.designpattern.chain.AbstractChainContext;
import org.opengoofy.index12306.framework.starter.idempotent.annotation.Idempotent;
import org.opengoofy.index12306.framework.starter.idempotent.enums.IdempotentSceneEnum;
import org.opengoofy.index12306.framework.starter.idempotent.enums.IdempotentTypeEnum;
import org.opengoofy.index12306.framework.starter.log.annotation.ILog;
import org.opengoofy.index12306.frameworks.starter.user.core.UserContext;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static org.opengoofy.index12306.biz.ticketservice.common.constant.Index12306Constant.ADVANCE_TICKET_DAY;
import static org.opengoofy.index12306.biz.ticketservice.common.constant.RedisKeyConstant.LOCK_PURCHASE_TICKETS;
import static org.opengoofy.index12306.biz.ticketservice.common.constant.RedisKeyConstant.LOCK_PURCHASE_TICKETS_V2;
import static org.opengoofy.index12306.biz.ticketservice.common.constant.RedisKeyConstant.LOCK_REGION_TRAIN_STATION;
import static org.opengoofy.index12306.biz.ticketservice.common.constant.RedisKeyConstant.LOCK_REGION_TRAIN_STATION_MAPPING;
import static org.opengoofy.index12306.biz.ticketservice.common.constant.RedisKeyConstant.LOCK_TOKEN_BUCKET_ISNULL;
import static org.opengoofy.index12306.biz.ticketservice.common.constant.RedisKeyConstant.REGION_TRAIN_STATION;
import static org.opengoofy.index12306.biz.ticketservice.common.constant.RedisKeyConstant.REGION_TRAIN_STATION_MAPPING;
import static org.opengoofy.index12306.biz.ticketservice.common.constant.RedisKeyConstant.TRAIN_INFO;
import static org.opengoofy.index12306.biz.ticketservice.common.constant.RedisKeyConstant.TRAIN_STATION_PRICE;
import static org.opengoofy.index12306.biz.ticketservice.common.constant.RedisKeyConstant.TRAIN_STATION_REMAINING_TICKET;
import static org.opengoofy.index12306.biz.ticketservice.toolkit.DateUtil.convertDateToLocalTime;

/**
 * 车票接口实现
 * 公众号：马丁玩编程，回复：加群，添加马哥微信（备注：12306）获取项目资料
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketServiceImpl extends ServiceImpl<TicketMapper, TicketDO> implements TicketService, CommandLineRunner {

    private final TrainMapper trainMapper;
    private final TrainStationRelationMapper trainStationRelationMapper;
    private final TrainStationPriceMapper trainStationPriceMapper;
    private final DistributedCache distributedCache;
    private final TicketOrderRemoteService ticketOrderRemoteService;
    private final PayRemoteService payRemoteService;
    private final StationMapper stationMapper;
    private final SeatService seatService;
    private final TrainStationService trainStationService;
    private final TrainSeatTypeSelector trainSeatTypeSelector;
    private final SeatMarginCacheLoader seatMarginCacheLoader;
    private final AbstractChainContext<TicketPageQueryReqDTO> ticketPageQueryAbstractChainContext;
    private final AbstractChainContext<PurchaseTicketReqDTO> purchaseTicketAbstractChainContext;
    private final AbstractChainContext<RefundTicketReqDTO> refundReqDTOAbstractChainContext;
    private final RedissonClient redissonClient;
    private final ConfigurableEnvironment environment;
    private final TicketAvailabilityTokenBucket ticketAvailabilityTokenBucket;
    private TicketService ticketService;

    @Value("${ticket.availability.cache-update.type:}")
    private String ticketAvailabilityCacheUpdateType;
    @Value("${framework.cache.redis.prefix:}")
    private String cacheRedisPrefix;

    /**
     * V1 版本车票查询：采用 Cache-Aside 模式 + DCL（双重检查锁）保证缓存一致性
     * <p>
     * 整体流程分为五大阶段：
     * <ol>
     *   <li>参数校验（责任链）</li>
     *   <li>站点→地区映射（Redis Hash: REGION_TRAIN_STATION_MAPPING）</li>
     *   <li>地区→列车列表（Redis Hash: REGION_TRAIN_STATION）</li>
     *   <li>逐车次获取票价（Redis String / DB）</li>
     *   <li>逐车次逐座型获取余票（Redis Hash / DB）</li>
     * </ol>
     * 性能问题：阶段4和5对每个车次逐一查Redis/DB，交互次数 = 车次数 × 座型数，高并发下是性能深渊。
     * V2 版本通过 Redis Pipeline 将 N 次网络IO 合并为 2 次批量操作，性能提升 300%-500%+。
     */
    @Override
    public TicketPageQueryRespDTO pageListTicketQueryV1(TicketPageQueryReqDTO requestParam) {

        // ═══════════════════════════════════════════════════════════════════════
        // 阶段一：参数校验（责任链模式）
        // 验证：城市名称是否合法、出发日期是否在预售期内、日期不能小于当前日期等
        // 多个校验器按 order 排序依次执行，任何一个不通过都会抛异常
        // ═══════════════════════════════════════════════════════════════════════
        ticketPageQueryAbstractChainContext.handler(TicketChainMarkEnum.TRAIN_QUERY_FILTER.name(), requestParam);
        StringRedisTemplate stringRedisTemplate = (StringRedisTemplate) distributedCache.getInstance();

        // ═══════════════════════════════════════════════════════════════════════
        // 阶段二：站点编码 → 地区名称 映射（Cache-Aside + DCL 双重检查锁）
        //
        // 为什么需要这个映射？
        //   用户输入的是站点编码（如 "BJP" = 北京），而后续查列车时用的是
        //   地区维度（"北京"→"杭州"之间运行的所有列车）。因此需要先把站码
        //   翻译成地区名。
        //
        // Redis 数据结构：
        //   Hash Key: REGION_TRAIN_STATION_MAPPING
        //   Fields:   "BJP" → "北京", "HZH" → "杭州", ...
        //
        // 缓存未命中时：
        //   1. 加分布式锁，再次检查缓存（防止并发重复加载）
        //   2. 全量加载 t_station 表，构建 code→regionName 映射
        //   3. 写入 Redis Hash
        // ═══════════════════════════════════════════════════════════════════════
        List<Object> stationDetails = stringRedisTemplate.opsForHash()
                .multiGet(REGION_TRAIN_STATION_MAPPING, Lists.newArrayList(requestParam.getFromStation(), requestParam.getToStation()));
        long count = stationDetails.stream().filter(Objects::isNull).count();
        if (count > 0) {
            // 缓存未命中：加锁后执行 DCL 二次检查
            RLock lock = redissonClient.getLock(LOCK_REGION_TRAIN_STATION_MAPPING);
            lock.lock();
            try {
                // --- DCL 第二次检查：可能上一个持锁线程已经加载完了 ---
                stationDetails = stringRedisTemplate.opsForHash()
                        .multiGet(REGION_TRAIN_STATION_MAPPING, Lists.newArrayList(requestParam.getFromStation(), requestParam.getToStation()));
                count = stationDetails.stream().filter(Objects::isNull).count();
                if (count > 0) {
                    // --- 全量加载 t_station 表，构建所有车站的 code→地区映射 ---
                    List<StationDO> stationDOList = stationMapper.selectList(Wrappers.emptyWrapper());
                    Map<String, String> regionTrainStationMap = new HashMap<>();
                    stationDOList.forEach(each -> regionTrainStationMap.put(each.getCode(), each.getRegionName()));
                    // --- 写入 Redis Hash，后续请求直接命中缓存 ---
                    stringRedisTemplate.opsForHash().putAll(REGION_TRAIN_STATION_MAPPING, regionTrainStationMap);
                    stationDetails = new ArrayList<>();
                    stationDetails.add(regionTrainStationMap.get(requestParam.getFromStation()));
                    stationDetails.add(regionTrainStationMap.get(requestParam.getToStation()));
                }
            } finally {
                lock.unlock();
            }
        }
        // 此时 stationDetails = ["北京", "杭州"]

        // ═══════════════════════════════════════════════════════════════════════
        // 阶段三：地区对 → 列车列表（Cache-Aside + DCL）
        //
        // Redis 数据结构：
        //   Hash Key: REGION_TRAIN_STATION + 北京 + 杭州   （如 "train_station:北京_杭州"）
        //   Fields:   "1_北京南_杭州东" → JSON{TicketListDTO}
        //             "3_北京_杭州"     → JSON{TicketListDTO}
        //
        // 每个 field 代表一列从北京地区到杭州地区的列车，key 是 trainId+出发站+到达站。
        // value 是车次基本信息的 JSON（车次号、出发时间、到达时间、历时等），
        // 但此时还不包含票价和余票信息。
        //
        // 缓存未命中时：
        //   1. 加锁 DCL
        //   2. 查 t_train_station_relation 表，找出 startRegion=北京 AND endRegion=杭州 的所有关系
        //   3. 对每条关系，通过 TrainDO 缓存/DB 获取车次详情
        //   4. 组装 TicketListDTO（不含票价和余票）写入 Redis
        // ═══════════════════════════════════════════════════════════════════════
        List<TicketListDTO> seatResults = new ArrayList<>();
        String buildRegionTrainStationHashKey = String.format(REGION_TRAIN_STATION, stationDetails.get(0), stationDetails.get(1));
        Map<Object, Object> regionTrainStationAllMap = stringRedisTemplate.opsForHash().entries(buildRegionTrainStationHashKey);
        if (MapUtil.isEmpty(regionTrainStationAllMap)) {
            RLock lock = redissonClient.getLock(LOCK_REGION_TRAIN_STATION);
            lock.lock();
            try {
                // --- DCL 第二次检查 ---
                regionTrainStationAllMap = stringRedisTemplate.opsForHash().entries(buildRegionTrainStationHashKey);
                if (MapUtil.isEmpty(regionTrainStationAllMap)) {
                    // --- 从 DB 查该地区对的所有列车关系 ---
                    LambdaQueryWrapper<TrainStationRelationDO> queryWrapper = Wrappers.lambdaQuery(TrainStationRelationDO.class)
                            .eq(TrainStationRelationDO::getStartRegion, stationDetails.get(0))
                            .eq(TrainStationRelationDO::getEndRegion, stationDetails.get(1));
                    List<TrainStationRelationDO> trainStationRelationList = trainStationRelationMapper.selectList(queryWrapper);

                    // --- 逐条组装车次基本信息 ---
                    for (TrainStationRelationDO each : trainStationRelationList) {
                        // safeGet: 封装了 get-if-null-then-load-put 逻辑，带过期时间
                        TrainDO trainDO = distributedCache.safeGet(
                                TRAIN_INFO + each.getTrainId(),
                                TrainDO.class,
                                () -> trainMapper.selectById(each.getTrainId()),
                                ADVANCE_TICKET_DAY,
                                TimeUnit.DAYS);
                        TicketListDTO result = new TicketListDTO();
                        result.setTrainId(String.valueOf(trainDO.getId()));
                        result.setTrainNumber(trainDO.getTrainNumber());                // 车次号，如 "G35"
                        result.setDepartureTime(convertDateToLocalTime(each.getDepartureTime(), "HH:mm")); // 出发时间 "09:56"
                        result.setArrivalTime(convertDateToLocalTime(each.getArrivalTime(), "HH:mm"));     // 到达时间 "15:14"
                        result.setDuration(DateUtil.calculateHourDifference(each.getDepartureTime(), each.getArrivalTime())); // 历时 "5时18分"
                        result.setDeparture(each.getDeparture());                        // 出发站名，如 "北京南"
                        result.setArrival(each.getArrival());                            // 到达站名，如 "杭州东"
                        result.setDepartureFlag(each.getDepartureFlag());                // 是否始发站
                        result.setArrivalFlag(each.getArrivalFlag());                    // 是否终点站
                        result.setTrainType(trainDO.getTrainType());                     // 列车类型 0-高铁 1-动车 2-普速
                        result.setTrainBrand(trainDO.getTrainBrand());                   // 列车品牌 0-GC 1-D 2-Z ...
                        if (StrUtil.isNotBlank(trainDO.getTrainTag())) {
                            result.setTrainTags(StrUtil.split(trainDO.getTrainTag(), ",")); // 标签: 复兴号/智能动车/静音车厢
                        }
                        long betweenDay = cn.hutool.core.date.DateUtil.betweenDay(each.getDepartureTime(), each.getArrivalTime(), false);
                        result.setDaysArrived((int) betweenDay);                         // 到达天数（跨天车次 > 0）
                        result.setSaleStatus(new Date().after(trainDO.getSaleTime()) ? 0 : 1);   // 是否已起售
                        result.setSaleTime(convertDateToLocalTime(trainDO.getSaleTime(), "MM-dd HH:mm"));

                        seatResults.add(result);
                        // --- 存入 regionTrainStationAllMap，用于写 Redis ---
                        regionTrainStationAllMap.put(
                                CacheUtil.buildKey(String.valueOf(each.getTrainId()), each.getDeparture(), each.getArrival()),
                                JSON.toJSONString(result));
                    }
                    // --- 批量写入 Redis Hash，后续请求直接命中 ---
                    stringRedisTemplate.opsForHash().putAll(buildRegionTrainStationHashKey, regionTrainStationAllMap);
                }
            } finally {
                lock.unlock();
            }
        }

        // --- 缓存命中时 seatResults 为空，从 regionTrainStationAllMap 反序列化 ---
        seatResults = CollUtil.isEmpty(seatResults)
                ? regionTrainStationAllMap.values().stream().map(each -> JSON.parseObject(each.toString(), TicketListDTO.class)).toList()
                : seatResults;

        // --- 按出发时间升序排列 ---
        seatResults = seatResults.stream().sorted(new TimeStringComparator()).toList();

        // ═══════════════════════════════════════════════════════════════════════
        // 阶段四 & 五：加载票价和余票（V1 的性能瓶颈所在）
        //
        // 对 seatResults 中每个车次，逐一执行：
        //   ① 查票价缓存（Redis String: train_station_price_{trainId}_{departure}_{arrival}）
        //     缓存 miss → 查 t_train_station_price 表，回种 Redis
        //   ② 对票价返回的每个座型，查余票缓存（Redis Hash: remaining_ticket_{trainId}_{departure}_{arrival}）
        //     缓存 miss → 触发 SeatMarginCacheLoader.load() 从 DB 全量加载回种
        //
        // 问题：如果返回 20 个车次、每个 3 种座型，最坏情况 = 20×3=60 次 Redis GET
        //      + N 次缓存 miss 触发的 DB 查询 + 锁竞争。每次都是同步阻塞 IO。
        // ═══════════════════════════════════════════════════════════════════════
        for (TicketListDTO each : seatResults) {

            // --- 阶段四：查票价。key = train_station_price_{trainId}_{departure}_{arrival} ---
            // safeGet 封装了 get-if-null-then-load-put 的 Cache-Aside 逻辑
            String trainStationPriceStr = distributedCache.safeGet(
                    String.format(TRAIN_STATION_PRICE, each.getTrainId(), each.getDeparture(), each.getArrival()),
                    String.class,
                    () -> {
                        // 缓存 miss：查 t_train_station_price 表
                        // 返回该列车在该区间所有座型的价格列表
                        LambdaQueryWrapper<TrainStationPriceDO> trainStationPriceQueryWrapper = Wrappers.lambdaQuery(TrainStationPriceDO.class)
                                .eq(TrainStationPriceDO::getDeparture, each.getDeparture())
                                .eq(TrainStationPriceDO::getArrival, each.getArrival())
                                .eq(TrainStationPriceDO::getTrainId, each.getTrainId());
                        return JSON.toJSONString(trainStationPriceMapper.selectList(trainStationPriceQueryWrapper));
                    },
                    ADVANCE_TICKET_DAY,
                    TimeUnit.DAYS
            );
            List<TrainStationPriceDO> trainStationPriceDOList = JSON.parseArray(trainStationPriceStr, TrainStationPriceDO.class);

            // --- 阶段五：查余票。对每个座型逐一查 Redis，miss 则走 DB ---
            List<SeatClassDTO> seatClassList = new ArrayList<>();
            trainStationPriceDOList.forEach(item -> {
                String seatType = String.valueOf(item.getSeatType());  // 座型编码: 0-商务座 1-一等座 2-二等座 ...

                // keySuffix = trainId_departure_arrival，与 SeatMarginCacheLoader 中保持一致
                String keySuffix = StrUtil.join("_", each.getTrainId(), item.getDeparture(), item.getArrival());
                // Redis Hash: remaining_ticket_{trainId}_{departure}_{arrival}
                //   field: seatType (0/1/2/...) → value: 余票数量字符串
                Object quantityObj = stringRedisTemplate.opsForHash()
                        .get(TRAIN_STATION_REMAINING_TICKET + keySuffix, seatType);

                int quantity = Optional.ofNullable(quantityObj)
                        .map(Object::toString)
                        .map(Integer::parseInt)
                        .orElseGet(() -> {
                            // --- 缓存 miss：触发 SeatMarginCacheLoader 全量加载回种 ---
                            // load() 内部会：加锁 → DCL → 查全路线区间 → 对每个区间查 DB count → 批量写 Redis
                            Map<String, String> seatMarginMap = seatMarginCacheLoader.load(
                                    String.valueOf(each.getTrainId()), seatType,
                                    item.getDeparture(), item.getArrival());
                            return Optional.ofNullable(seatMarginMap.get(String.valueOf(item.getSeatType())))
                                    .map(Integer::parseInt).orElse(0);
                        });

                // 组装座型信息：类型、余票数、价格（分转元，÷100）、是否为候补
                seatClassList.add(new SeatClassDTO(
                        item.getSeatType(),
                        quantity,
                        new BigDecimal(item.getPrice()).divide(new BigDecimal("100"), 1, RoundingMode.HALF_UP),
                        false));
            });
            each.setSeatClassList(seatClassList);
        }

        // ═══════════════════════════════════════════════════════════════════════
        // 阶段六：组装响应 — 主数据 + 筛选器选项
        //
        // trainList           → 表格数据（每个车次的完整信息）
        // departureStationList → "出发车站"筛选复选框的选项（从 trainList distinct）
        // arrivalStationList   → "到达车站"筛选复选框的选项
        // trainBrandList       → "车次类型"筛选复选框的选项
        // seatClassTypeList    → "车次席别"筛选复选框的选项
        // ═══════════════════════════════════════════════════════════════════════
        return TicketPageQueryRespDTO.builder()
                .trainList(seatResults)
                .departureStationList(buildDepartureStationList(seatResults))
                .arrivalStationList(buildArrivalStationList(seatResults))
                .trainBrandList(buildTrainBrandList(seatResults))
                .seatClassTypeList(buildSeatClassList(seatResults))
                .build();
    }

    /**
     * V2 版本车票查询（高性能版）：使用 Redis Pipeline 批量获取票价和余票信息
     * 核心优化：将 N 次独立的 Redis GET/HGET 合并为 2 次 Pipeline 批量操作，大幅减少网络 IO
     * 流程：站点映射 -> 区域列车缓存 -> Pipeline 批量获取票价 -> Pipeline 批量获取余票 -> 组装结果
     */
    @Override
    public TicketPageQueryRespDTO pageListTicketQueryV2(TicketPageQueryReqDTO requestParam) {
        // 责任链模式 验证城市名称是否存在、不存在加载缓存以及出发日期不能小于当前日期等等
        ticketPageQueryAbstractChainContext.handler(TicketChainMarkEnum.TRAIN_QUERY_FILTER.name(), requestParam);
        StringRedisTemplate stringRedisTemplate = (StringRedisTemplate) distributedCache.getInstance();
        // 列车查询逻辑较为复杂，详细解析文章查看 https://nageoffer.com/12306/question
        // v2 版本更符合企业级高并发真实场景解决方案，完美解决了 v1 版本性能深渊问题。通过 Jmeter 压测聚合报告得知，性能提升在 300% - 500%+
        // 其实还能有 v3 版本，性能估计在原基础上还能进一步提升一倍。不过 v3 版本太过于复杂，不易读且不易扩展，就不写具体的代码了。面试中 v2 版本已经够和面试官吹的了
        List<Object> stationDetails = stringRedisTemplate.opsForHash()
                .multiGet(REGION_TRAIN_STATION_MAPPING, Lists.newArrayList(requestParam.getFromStation(), requestParam.getToStation()));
        String buildRegionTrainStationHashKey = String.format(REGION_TRAIN_STATION, stationDetails.get(0), stationDetails.get(1));
        Map<Object, Object> regionTrainStationAllMap = stringRedisTemplate.opsForHash().entries(buildRegionTrainStationHashKey);
        List<TicketListDTO> seatResults = regionTrainStationAllMap.values().stream()
                .map(each -> JSON.parseObject(each.toString(), TicketListDTO.class))
                .sorted(new TimeStringComparator())
                .toList();
        List<String> trainStationPriceKeys = seatResults.stream()
                .map(each -> String.format(cacheRedisPrefix + TRAIN_STATION_PRICE, each.getTrainId(), each.getDeparture(), each.getArrival()))
                .toList();
        List<Object> trainStationPriceObjs = stringRedisTemplate.executePipelined((RedisCallback<String>) connection -> {
            trainStationPriceKeys.forEach(each -> connection.stringCommands().get(each.getBytes()));
            return null;
        });
        List<TrainStationPriceDO> trainStationPriceDOList = new ArrayList<>();
        List<String> trainStationRemainingKeyList = new ArrayList<>();
        for (Object each : trainStationPriceObjs) {
            List<TrainStationPriceDO> trainStationPriceList = JSON.parseArray(each.toString(), TrainStationPriceDO.class);
            trainStationPriceDOList.addAll(trainStationPriceList);
            for (TrainStationPriceDO item : trainStationPriceList) {
                String trainStationRemainingKey = cacheRedisPrefix + TRAIN_STATION_REMAINING_TICKET + StrUtil.join("_", item.getTrainId(), item.getDeparture(), item.getArrival());
                trainStationRemainingKeyList.add(trainStationRemainingKey);
            }
        }
        List<Object> trainStationRemainingObjs = stringRedisTemplate.executePipelined((RedisCallback<String>) connection -> {
            for (int i = 0; i < trainStationRemainingKeyList.size(); i++) {
                connection.hashCommands().hGet(trainStationRemainingKeyList.get(i).getBytes(), trainStationPriceDOList.get(i).getSeatType().toString().getBytes());
            }
            return null;
        });
        for (TicketListDTO each : seatResults) {
            List<Integer> seatTypesByCode = VehicleTypeEnum.findSeatTypesByCode(each.getTrainType());
            List<Object> remainingTicket = new ArrayList<>(trainStationRemainingObjs.subList(0, seatTypesByCode.size()));
            List<TrainStationPriceDO> trainStationPriceDOSub = new ArrayList<>(trainStationPriceDOList.subList(0, seatTypesByCode.size()));
            trainStationRemainingObjs.subList(0, seatTypesByCode.size()).clear();
            trainStationPriceDOList.subList(0, seatTypesByCode.size()).clear();
            List<SeatClassDTO> seatClassList = new ArrayList<>();
            for (int i = 0; i < trainStationPriceDOSub.size(); i++) {
                TrainStationPriceDO trainStationPriceDO = trainStationPriceDOSub.get(i);
                SeatClassDTO seatClassDTO = SeatClassDTO.builder()
                        .type(trainStationPriceDO.getSeatType())
                        .quantity(Integer.parseInt(remainingTicket.get(i).toString()))
                        .price(new BigDecimal(trainStationPriceDO.getPrice()).divide(new BigDecimal("100"), 1, RoundingMode.HALF_UP))
                        .candidate(false)
                        .build();
                seatClassList.add(seatClassDTO);
            }
            each.setSeatClassList(seatClassList);
        }
        return TicketPageQueryRespDTO.builder()
                .trainList(seatResults)
                .departureStationList(buildDepartureStationList(seatResults))
                .arrivalStationList(buildArrivalStationList(seatResults))
                .trainBrandList(buildTrainBrandList(seatResults))
                .seatClassTypeList(buildSeatClassList(seatResults))
                .build();
    }

    /**
     * V1 版本购票：使用单一 Redisson 分布式锁锁定整列车，确保同一列车同时只有一个购票请求执行
     * 优点：实现简单，能防止超卖
     * 缺点：锁粒度过大（列车级），不同座位类型的购票请求也被串行化，高并发下性能差
     */
    @ILog
    @Idempotent(
            uniqueKeyPrefix = "index12306-ticket:lock_purchase-tickets:",
            key = "T(org.opengoofy.index12306.framework.starter.bases.ApplicationContextHolder).getBean('environment').getProperty('unique-name', '')"
                    + "+'_'+"
                    + "T(org.opengoofy.index12306.frameworks.starter.user.core.UserContext).getUsername()",
            message = "正在执行下单流程，请稍后...",
            scene = IdempotentSceneEnum.RESTAPI,
            type = IdempotentTypeEnum.SPEL
    )
    @Override
    public TicketPurchaseRespDTO purchaseTicketsV1(PurchaseTicketReqDTO requestParam) {
        // 责任链模式，验证 1：参数必填 2：参数正确性 3：乘客是否已买当前车次等...
        purchaseTicketAbstractChainContext.handler(TicketChainMarkEnum.TRAIN_PURCHASE_TICKET_FILTER.name(), requestParam);
        // v1 版本购票存在 4 个较为严重的问题，v2 版本相比较 v1 版本更具有业务特点以及性能，整体提升较大
        // 写了详细的 v2 版本购票升级指南，详情查看：https://nageoffer.com/12306/question
        String lockKey = environment.resolvePlaceholders(String.format(LOCK_PURCHASE_TICKETS, requestParam.getTrainId()));
        RLock lock = redissonClient.getLock(lockKey);
        lock.lock();
        try {
            return ticketService.executePurchaseTickets(requestParam);
        } finally {
            lock.unlock();
        }
    }

    private final Cache<String, ReentrantLock> localLockMap = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.DAYS)
            .build();

    private final Cache<String, Object> tokenTicketsRefreshMap = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();

    /**
     * V2 版本购票（高性能版）：三层并发控制
     * 第一层 - 令牌桶预校验：通过 Redis Lua 脚本原子检查余票是否足够，提前拦截无效请求
     * 第二层 - 本地锁（Caffeine 缓存）：按 trainId + seatType 分组，JVM 内互斥，减少分布式锁竞争
     * 第三层 - 分布式公平锁：跨 JVM 互斥，保证集群环境下的数据一致性
     * 同时使用 @Idempotent 注解防止用户重复提交订单
     */
    @ILog
    @Idempotent(
            uniqueKeyPrefix = "index12306-ticket:lock_purchase-tickets:",
            key = "T(org.opengoofy.index12306.framework.starter.bases.ApplicationContextHolder).getBean('environment').getProperty('unique-name', '')"
                    + "+'_'+"
                    + "T(org.opengoofy.index12306.frameworks.starter.user.core.UserContext).getUsername()",
            message = "正在执行下单流程，请稍后...",
            scene = IdempotentSceneEnum.RESTAPI,
            type = IdempotentTypeEnum.SPEL
    )
    @Override
    public TicketPurchaseRespDTO purchaseTicketsV2(PurchaseTicketReqDTO requestParam) {
        // 责任链模式，验证 1：参数必填 2：参数正确性 3：乘客是否已买当前车次等...
        purchaseTicketAbstractChainContext.handler(TicketChainMarkEnum.TRAIN_PURCHASE_TICKET_FILTER.name(), requestParam);
        // 为什么需要令牌限流？余票缓存限流不可以么？详情查看：https://nageoffer.com/12306/question
        TokenResultDTO tokenResult = ticketAvailabilityTokenBucket.takeTokenFromBucket(requestParam);
        if (tokenResult.getTokenIsNull()) {
            Object ifPresentObj = tokenTicketsRefreshMap.getIfPresent(requestParam.getTrainId());
            if (ifPresentObj == null) {
                synchronized (TicketService.class) {
                    if (tokenTicketsRefreshMap.getIfPresent(requestParam.getTrainId()) == null) {
                        ifPresentObj = new Object();
                        tokenTicketsRefreshMap.put(requestParam.getTrainId(), ifPresentObj);
                        tokenIsNullRefreshToken(requestParam, tokenResult);
                    }
                }
            }
            throw new ServiceException("列车站点已无余票");
        }
        // v1 版本购票存在 4 个较为严重的问题，v2 版本相比较 v1 版本更具有业务特点以及性能，整体提升较大
        // 写了详细的 v2 版本购票升级指南，详情查看：https://nageoffer.com/12306/question
        List<ReentrantLock> localLockList = new ArrayList<>();
        List<RLock> distributedLockList = new ArrayList<>();
        Map<Integer, List<PurchaseTicketPassengerDetailDTO>> seatTypeMap = requestParam.getPassengers().stream()
                .collect(Collectors.groupingBy(PurchaseTicketPassengerDetailDTO::getSeatType));
        seatTypeMap.forEach((searType, count) -> {
            String lockKey = environment.resolvePlaceholders(String.format(LOCK_PURCHASE_TICKETS_V2, requestParam.getTrainId(), searType));
            ReentrantLock localLock = localLockMap.getIfPresent(lockKey);
            if (localLock == null) {
                synchronized (TicketService.class) {
                    if ((localLock = localLockMap.getIfPresent(lockKey)) == null) {
                        localLock = new ReentrantLock(true);
                        localLockMap.put(lockKey, localLock);
                    }
                }
            }
            localLockList.add(localLock);
            RLock distributedLock = redissonClient.getFairLock(lockKey);
            distributedLockList.add(distributedLock);
        });
        try {
            localLockList.forEach(ReentrantLock::lock);
            distributedLockList.forEach(RLock::lock);
            return ticketService.executePurchaseTickets(requestParam);
        } finally {
            localLockList.forEach(localLock -> {
                try {
                    localLock.unlock();
                } catch (Throwable ignored) {
                }
            });
            distributedLockList.forEach(distributedLock -> {
                try {
                    distributedLock.unlock();
                } catch (Throwable ignored) {
                }
            });
        }
    }

    /**
     * 购票核心事务执行逻辑：在 V1/V2 获取锁之后由代理调用（通过 self-injection 确保 @Transactional 生效）
     * 执行步骤：
     * 1. 根据列车类型选择对应的座位分配策略（商务座、一等座、二等座各有不同策略）
     * 2. 批量保存车票记录到数据库
     * 3. 远程调用订单服务创建订单（订单创建失败则回滚事务）
     * 注意：本方法必须通过 ApplicationContextHolder 获取的代理 Bean 调用，直接调用会导致 @Transactional 失效
     */
    @Override
    @Transactional(rollbackFor = Throwable.class)
    public TicketPurchaseRespDTO executePurchaseTickets(PurchaseTicketReqDTO requestParam) {
        List<TicketOrderDetailRespDTO> ticketOrderDetailResults = new ArrayList<>();
        String trainId = requestParam.getTrainId();
        // 节假日高并发购票Redis能扛得住么？详情查看：https://nageoffer.com/12306/question
        TrainDO trainDO = distributedCache.safeGet(
                TRAIN_INFO + trainId,
                TrainDO.class,
                () -> trainMapper.selectById(trainId),
                ADVANCE_TICKET_DAY,
                TimeUnit.DAYS);
        List<TrainPurchaseTicketRespDTO> trainPurchaseTicketResults = trainSeatTypeSelector.select(trainDO.getTrainType(), requestParam);
        List<TicketDO> ticketDOList = trainPurchaseTicketResults.stream()
                .map(each -> TicketDO.builder()
                        .username(UserContext.getUsername())
                        .trainId(Long.parseLong(requestParam.getTrainId()))
                        .carriageNumber(each.getCarriageNumber())
                        .seatNumber(each.getSeatNumber())
                        .passengerId(each.getPassengerId())
                        .ticketStatus(TicketStatusEnum.UNPAID.getCode())
                        .build())
                .toList();
        saveBatch(ticketDOList);
        Result<String> ticketOrderResult;
        try {
            List<TicketOrderItemCreateRemoteReqDTO> orderItemCreateRemoteReqDTOList = new ArrayList<>();
            trainPurchaseTicketResults.forEach(each -> {
                TicketOrderItemCreateRemoteReqDTO orderItemCreateRemoteReqDTO = TicketOrderItemCreateRemoteReqDTO.builder()
                        .amount(each.getAmount())
                        .carriageNumber(each.getCarriageNumber())
                        .seatNumber(each.getSeatNumber())
                        .idCard(each.getIdCard())
                        .idType(each.getIdType())
                        .phone(each.getPhone())
                        .seatType(each.getSeatType())
                        .ticketType(each.getUserType())
                        .realName(each.getRealName())
                        .build();
                TicketOrderDetailRespDTO ticketOrderDetailRespDTO = TicketOrderDetailRespDTO.builder()
                        .amount(each.getAmount())
                        .carriageNumber(each.getCarriageNumber())
                        .seatNumber(each.getSeatNumber())
                        .idCard(each.getIdCard())
                        .idType(each.getIdType())
                        .seatType(each.getSeatType())
                        .ticketType(each.getUserType())
                        .realName(each.getRealName())
                        .build();
                orderItemCreateRemoteReqDTOList.add(orderItemCreateRemoteReqDTO);
                ticketOrderDetailResults.add(ticketOrderDetailRespDTO);
            });
            LambdaQueryWrapper<TrainStationRelationDO> queryWrapper = Wrappers.lambdaQuery(TrainStationRelationDO.class)
                    .eq(TrainStationRelationDO::getTrainId, trainId)
                    .eq(TrainStationRelationDO::getDeparture, requestParam.getDeparture())
                    .eq(TrainStationRelationDO::getArrival, requestParam.getArrival());
            TrainStationRelationDO trainStationRelationDO = trainStationRelationMapper.selectOne(queryWrapper);
            TicketOrderCreateRemoteReqDTO orderCreateRemoteReqDTO = TicketOrderCreateRemoteReqDTO.builder()
                    .departure(requestParam.getDeparture())
                    .arrival(requestParam.getArrival())
                    .orderTime(new Date())
                    .source(SourceEnum.INTERNET.getCode())
                    .trainNumber(trainDO.getTrainNumber())
                    .departureTime(trainStationRelationDO.getDepartureTime())
                    .arrivalTime(trainStationRelationDO.getArrivalTime())
                    .ridingDate(trainStationRelationDO.getDepartureTime())
                    .userId(UserContext.getUserId())
                    .username(UserContext.getUsername())
                    .trainId(Long.parseLong(requestParam.getTrainId()))
                    .ticketOrderItems(orderItemCreateRemoteReqDTOList)
                    .build();
            ticketOrderResult = ticketOrderRemoteService.createTicketOrder(orderCreateRemoteReqDTO);
            if (!ticketOrderResult.isSuccess() || StrUtil.isBlank(ticketOrderResult.getData())) {
                log.error("订单服务调用失败，返回结果：{}", ticketOrderResult.getMessage());
                throw new ServiceException("订单服务调用失败");
            }
        } catch (Throwable ex) {
            log.error("远程调用订单服务创建错误，请求参数：{}", JSON.toJSONString(requestParam), ex);
            throw ex;
        }
        return new TicketPurchaseRespDTO(ticketOrderResult.getData(), ticketOrderDetailResults);
    }

    @Override
    public PayInfoRespDTO getPayInfo(String orderSn) {
        return payRemoteService.getPayInfo(orderSn).getData();
    }

    /**
     * 取消订单的补偿逻辑：解锁座位、回滚缓存余票、回滚令牌桶
     * 缓存更新策略：
     * - 非 binlog 模式：直接操作 Redis 缓存（DB + Cache 同步回滚）
     * - binlog 模式：缓存由 Canal binlog 同步自动更新，此处跳过直接缓存操作
     */
    @ILog
    @Override
    public void cancelTicketOrder(CancelTicketOrderReqDTO requestParam) {
        Result<Void> cancelOrderResult = ticketOrderRemoteService.cancelTicketOrder(requestParam);
        if (cancelOrderResult.isSuccess() && !StrUtil.equals(ticketAvailabilityCacheUpdateType, "binlog")) {
            Result<org.opengoofy.index12306.biz.ticketservice.remote.dto.TicketOrderDetailRespDTO> ticketOrderDetailResult = ticketOrderRemoteService.queryTicketOrderByOrderSn(requestParam.getOrderSn());
            org.opengoofy.index12306.biz.ticketservice.remote.dto.TicketOrderDetailRespDTO ticketOrderDetail = ticketOrderDetailResult.getData();
            String trainId = String.valueOf(ticketOrderDetail.getTrainId());
            String departure = ticketOrderDetail.getDeparture();
            String arrival = ticketOrderDetail.getArrival();
            List<TicketOrderPassengerDetailRespDTO> trainPurchaseTicketResults = ticketOrderDetail.getPassengerDetails();
            try {
                seatService.unlock(trainId, departure, arrival, BeanUtil.convert(trainPurchaseTicketResults, TrainPurchaseTicketRespDTO.class));
            } catch (Throwable ex) {
                log.error("[取消订单] 订单号：{} 回滚列车DB座位状态失败", requestParam.getOrderSn(), ex);
                throw ex;
            }
            ticketAvailabilityTokenBucket.rollbackInBucket(ticketOrderDetail);
            try {
                StringRedisTemplate stringRedisTemplate = (StringRedisTemplate) distributedCache.getInstance();
                Map<Integer, List<TicketOrderPassengerDetailRespDTO>> seatTypeMap = trainPurchaseTicketResults.stream()
                        .collect(Collectors.groupingBy(TicketOrderPassengerDetailRespDTO::getSeatType));
                List<RouteDTO> routeDTOList = trainStationService.listTakeoutTrainStationRoute(trainId, departure, arrival);
                routeDTOList.forEach(each -> {
                    String keySuffix = StrUtil.join("_", trainId, each.getStartStation(), each.getEndStation());
                    seatTypeMap.forEach((seatType, ticketOrderPassengerDetailRespDTOList) -> {
                        stringRedisTemplate.opsForHash()
                                .increment(TRAIN_STATION_REMAINING_TICKET + keySuffix, String.valueOf(seatType), ticketOrderPassengerDetailRespDTOList.size());
                    });
                });
            } catch (Throwable ex) {
                log.error("[取消关闭订单] 订单号：{} 回滚列车Cache余票失败", requestParam.getOrderSn(), ex);
                throw ex;
            }
        }
    }

    /**
     * 公共退款接口实现：全额退款 / 部分退款
     * 流程：责任链参数验证 -> 查询订单详情 -> 区分全额/部分退款 -> 计算退款金额 -> 调用 pay-service 执行退款
     * 部分退款：从订单中过滤出指定子订单的乘客明细，只退这些乘客的票
     * 全额退款：取整个订单下所有乘客明细执行退款
     * 注意：当前返回 null 是占位实现，后续需要根据退款结果构造完整的 RefundTicketRespDTO
     */
    @Override
    public RefundTicketRespDTO commonTicketRefund(RefundTicketReqDTO requestParam) {
        // 责任链模式，验证 1：参数必填
        refundReqDTOAbstractChainContext.handler(TicketChainMarkEnum.TRAIN_REFUND_TICKET_FILTER.name(), requestParam);
        Result<org.opengoofy.index12306.biz.ticketservice.remote.dto.TicketOrderDetailRespDTO> orderDetailRespDTOResult = ticketOrderRemoteService.queryTicketOrderByOrderSn(requestParam.getOrderSn());
        if (!orderDetailRespDTOResult.isSuccess() && Objects.isNull(orderDetailRespDTOResult.getData())) {
            throw new ServiceException("车票订单不存在");
        }
        org.opengoofy.index12306.biz.ticketservice.remote.dto.TicketOrderDetailRespDTO ticketOrderDetailRespDTO = orderDetailRespDTOResult.getData();
        List<TicketOrderPassengerDetailRespDTO> passengerDetails = ticketOrderDetailRespDTO.getPassengerDetails();
        if (CollectionUtil.isEmpty(passengerDetails)) {
            throw new ServiceException("车票子订单不存在");
        }
        RefundReqDTO refundReqDTO = new RefundReqDTO();
        if (RefundTypeEnum.PARTIAL_REFUND.getType().equals(requestParam.getType())) {
            TicketOrderItemQueryReqDTO ticketOrderItemQueryReqDTO = new TicketOrderItemQueryReqDTO();
            ticketOrderItemQueryReqDTO.setOrderSn(requestParam.getOrderSn());
            ticketOrderItemQueryReqDTO.setOrderItemRecordIds(requestParam.getSubOrderRecordIdReqList());
            Result<List<TicketOrderPassengerDetailRespDTO>> queryTicketItemOrderById = ticketOrderRemoteService.queryTicketItemOrderById(ticketOrderItemQueryReqDTO);
            List<TicketOrderPassengerDetailRespDTO> partialRefundPassengerDetails = passengerDetails.stream()
                    .filter(item -> queryTicketItemOrderById.getData().contains(item))
                    .collect(Collectors.toList());
            refundReqDTO.setRefundTypeEnum(RefundTypeEnum.PARTIAL_REFUND);
            refundReqDTO.setRefundDetailReqDTOList(partialRefundPassengerDetails);
        } else if (RefundTypeEnum.FULL_REFUND.getType().equals(requestParam.getType())) {
            refundReqDTO.setRefundTypeEnum(RefundTypeEnum.FULL_REFUND);
            refundReqDTO.setRefundDetailReqDTOList(passengerDetails);
        }
        if (CollectionUtil.isNotEmpty(passengerDetails)) {
            Integer partialRefundAmount = passengerDetails.stream()
                    .mapToInt(TicketOrderPassengerDetailRespDTO::getAmount)
                    .sum();
            refundReqDTO.setRefundAmount(partialRefundAmount);
        }
        refundReqDTO.setOrderSn(requestParam.getOrderSn());
        Result<RefundRespDTO> refundRespDTOResult = payRemoteService.commonRefund(refundReqDTO);
        if (!refundRespDTOResult.isSuccess() && Objects.isNull(refundRespDTOResult.getData())) {
            throw new ServiceException("车票订单退款失败");
        }
        return null; // 暂时返回空实体
    }

    private List<String> buildDepartureStationList(List<TicketListDTO> seatResults) {
        return seatResults.stream().map(TicketListDTO::getDeparture).distinct().collect(Collectors.toList());
    }

    private List<String> buildArrivalStationList(List<TicketListDTO> seatResults) {
        return seatResults.stream().map(TicketListDTO::getArrival).distinct().collect(Collectors.toList());
    }

    private List<Integer> buildSeatClassList(List<TicketListDTO> seatResults) {
        Set<Integer> resultSeatClassList = new HashSet<>();
        for (TicketListDTO each : seatResults) {
            for (SeatClassDTO item : each.getSeatClassList()) {
                resultSeatClassList.add(item.getType());
            }
        }
        return resultSeatClassList.stream().toList();
    }

    private List<Integer> buildTrainBrandList(List<TicketListDTO> seatResults) {
        Set<Integer> trainBrandSet = new HashSet<>();
        for (TicketListDTO each : seatResults) {
            if (StrUtil.isNotBlank(each.getTrainBrand())) {
                trainBrandSet.addAll(StrUtil.split(each.getTrainBrand(), ",").stream().map(Integer::parseInt).toList());
            }
        }
        return trainBrandSet.stream().toList();
    }

    private final ScheduledExecutorService tokenIsNullRefreshExecutor = Executors.newScheduledThreadPool(1);

    private void tokenIsNullRefreshToken(PurchaseTicketReqDTO requestParam, TokenResultDTO tokenResult) {
        RLock lock = redissonClient.getLock(String.format(LOCK_TOKEN_BUCKET_ISNULL, requestParam.getTrainId()));
        if (!lock.tryLock()) {
            return;
        }
        tokenIsNullRefreshExecutor.schedule(() -> {
            try {
                List<Integer> seatTypes = new ArrayList<>();
                Map<Integer, Integer> tokenCountMap = new HashMap<>();
                tokenResult.getTokenIsNullSeatTypeCounts().stream()
                        .map(each -> each.split("_"))
                        .forEach(split -> {
                            int seatType = Integer.parseInt(split[0]);
                            seatTypes.add(seatType);
                            tokenCountMap.put(seatType, Integer.parseInt(split[1]));
                        });
                List<SeatTypeCountDTO> seatTypeCountDTOList = seatService.listSeatTypeCount(Long.parseLong(requestParam.getTrainId()), requestParam.getDeparture(), requestParam.getArrival(), seatTypes);
                for (SeatTypeCountDTO each : seatTypeCountDTOList) {
                    Integer tokenCount = tokenCountMap.get(each.getSeatType());
                    if (tokenCount <= each.getSeatCount()) {
                        ticketAvailabilityTokenBucket.delTokenInBucket(requestParam);
                        break;
                    }
                }
            } finally {
                lock.unlock();
            }
        }, 10, TimeUnit.SECONDS);
    }

    /**
     * Spring Boot 启动后执行：通过 ApplicationContextHolder 获取 TicketService 的代理 Bean
     * 目的：解决 Spring AOP 自调用问题。本类内部直接调用 @Transactional 方法时 AOP 不生效，
     * 通过注入的代理 Bean 调用则可以触发事务、幂等、日志等 AOP 拦截
     */
    @Override
    public void run(String... args) throws Exception {
        ticketService = ApplicationContextHolder.getBean(TicketService.class);
    }
}
