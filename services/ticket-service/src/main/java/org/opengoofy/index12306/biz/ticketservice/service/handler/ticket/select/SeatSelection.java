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

package org.opengoofy.index12306.biz.ticketservice.service.handler.ticket.select;

import cn.hutool.core.collection.CollUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 座位选择器，提供两种座位选择算法
 * 公众号：马丁玩编程，回复：加群，添加马哥微信（备注：12306）获取项目资料
 */
public class SeatSelection {

    /**
     * 相邻座位选择算法：在二维座位矩阵中查找连续的 numSeats 个空位（值为0的格子）
     * 算法：逐行扫描，找到第一个有 numSeats 个连续空位的行，返回这些座位的行列坐标（从1开始）
     * 如找不到则返回 null
     *
     * @param numSeats   需要选择的座位数量
     * @param seatLayout 座位布局矩阵，0=空闲，1=已占用
     * @return 选中座位的行列坐标数组，每行是 [row, col]（从1开始）；找不到返回 null
     */
    public static int[][] adjacent(int numSeats, int[][] seatLayout) {
        int numRows = seatLayout.length;
        int numCols = seatLayout[0].length;
        List<int[]> selectedSeats = new ArrayList<>();
        for (int i = 0; i < numRows; i++) {
            for (int j = 0; j < numCols; j++) {
                if (seatLayout[i][j] == 0) {
                    int consecutiveSeats = 0;
                    for (int k = j; k < numCols; k++) {
                        if (seatLayout[i][k] == 0) {
                            consecutiveSeats++;
                            if (consecutiveSeats == numSeats) {
                                for (int l = k - numSeats + 1; l <= k; l++) {
                                    selectedSeats.add(new int[]{i, l});
                                }
                                break;
                            }
                        } else {
                            consecutiveSeats = 0;
                        }
                    }
                    if (!selectedSeats.isEmpty()) {
                        break;
                    }
                }
            }
            if (!selectedSeats.isEmpty()) {
                break;
            }
        }
        if (CollUtil.isEmpty(selectedSeats)) {
            return null;
        }
        int[][] actualSeat = new int[numSeats][2];
        int i = 0;
        for (int[] seat : selectedSeats) {
            int row = seat[0] + 1;
            int col = seat[1] + 1;
            actualSeat[i][0] = row;
            actualSeat[i][1] = col;
            i++;
        }
        return actualSeat;
    }

    /**
     * 非相邻座位选择算法：在二维座位矩阵中查找前 numSeats 个空位（不要求相邻）
     * 算法：逐行逐列扫描，按顺序收集空闲座位，直到选够 numSeats 个
     *
     * @param numSeats   需要选择的座位数量
     * @param seatLayout 座位布局矩阵，0=空闲，1=已占用
     * @return 选中座位的行列坐标数组，每行是 [row, col]（从1开始）
     */
    public static int[][] nonAdjacent(int numSeats, int[][] seatLayout) {
        int numRows = seatLayout.length;
        int numCols = seatLayout[0].length;
        List<int[]> selectedSeats = new ArrayList<>();
        for (int i = 0; i < numRows; i++) {
            for (int j = 0; j < numCols; j++) {
                if (seatLayout[i][j] == 0) {
                    selectedSeats.add(new int[]{i, j});
                    if (selectedSeats.size() == numSeats) {
                        break;
                    }
                }
            }
            if (selectedSeats.size() == numSeats) {
                break;
            }
        }
        return convertToActualSeat(selectedSeats);
    }

    private static int[][] convertToActualSeat(List<int[]> selectedSeats) {
        int[][] actualSeat = new int[selectedSeats.size()][2];
        for (int i = 0; i < selectedSeats.size(); i++) {
            int[] seat = selectedSeats.get(i);
            int row = seat[0] + 1;
            int col = seat[1] + 1;
            actualSeat[i][0] = row;
            actualSeat[i][1] = col;
        }
        return actualSeat;
    }

    public static void main(String[] args) {
        int[][] seatLayout = {
                {1, 1, 1, 1},
                {1, 1, 1, 0},
                {1, 1, 1, 0},
                {0, 0, 0, 0}
        };
        int[][] select = adjacent(2, seatLayout);
        System.out.println("成功预订相邻座位，座位位置为：");
        assert select != null;
        for (int[] ints : select) {
            System.out.printf("第 %d 排，第 %d 列%n", ints[0], ints[1]);
        }

        int[][] seatLayoutTwo = {
                {1, 0, 1, 1},
                {1, 1, 0, 0},
                {1, 1, 1, 0},
                {0, 0, 0, 0}
        };
        int[][] selectTwo = nonAdjacent(3, seatLayoutTwo);
        System.out.println("成功预订不相邻座位，座位位置为：");
        for (int[] ints : selectTwo) {
            System.out.printf("第 %d 排，第 %d 列%n", ints[0], ints[1]);
        }
    }
}
