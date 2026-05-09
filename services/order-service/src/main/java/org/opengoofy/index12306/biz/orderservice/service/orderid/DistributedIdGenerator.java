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

/**
 * 全局唯一订单号生成器（雪花算法变种）
 * 算法参数：
 * - EPOCH：2021-01-01 00:00:00 UTC（1609459200000L），起点时间戳，延长 ID 可用年限
 * - NODE_BITS：5 位，最多支持 32 个节点
 * - SEQUENCE_BITS：7 位，每毫秒最多生成 128 个 ID
 * ID 结构：timestamp(ms) << 12 | nodeID << 7 | sequence
 * 公众号：马丁玩编程，回复：加群，添加马哥微信（备注：12306）获取项目资料
 */
public class DistributedIdGenerator {

    /** 起始时间戳：2021-01-01 00:00:00 UTC */
    private static final long EPOCH = 1609459200000L;
    /** 节点 ID 占用的位数：5位，支持 0-31 共 32 个节点 */
    private static final int NODE_BITS = 5;
    /** 序列号占用的位数：7位，每毫秒最多 128 个 ID */
    private static final int SEQUENCE_BITS = 7;

    private final long nodeID;
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public DistributedIdGenerator(long nodeID) {
        this.nodeID = nodeID;
    }

    /**
     * 生成全局唯一 ID（线程安全）
     * 同一毫秒内通过递增序列号区分；序列号用尽则等待下一毫秒
     * @throws RuntimeException 时钟回拨时抛出异常
     */
    public synchronized long generateId() {
        long timestamp = System.currentTimeMillis() - EPOCH;
        if (timestamp < lastTimestamp) {
            throw new RuntimeException("Clock moved backwards. Refusing to generate ID.");
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & ((1 << SEQUENCE_BITS) - 1);
            if (sequence == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return (timestamp << (NODE_BITS + SEQUENCE_BITS)) | (nodeID << SEQUENCE_BITS) | sequence;
    }

    private long tilNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis() - EPOCH;
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis() - EPOCH;
        }
        return timestamp;
    }
}
