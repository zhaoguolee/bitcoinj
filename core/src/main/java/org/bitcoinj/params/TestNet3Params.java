/*
 * Copyright 2013 Google Inc.
 * Copyright 2014 Andreas Schildbach
 * Copyright 2018 the bitcoinj-cash developers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This file has been modified by the bitcoinj-cash developers for the bitcoinj-cash project.
 * The original file was from the bitcoinj project (https://github.com/bitcoinj/bitcoinj).
 */

package org.bitcoinj.params;

import java.math.BigInteger;

import org.bitcoinj.core.*;

import java.util.Date;

import com.google.common.base.Preconditions;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.Utils;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;

import com.google.common.base.Preconditions;
import java.math.BigInteger;
import java.util.Date;

import static com.google.common.base.Preconditions.checkState;

/**
 * Parameters for the testnet, a separate public instance of Bitcoin that has relaxed rules suitable for development
 * and testing of applications and new Bitcoin versions.
 */
public class TestNet3Params extends AbstractBitcoinNetParams {
    public TestNet3Params() {
        super();
        id = ID_TESTNET;
        // Genesis hash is 000000000933ea01ad0ee984209779baaec3ced90fa3f408719526f8d77f4943
        packetMagic = 0xf4e5f3f4L;
        interval = INTERVAL;
        targetTimespan = TARGET_TIMESPAN;
        maxTarget = Utils.decodeCompactBits(0x1d00ffffL);
        port = 18333;
        addressHeader = 111;
        p2shHeader = 196;
        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };
        dumpedPrivateKeyHeader = 239;
        genesisBlock.setTime(1296688602L);
        genesisBlock.setDifficultyTarget(0x1d00ffffL);
        genesisBlock.setNonce(414098458);
        spendableCoinbaseDepth = 100;
        subsidyDecreaseBlockCount = 210000;
        String genesisHash = genesisBlock.getHashAsString();
        checkState(genesisHash.equals("000000000933ea01ad0ee984209779baaec3ced90fa3f408719526f8d77f4943"));
        alertSigningKey = Utils.HEX.decode("04302390343f91cc401d56d68b123028bf52e5fca1939df127f63c6467cdf9c8e2c14b61104cf817d0b780da337893ecc4aaff1309e536162dabbdb45200ca2b0a");

        checkpoints.put(546, Sha256Hash.wrap("000000002a936ca763904c3c35fce2f3556c559c0214345d31b1bcebf76acb70"));
        checkpoints.put(1155876, Sha256Hash.wrap("00000000000e38fef93ed9582a7df43815d5c2ba9fd37ef70c9a0ea4a285b8f5")); //August 1, 2017
        checkpoints.put(1188697, Sha256Hash.wrap("0000000000170ed0918077bde7b4d36cc4c91be69fa09211f748240dabe047fb")); //November 13, 2017
        checkpoints.put(1233070, Sha256Hash.wrap("0000000000000253c6201a2076663cfe4722e4c75f537552cc4ce989d15f7cd5")); //May 15, 2018
        checkpoints.put(1267996, Sha256Hash.wrap("00000000000001fae0095cd4bea16f1ce8ab63f3f660a03c6d8171485f484b24")); //November 15, 2018
        checkpoints.put(1303885, Sha256Hash.wrap("00000000000000479138892ef0e4fa478ccc938fb94df862ef5bde7e8dee23d3")); //May 15, 2019

        dnsSeeds = new String[] {
               "testnet-seed.bitcoinabc.org",
                "testnet-seed-abc.bitcoinforks.org",
                "testnet-seed.bitprim.org",
                "testnet-seed.deadalnix.me"
        };
        addrSeeds = null;
        bip32HeaderPub = 0x043587CF;
        bip32HeaderPriv = 0x04358394;

        majorityEnforceBlockUpgrade = TestNet2Params.TESTNET_MAJORITY_ENFORCE_BLOCK_UPGRADE;
        majorityRejectBlockOutdated = TestNet2Params.TESTNET_MAJORITY_REJECT_BLOCK_OUTDATED;
        majorityWindow = TestNet2Params.TESTNET_MAJORITY_WINDOW;

        // Aug, 1 hard fork
        uahfHeight = 1155876;
        // Nov, 13 hard fork
        daaUpdateHeight = 1188697;
        cashAddrPrefix = "bchtest";
        simpleledgerPrefix = "slptest";
    }

    private static TestNet3Params instance;
    public static synchronized TestNet3Params get() {
        if (instance == null) {
            instance = new TestNet3Params();
        }
        return instance;
    }

    @Override
    public String getPaymentProtocolId() {
        return PAYMENT_PROTOCOL_ID_TESTNET;
    }

    // February 16th 2012
    private static final Date testnetDiffDate = new Date(1329264000000L);

    public static boolean isValidTestnetDateBlock(Block block){
        return block.getTime().after(testnetDiffDate);
    }

}
