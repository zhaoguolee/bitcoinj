package org.bitcoinj.utils;

import org.bitcoinj.core.MultisigInput;

import java.util.ArrayList;

public class MultisigPayload {
    public String address;
    public String amount;
    public ArrayList<MultisigInput> inputs;
}
