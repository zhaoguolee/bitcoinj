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

    public BigInteger anchorHeight = BigInteger.ZERO;
    public BigInteger anchorTime = BigInteger.ZERO;
    public int anchorBits = 0;
    public BigInteger startHeight = BigInteger.ZERO;
    public BigInteger startTime = BigInteger.ZERO;
    public int iterations = 225;
    public HeightIncrType heightIncrFunction;
    public TimediffIncrType timeDiffFunction;

    @Test
    public void run() throws Exception {
        this.initVariables(BigInteger.valueOf(1), BigInteger.valueOf(0), 0x01010000, BigInteger.valueOf(2), BigInteger.valueOf(174000), 225, HeightIncrType.INCR_BY_ONE, TimediffIncrType.INCR_BY_EXTRA_HALFLIFE);
        BigInteger h = this.startHeight;
        BigInteger t = this.startTime;
        for (int i = 1; i <= iterations; ++i) {
            BigInteger nextTarget;
            nextTarget = AbstractBitcoinNetParams.computeAsertTarget(anchorBits, anchorTime, anchorHeight, t, h);
            System.out.println(i + " " + h + " " + t + " " + nextTarget.toString(16));
            switch(this.heightIncrFunction) {
                case INCR_BY_ONE:
                    h = h.add(BigInteger.ONE);
                    break;
                case INCR_BY_288:
                    h = h.add(BigInteger.valueOf(288));
                    break;
            }
            switch(this.timeDiffFunction) {
                case INCR_BY_300S:
                    t = t.add(BigInteger.valueOf(300));
                    break;
                case INCR_BY_600S:
                    t = t.add(BigInteger.valueOf(600));
                    break;
                case INCR_BY_900S:
                    t = t.add(BigInteger.valueOf(900));
                    break;
                case INCR_BY_EXTRA_HALFLIFE:
                    t = t.add(timeIncrByExtraHalflife());
                    break;
            }
        }
    }

    private void initVariables(BigInteger anchorHeight, BigInteger anchorTime, int anchorBits, BigInteger startHeight, BigInteger startTime, int iterations, HeightIncrType heightIncrFunction, TimediffIncrType timeDiffFunction) {
        this.anchorHeight = anchorHeight;
        this.anchorTime = anchorTime;
        this.anchorBits = anchorBits;
        this.startHeight = startHeight;
        this.startTime = startTime;
        this.iterations = iterations;
        this.heightIncrFunction = heightIncrFunction;
        this.timeDiffFunction = timeDiffFunction;
    }

    private BigInteger timeIncrByExtraHalflife() {
        return BigInteger.valueOf(600 + 2*24*3600);
    }

}
