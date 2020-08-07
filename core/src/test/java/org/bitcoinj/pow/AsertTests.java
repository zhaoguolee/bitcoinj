package org.bitcoinj.pow;

import org.bitcoinj.params.AbstractBitcoinNetParams;
import org.bitcoinj.script.Script;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;

import static org.bitcoinj.core.Utils.HEX;
import static org.junit.Assert.assertEquals;

public class AsertTests {
    @Test
    public void run01() throws Exception {
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
        int anchorHeight = 1;
        long anchorTime = 0;
        int anchorBits = 0x1d00ffff;
        int startHeight = 2;
        int startTime = 1200;
        int iterations = 10;
        BigInteger expectedValue = BigInteger.valueOf(0x1d00ffffL);
        for (int i = 0 ; i < iterations; ++i) {
            int evalHeight = startHeight + i;
            long evalTime = evalHeight * 600;
            BigInteger result = AbstractBitcoinNetParams.computeAsertTarget(anchorBits, anchorTime, anchorHeight, evalTime, evalHeight);
            System.out.println(i + " " + evalHeight + " " + evalTime + " " + result.toString(16));
            Assert.assertEquals(expectedValue, result);
        }
    }
}
