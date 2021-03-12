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

package org.bchj.pow;

import org.bchj.core.Block;
import org.bchj.core.NetworkParameters;
import org.bchj.core.StoredBlock;
import org.bchj.store.BlockStore;

public abstract class AbstractRuleCheckerFactory {

    protected NetworkParameters networkParameters;

    public AbstractRuleCheckerFactory(NetworkParameters networkParameters) {
        this.networkParameters = networkParameters;
    }

    public abstract RulesPoolChecker getRuleChecker(StoredBlock storedPrev, Block nextBlock, BlockStore blockStore);

    protected boolean isTestNet() {
        return NetworkParameters.ID_SCALENET.equals(networkParameters.getId()) || NetworkParameters.ID_TESTNET.equals(networkParameters.getId()) || NetworkParameters.ID_TESTNET4.equals(networkParameters.getId());
    }

}
