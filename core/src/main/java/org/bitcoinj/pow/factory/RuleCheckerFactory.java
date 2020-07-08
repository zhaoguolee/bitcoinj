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

package org.bitcoinj.pow.factory;

import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.pow.AbstractRuleCheckerFactory;
import org.bitcoinj.pow.RulesPoolChecker;
import org.bitcoinj.pow.rule.RegTestRuleChecker;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;

public class RuleCheckerFactory extends AbstractRuleCheckerFactory {

    private RulesPoolChecker regtestChecker;
    private AbstractRuleCheckerFactory daaRulesFactory;
    private AbstractRuleCheckerFactory edaRulesFactory;
    private AbstractRuleCheckerFactory oscillationFixRulesFactory;

    public static RuleCheckerFactory create(NetworkParameters parameters) {
        return new RuleCheckerFactory(parameters);
    }

    private RuleCheckerFactory(NetworkParameters parameters) {
        super(parameters);
        if (NetworkParameters.ID_REGTEST.equals(networkParameters.getId())) {
            this.regtestChecker = new RulesPoolChecker(networkParameters);
            this.regtestChecker.addRule(new RegTestRuleChecker(networkParameters));
        } else {
            this.oscillationFixRulesFactory = new OscillationFixRuleCheckerFactory(parameters);
            this.daaRulesFactory = new DAARuleCheckerFactory(parameters);
            this.edaRulesFactory = new EDARuleCheckerFactory(parameters);
        }
    }

    @Override
    public RulesPoolChecker getRuleChecker(StoredBlock storedPrev, Block nextBlock, BlockStore blockStore) {
        if (NetworkParameters.ID_REGTEST.equals(networkParameters.getId())) {
            return this.regtestChecker;
        } else if(isOscillationFixActivated(storedPrev, blockStore, networkParameters)) {
            return oscillationFixRulesFactory.getRuleChecker(storedPrev, nextBlock, blockStore);
        } else if (isNewDaaActivated(storedPrev, networkParameters)) {
            return daaRulesFactory.getRuleChecker(storedPrev, nextBlock, blockStore);
        } else {
            return edaRulesFactory.getRuleChecker(storedPrev, nextBlock, blockStore);
        }
    }

    private boolean isNewDaaActivated(StoredBlock storedPrev, NetworkParameters parameters) {
        return storedPrev.getHeight() >= parameters.getDAAUpdateHeight();
    }

    private boolean isOscillationFixActivated(StoredBlock storedPrev, BlockStore blockStore, NetworkParameters parameters) {
        try {
            long mtp = BlockChain.getMedianTimestampOfRecentBlocks(storedPrev, blockStore);
            return mtp >= parameters.getOscillationFixUpdateTime();
        } catch (BlockStoreException e) {
            e.printStackTrace();
        }
        return false;
    }
}
