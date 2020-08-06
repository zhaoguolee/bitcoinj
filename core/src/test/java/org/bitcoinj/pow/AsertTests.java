package org.bitcoinj.pow;

import org.bitcoinj.params.AbstractBitcoinNetParams;
import org.bitcoinj.script.Script;
import org.junit.Test;

import java.math.BigInteger;

import static org.bitcoinj.core.Utils.HEX;
import static org.junit.Assert.assertEquals;

public class AsertTests {
    @Test
    public void run01() throws Exception {
        int anchorHeight = 1;
        long anchorTime = 0;
        int anchorBits = 0x1a2b3c4d;
        int startHeight = 2;
        int iterations = startHeight + 10;
        for(int x = startHeight; x < iterations; x++) {
            int evalHeight = x;
            long evalTime = evalHeight * 600;
            BigInteger result = AbstractBitcoinNetParams.computeAsertTarget(anchorBits, anchorTime, anchorHeight, evalTime, evalHeight);
            int iteration = x - 1;
            System.out.println(iteration + " " + evalHeight + " " + evalTime + " " + result.toString(16));
        }
    }
}
