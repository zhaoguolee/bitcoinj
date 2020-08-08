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
        INCR_BY_300S,
        INCR_BY_600S,
        INCR_BY_900S,
        INCR_BY_EXTRA_HALFLIFE,
        NONE
    }

    public long anchorHeight = 1;
    public long anchorTime = 0;
    public int anchorBits = 0;
    public long startHeight = 2;
    public long startTime = 0;
    public int iterations = 225;
    public HeightIncrType heightIncrFunction;
    public TimediffIncrType timeDiffFunction;

    @Test
    public void run() throws Exception {
        //todo convert all longs into bigintegers. this run is run10, which fails.
        this.initVariables(9223372036854775802L, 2147483047L, 0x1802aee8, 9223372036854775803L, 2147484547L, 10, HeightIncrType.INCR_BY_ONE, TimediffIncrType.INCR_BY_900S);
        long h = this.startHeight;
        long t = this.startTime;
        for (int i = 1; i <= iterations; ++i) {
            BigInteger nextTarget;
            nextTarget = AbstractBitcoinNetParams.computeAsertTarget(anchorBits, anchorTime, anchorHeight, t, h);
            System.out.println(i + " " + h + " " + t + " " + nextTarget.toString(16));
            switch(this.heightIncrFunction) {
                case INCR_BY_ONE:
                    h += heightIncrByOne();
                    break;
                case INCR_BY_288:
                    h += heightIncrBy288();
                    break;
            }
            switch(this.timeDiffFunction) {
                case INCR_BY_300S:
                    t += timeIncrBy300s();
                    break;
                case INCR_BY_600S:
                    t += timeIncrBy600s();
                    break;
                case INCR_BY_900S:
                    t += timeIncrBy900s();
                    break;
                case INCR_BY_EXTRA_HALFLIFE:
                    t += timeIncrByExtraHalflife();
                    break;
            }
        }
    }

    private void initVariables(long anchorHeight, long anchorTime, int anchorBits, long startHeight, long startTime, int iterations, HeightIncrType heightIncrFunction, TimediffIncrType timeDiffFunction) {
        this.anchorHeight = anchorHeight;
        this.anchorTime = anchorTime;
        this.anchorBits = anchorBits;
        this.startHeight = startHeight;
        this.startTime = startTime;
        this.iterations = iterations;
        this.heightIncrFunction = heightIncrFunction;
        this.timeDiffFunction = timeDiffFunction;
    }

    long heightIncrByOne() {
        return 1;
    };

    long heightIncrBy288() {
        return 288;
    };

    long timeIncrBy300s() {
        return 300;
    }

    long timeIncrBy600s() {
        return 600;
    }

    long timeIncrBy900s() {
        return 900;
    }

    long timeIncrByExtraHalflife() {
        return 600 + 2*24*3600;
    }

}
