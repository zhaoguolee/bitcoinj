package org.bchj.pow.rule;

import org.bchj.core.*;
import org.bchj.pow.AbstractPowRulesChecker;
import org.bchj.store.BlockStore;
import org.bchj.store.BlockStoreException;

public class RegTestRuleChecker extends AbstractPowRulesChecker {
    public RegTestRuleChecker(NetworkParameters networkParameters) {
        super(networkParameters);
    }

    public void checkRules(StoredBlock storedPrev, Block nextBlock, BlockStore blockStore,
                           AbstractBlockChain blockChain) throws VerificationException, BlockStoreException {
        // always pass
    }
}
