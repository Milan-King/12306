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

package org.opengoofy.index12306.biz.orderservice.service.orderid;

import lombok.RequiredArgsConstructor;
import org.opengoofy.index12306.framework.starter.cache.DistributedCache;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 订单 ID 全局唯一生成器管理
 * 公众号：马丁玩编程，回复：加群，添加马哥微信（备注：12306）获取项目资料
 */
@Component
@RequiredArgsConstructor
public final class OrderIdGeneratorManager implements InitializingBean {

    private final RedissonClient redissonClient;
    private final DistributedCache distributedCache;
    private static DistributedIdGenerator DISTRIBUTED_ID_GENERATOR;

    /**
     * 生成订单全局唯一 ID（基因法）
     * 格式：分布式雪花ID + userId % 1000000（后6位取模）
     * 基因法目的：将用户 ID 的后缀融入订单号，使同一用户的订单 hash 到同一个数据库分片
     * ShardingSphere 可以通过订单号的后6位定位到目标数据库和表，无需每次都传 userId
     *
     * @param userId 用户 ID
     * @return 订单号（雪花ID + 用户ID取模后缀）
     */
    public static String generateId(long userId) {
        return DISTRIBUTED_ID_GENERATOR.generateId() + String.valueOf(userId % 1000000);
    }

    /**
     * Spring Bean 初始化：通过 Redis 分布式锁 + Redis 原子递增获取唯一的 nodeId（工作机器标识）
     * nodeId 范围：0-31（最多 32 个服务实例），超过最大值自动回卷到 0
     * 获取到 nodeId 后创建单例 DistributedIdGenerator
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        String LOCK_KEY = "distributed_id_generator_lock_key";
        RLock lock = redissonClient.getLock(LOCK_KEY);
        lock.lock();
        try {
            StringRedisTemplate instance = (StringRedisTemplate) distributedCache.getInstance();
            String DISTRIBUTED_ID_GENERATOR_KEY = "distributed_id_generator_config";
            long incremented = Optional.ofNullable(instance.opsForValue().increment(DISTRIBUTED_ID_GENERATOR_KEY)).orElse(0L);
            // 注意：这里只是提供一种分库分表基因法的实现思路，所以将标识位定义 32。其次，如果对比 TB 网站订单号，应该不是在应用内生成，而是有一个全局服务调用获取
            int NODE_MAX = 32;
            if (incremented > NODE_MAX) {
                incremented = 0;
                instance.opsForValue().set(DISTRIBUTED_ID_GENERATOR_KEY, "0");
            }
            DISTRIBUTED_ID_GENERATOR = new DistributedIdGenerator(incremented);
        } finally {
            lock.unlock();
        }
    }
}
