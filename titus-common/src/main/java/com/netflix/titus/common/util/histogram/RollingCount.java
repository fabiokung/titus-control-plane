/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.titus.common.util.histogram;

import java.util.Arrays;

import com.google.common.base.Preconditions;

/**
 * Counts number of items in a rolling time window.
 */
public class RollingCount {

    private final int steps;
    private final long stepTime;

    private final long[] buckets;
    private int bucketStart;

    private long startTime;
    private long endTime;
    private long now;

    public RollingCount(long stepTime, int steps, long startTime) {
        this.steps = steps;
        this.stepTime = stepTime;
        this.buckets = new long[steps];
        this.startTime = startTime;
        this.endTime = startTime + steps * stepTime;
        this.now = startTime;
    }

    public long getCounts(long now) {
        Preconditions.checkState(now >= this.now, "Tried to check rolling count state in the past");
        add(0, now);
        return buckets[posAt(now)];
    }

    public long addOne(long now) {
        return add(1, now);
    }

    public long add(long counter, long now) {
        Preconditions.checkState(now >= this.now, "Tried to add rolling count item in the past");

        if (now >= endTime) {
            adjust(now);
        }

        int insertionPoint = (int) ((now - startTime) / stepTime);
        for (int i = insertionPoint; i < steps; i++) {
            buckets[(bucketStart + i) % steps] += counter;
        }
        this.now = now;

        return buckets[posAt(now)];
    }

    private void adjust(long now) {
        int shift = (int) ((now - endTime) / stepTime + 1);

        if (shift >= steps) {
            Arrays.fill(buckets, 0);
            this.bucketStart = 0;
        } else {
            long correction = buckets[(bucketStart + shift - 1) % steps];
            for (int i = shift; i < steps; i++) {
                buckets[(bucketStart + i) % steps] -= correction;
            }
            long total = buckets[(bucketStart + steps - 1) % steps];
            for (int i = 0; i < shift; i++) {
                buckets[(bucketStart + i) % steps] = total;
            }

            this.bucketStart = (bucketStart + shift) % steps;
        }

        this.startTime = startTime + shift * stepTime;
        this.endTime = startTime + steps * stepTime;
        this.now = now;
    }

    private int posAt(long now) {
        int stepIdx = (int) ((now - startTime) / stepTime);
        return (bucketStart + stepIdx) % steps;
    }
}
