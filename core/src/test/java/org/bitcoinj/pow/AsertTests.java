package org.bitcoinj.pow;

import org.bitcoinj.params.AbstractBitcoinNetParams;
import org.bitcoinj.script.Script;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.concurrent.Callable;

import static org.bitcoinj.core.Utils.HEX;
import static org.junit.Assert.assertEquals;

public class AsertTests {
    private enum HeightIncrType {
        INCR_BY_ONE,
        INCR_BY_288
    }

    private enum TimediffIncrType {
        INCR_BY_600S,
        INCR_BY_EXTRA_HALFLIFE
    }

    public long anchorHeight = 1;
    public long anchorTime = 0;
    public int anchorBits = 0;
    public long startHeight = 2;
    public long startTime = 1200;
    public int iterations = 225;
    public HeightIncrType heightIncrFunction;
    public TimediffIncrType timeDiffFunction;

    @Test
    public void run() throws Exception {
        this.initVariables(1, 0, 0x01010000, 2, 173400, 225, HeightIncrType.INCR_BY_ONE, TimediffIncrType.INCR_BY_EXTRA_HALFLIFE);
        /**
         * ##   anchor height: 1
         * ##   anchor ancestor time: 0
         * ##   anchor nBits: 0x1d00ffff
         * ##   start height: 2
         * ##   start time: 1200
         * ##   iterations: 10
         * # iteration,height,time,target
         * 1 2 1200 0x1d00ffff
         * 2 3 1800 0x1d00ffff
         * 3 4 2400 0x1d00ffff
         * 4 5 3000 0x1d00ffff
         * 5 6 3600 0x1d00ffff
         * 6 7 4200 0x1d00ffff
         * 7 8 4800 0x1d00ffff
         * 8 9 5400 0x1d00ffff
         * 9 10 6000 0x1d00ffff
         * 10 11 6600 0x1d00ffff
         */
        long h = this.startHeight;
        long t = this.startTime;
        for (int i = 1; i <= iterations; ++i) {
            BigInteger nextTarget;
            nextTarget = AbstractBitcoinNetParams.computeAsertTarget(anchorBits, anchorTime, anchorHeight, t, h);
            System.out.println(i + " " + h + " " + t + " " + nextTarget.toString(16));
            switch(this.heightIncrFunction) {
                case INCR_BY_ONE:
                    h += heightIncrByOne(h);
                    break;
                case INCR_BY_288:
                    h += heightIncrBy288(h);
                    break;
            }
            switch(this.timeDiffFunction) {
                case INCR_BY_600S:
                    t += timeIncrBy600s(t);
                    break;
                case INCR_BY_EXTRA_HALFLIFE:
                    t += timeIncrByExtraHalflife(t);
                    break;
            }
        }
    }

    private void initVariables(int anchorHeight, long anchorTime, int anchorBits, int startHeight, long startTime, int iterations, HeightIncrType heightIncrFunction, TimediffIncrType timeDiffFunction) {
        this.anchorHeight = anchorHeight;
        this.anchorTime = anchorTime;
        this.anchorBits = anchorBits;
        this.startHeight = startHeight;
        this.startTime = startTime;
        this.iterations = iterations;
        this.heightIncrFunction = heightIncrFunction;
        this.timeDiffFunction = timeDiffFunction;
    }

    long heightIncrByOne(long prev_height) {
        return 1;
    };

    long heightIncrBy288(long prev_height) {
        return 288;
    };

    long timeIncrBy600s(long iteration) {
        return 600;
    }

    long timeIncrByExtraHalflife(long iteration) {
        return 600 + 2*24*3600;
    }

}
