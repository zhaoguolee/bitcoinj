package org.bitcoinj.core.flipstarter;

public class FlipstarterPledgePayload {
    public Output[] outputs;
    public Data data;
    public Donation donation;

    public static class Output {
        public long value;
        public String address;
    }

    public static class Data {
        public String alias;
        public String comment;
    }

    public static class Donation {
        public long amount;
    }
}
