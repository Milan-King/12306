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

package org.opengoofy.index12306.biz.orderservice.dao.algorithm;

import cn.hutool.core.collection.CollUtil;
import com.google.common.base.Preconditions;
import lombok.Getter;
import org.apache.shardingsphere.sharding.api.sharding.complex.ComplexKeysShardingAlgorithm;
import org.apache.shardingsphere.sharding.api.sharding.complex.ComplexKeysShardingValue;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;

/**
 * 订单表级复合分片算法（ShardingSphere）
 * 分片策略：取 user_id 或 order_sn 的后6位进行 hash，再基于分表数量取模确定目标表
 * 表级路由公式：hash(后缀) % shardingCount
 * - shardingCount：每个库的表数，如 8
 * 生成的目标表名如：t_order_0, t_order_1 ...
 * 优先使用 user_id 作为分片键，若不存在则回退使用 order_sn
 * 公众号：马丁玩编程，回复：加群，添加马哥微信（备注：12306）获取项目资料
 */
public class OrderCommonTableComplexAlgorithm implements ComplexKeysShardingAlgorithm {

    @Getter
    private Properties props;

    private int shardingCount;

    private static final String SHARDING_COUNT_KEY = "sharding-count";

    /**
     * 根据分片值计算目标表名（如 t_order_0）
     * 优先使用 user_id 作为分片键，若不存在则使用 order_sn 作为分片键
     * 算法：取后6位 -> hash -> 绝对值 -> mod(分表数)
     */
    @Override
    public Collection<String> doSharding(Collection availableTargetNames, ComplexKeysShardingValue shardingValue) {
        Map<String, Collection<Comparable<?>>> columnNameAndShardingValuesMap = shardingValue.getColumnNameAndShardingValuesMap();
        Collection<String> result = new LinkedHashSet<>(availableTargetNames.size());
        if (CollUtil.isNotEmpty(columnNameAndShardingValuesMap)) {
            String userId = "user_id";
            Collection<Comparable<?>> customerUserIdCollection = columnNameAndShardingValuesMap.get(userId);
            if (CollUtil.isNotEmpty(customerUserIdCollection)) {
                Comparable<?> comparable = customerUserIdCollection.stream().findFirst().get();
                if (comparable instanceof String) {
                    String actualUserId = comparable.toString();
                    result.add(shardingValue.getLogicTableName() + "_" + hashShardingValue(actualUserId.substring(Math.max(actualUserId.length() - 6, 0))) % shardingCount);
                } else {
                    String dbSuffix = String.valueOf(hashShardingValue((Long) comparable % 1000000) % shardingCount);
                    result.add(shardingValue.getLogicTableName() + "_" + dbSuffix);
                }
            } else {
                String orderSn = "order_sn";
                Collection<Comparable<?>> orderSnCollection = columnNameAndShardingValuesMap.get(orderSn);
                Comparable<?> comparable = orderSnCollection.stream().findFirst().get();
                if (comparable instanceof String) {
                    String actualOrderSn = comparable.toString();
                    result.add(shardingValue.getLogicTableName() + "_" + hashShardingValue(actualOrderSn.substring(Math.max(actualOrderSn.length() - 6, 0))) % shardingCount);
                } else {
                    String dbSuffix = String.valueOf(hashShardingValue((Long) comparable % 1000000) % shardingCount);
                    result.add(shardingValue.getLogicTableName() + "_" + dbSuffix);
                }
            }
        }
        return result;
    }

    @Override
    public void init(Properties props) {
        this.props = props;
        shardingCount = getShardingCount(props);
    }

    private int getShardingCount(final Properties props) {
        Preconditions.checkArgument(props.containsKey(SHARDING_COUNT_KEY), "Sharding count cannot be null.");
        return Integer.parseInt(props.getProperty(SHARDING_COUNT_KEY));
    }

    private long hashShardingValue(final Comparable<?> shardingValue) {
        return Math.abs((long) shardingValue.hashCode());
    }
}
