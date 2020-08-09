/*
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
 */

package org.bitcoinj.pow.rule;

import com.google.common.base.Preconditions;
import org.bitcoinj.core.*;
import org.bitcoinj.params.AbstractBitcoinNetParams;
import org.bitcoinj.pow.AbstractPowRulesChecker;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;

import java.math.BigInteger;

/**
 * The new DAA algorithm seeks to accomplish the following objectives:
 * - Adjust difficulty to hash rate to target a mean block interval of 600 seconds.
 * - Avoid sudden changes in difficulty when hash rate is fairly stable.
 * - Adjust difficulty rapidly when hash rate changes rapidly.
 * - Avoid oscillations from feedback between hash rate and difficulty.
 * - Be resilient to attacks such as timestamp manipulation.
 * <p>
 * https://www.bitcoinabc.org/november
 */
public class AsertDifficultyRuleChecker extends AbstractPowRulesChecker {

    public AsertDifficultyRuleChecker(NetworkParameters networkParameters) {
        super(networkParameters);
    }

    @Override
    public void checkRules(StoredBlock storedPrev, Block nextBlock, BlockStore blockStore, AbstractBlockChain blockChain) throws VerificationException, BlockStoreException {
        checkNextCashWorkRequired(storedPrev, nextBlock, blockStore);
    }

    private void checkNextCashWorkRequired(StoredBlock storedPrev, Block nextBlock, BlockStore blockStore) {
        int prevHeight = storedPrev.getHeight();
        Preconditions.checkState(prevHeight >= networkParameters.getInterval());

        try {
            StoredBlock asertReferenceBlock = getAsertReferenceBlock(storedPrev, blockStore);
            StoredBlock last = GetMostSuitableBlock(storedPrev, blockStore);
            BigInteger evalBlockTime = BigInteger.valueOf(last.getHeader().getTimeSeconds());
            BigInteger evalBlockHeight = BigInteger.valueOf(last.getHeight());
            BigInteger referenceBlockBitsBigInteger = BigInteger.valueOf(Utils.encodeCompactBits(asertReferenceBlock.getHeader().getDifficultyTargetAsInteger()));
            int referenceBlockBits = referenceBlockBitsBigInteger.intValue();
            BigInteger referenceBlockTime = BigInteger.valueOf(asertReferenceBlock.getHeader().getTimeSeconds());
            BigInteger referenceBlockHeight = BigInteger.valueOf(asertReferenceBlock.getHeight());
            BigInteger nextTarget = AbstractBitcoinNetParams.computeAsertTarget(referenceBlockBits, referenceBlockTime, referenceBlockHeight, evalBlockTime, evalBlockHeight);
            networkParameters.verifyAsertDifficulty(nextTarget, nextBlock);
        } catch (BlockStoreException x) {
            // We don't have enough blocks, yet
        }
    }

    private StoredBlock getAsertReferenceBlock(StoredBlock storedPrev, BlockStore blockStore) throws BlockStoreException {
        StoredBlock bestAsertCandidate = storedPrev;
        StoredBlock prev = storedPrev;
        while(true) {
            if(isAsertMtp(prev, blockStore, networkParameters)) {
                bestAsertCandidate = prev;
                prev = prev.getPrev(blockStore);
            } else if(!isAsertMtp(prev, blockStore, networkParameters)) {
                return bestAsertCandidate;
            }
        }
    }

    private boolean isAsertMtp(StoredBlock storedPrev, BlockStore blockStore, NetworkParameters parameters) {
        try {
            long mtp = BlockChain.getMedianTimestampOfRecentBlocks(storedPrev, blockStore);
            return mtp >= parameters.getAsertUpdateTime();
        } catch (BlockStoreException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * To reduce the impact of timestamp manipulation, we select the block we are
     * basing our computation on via a median of 3.
     */
    private StoredBlock GetMostSuitableBlock(StoredBlock pindex, BlockStore blockStore) throws BlockStoreException {
        /**
         * In order to avoid a block is a very skewed timestamp to have too much
         * influence, we select the median of the 3 top most blocks as a starting
         * point.
         */
        StoredBlock blocks[] = new StoredBlock[3];
        blocks[2] = pindex;
        blocks[1] = pindex.getPrev(blockStore);
        if(blocks[1] == null)
            throw new BlockStoreException("Not enough blocks in blockStore to calculate difficulty");
        blocks[0] = blocks[1].getPrev(blockStore);
        if(blocks[0] == null)
            throw new BlockStoreException("Not enough blocks in blockStore to calculate difficulty");

        // Sorting network.
        if (blocks[0].getHeader().getTimeSeconds() > blocks[2].getHeader().getTimeSeconds()) {
            //std::swap(blocks[0], blocks[2]);
            StoredBlock temp = blocks[0];
            blocks[0] = blocks[2];
            blocks[2] = temp;
        }

        if (blocks[0].getHeader().getTimeSeconds() > blocks[1].getHeader().getTimeSeconds()) {
            //std::swap(blocks[0], blocks[1]);
            StoredBlock temp = blocks[0];
            blocks[0] = blocks[1];
            blocks[1] = temp;
        }

        if (blocks[1].getHeader().getTimeSeconds() > blocks[2].getHeader().getTimeSeconds()) {
            //std::swap(blocks[1], blocks[2]);
            StoredBlock temp = blocks[1];
            blocks[1] = blocks[2];
            blocks[2] = temp;
        }

        // We should have our candidate in the middle now.
        return blocks[1];
    }
}
