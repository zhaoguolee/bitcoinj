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

package org.bchj.pow.factory;

import org.bchj.core.Block;
import org.bchj.core.NetworkParameters;
import org.bchj.core.StoredBlock;
import org.bchj.params.AbstractBitcoinNetParams;
import org.bchj.pow.AbstractRuleCheckerFactory;
import org.bchj.pow.RulesPoolChecker;
import org.bchj.pow.rule.RegTestRuleChecker;
import org.bchj.store.BlockStore;

public class RuleCheckerFactory extends AbstractRuleCheckerFactory {

    private RulesPoolChecker regtestChecker;
    private AbstractRuleCheckerFactory daaRulesFactory;
    private AbstractRuleCheckerFactory edaRulesFactory;
    private AbstractRuleCheckerFactory asertRulesFactory;

    public static RuleCheckerFactory create(NetworkParameters parameters) {
        return new RuleCheckerFactory(parameters);
    }

    private RuleCheckerFactory(NetworkParameters parameters) {
        super(parameters);
        if (NetworkParameters.ID_REGTEST.equals(networkParameters.getId())) {
            this.regtestChecker = new RulesPoolChecker(networkParameters);
            this.regtestChecker.addRule(new RegTestRuleChecker(networkParameters));
        } else {
            this.asertRulesFactory = new AsertRuleCheckerFactory(parameters);
            this.daaRulesFactory = new DAARuleCheckerFactory(parameters);
            this.edaRulesFactory = new EDARuleCheckerFactory(parameters);
        }
    }

    @Override
    public RulesPoolChecker getRuleChecker(StoredBlock storedPrev, Block nextBlock, BlockStore blockStore) {
        if (NetworkParameters.ID_REGTEST.equals(networkParameters.getId())) {
            return this.regtestChecker;
        } else if (AbstractBitcoinNetParams.isAsertEnabled(storedPrev, blockStore, networkParameters)) {
            return asertRulesFactory.getRuleChecker(storedPrev, nextBlock, blockStore);
        } else if (isNewDaaActivated(storedPrev, networkParameters)) {
            return daaRulesFactory.getRuleChecker(storedPrev, nextBlock, blockStore);
        } else {
            return edaRulesFactory.getRuleChecker(storedPrev, nextBlock, blockStore);
        }
    }

    private boolean isNewDaaActivated(StoredBlock storedPrev, NetworkParameters parameters) {
        return storedPrev.getHeight() >= parameters.getDAAUpdateHeight();
    }
}
