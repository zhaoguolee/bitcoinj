package org.bitcoinj.core;

import com.subgraph.orchid.encoders.Hex;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptOpCodes;

import java.nio.ByteBuffer;

public class SlpOpReturnOutput {
    private Script script;
    public SlpOpReturnOutput(String tokenId, long tokenAmount, long changeAmount) {
        ScriptBuilder scriptBuilder = new ScriptBuilder().op(ScriptOpCodes.OP_RETURN)
                .data(Hex.decode("SLP\\x00"))
                .data("SEND".getBytes())
                .data(Hex.decode(tokenId))
                .data(ByteBuffer.allocate(Long.SIZE).putLong(tokenAmount).array());
                if(changeAmount > 0) {
                    scriptBuilder = scriptBuilder.data(ByteBuffer.allocate(Long.SIZE).putLong(changeAmount).array());
                }
        this.script = scriptBuilder.build();
    }

    public Script getScript() {
        return this.script;
    }
}
