/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2017 Jelurida IP B.V.
 * Copyright © 2018 metro.software
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida B.V.,
 * no part of the Nxt software, including this file, may be copied, modified,
 * propagated, or distributed except according to the terms contained in the
 * LICENSE.txt file.
 *
 * Removal or modification of this copyright notice is prohibited.
 *
 */

package metro.util;

import java.util.concurrent.atomic.AtomicLong;

public interface Time {

    long getTime();

    final class EpochTime implements Time {

        public long getTime() {
            return Convert.toEpochTime(System.currentTimeMillis());
        }

    }

    final class ConstantTime implements Time {

        private final long time;

        public ConstantTime(long time) {
            this.time = time;
        }

        public long getTime() {
            return time;
        }

    }

    final class FasterTime implements Time {

        private final int multiplier;
        private final long systemStartTime;
        private final long time;

        public FasterTime(long time, int multiplier) {
            if (multiplier > 1000 || multiplier <= 0) {
                throw new IllegalArgumentException("Time multiplier must be between 1 and 1000");
            }
            this.multiplier = multiplier;
            this.time = time;
            this.systemStartTime = System.currentTimeMillis();
        }

        public long getTime() {
            return time + (int)((System.currentTimeMillis() - systemStartTime) / (1000 / multiplier));
        }

    }

    final class CounterTime implements Time {

        private final AtomicLong counter;

        public CounterTime(long time) {
            this.counter = new AtomicLong(time);
        }

        public long getTime() {
            return counter.incrementAndGet();
        }

    }

}
