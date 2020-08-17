package org.bitcoinj.utils;

import org.bitcoinj.crypto.MultisigSignature;

import java.util.ArrayList;

public class MultisigPayload {
    public String address;
    public String amount;
    public ArrayList<MultisigSignature> signatures;
}
