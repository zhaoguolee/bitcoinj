package org.bitcoinj.core;

import org.bitcoinj.crypto.MultisigSignature;

import java.util.ArrayList;

public class MultisigInput {
    public ArrayList<MultisigSignature> signatures = new ArrayList<>();
}
