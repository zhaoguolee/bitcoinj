package org.bitcoinj.core;

import com.subgraph.orchid.encoders.Hex;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptChunk;
import org.bitcoinj.script.ScriptOpCodes;

import java.nio.ByteBuffer;

public class SlpOpReturnOutput {
    private Script script;
    private byte[] lokad = new byte[] {83, 76, 80, 0};
    private byte[] type = new byte[] {1};
    public SlpOpReturnOutput(String tokenId, long tokenAmount, long changeAmount) {
        ScriptBuilder scriptBuilder = new ScriptBuilder()
                .op(ScriptOpCodes.OP_RETURN)
                .data(lokad)
                .addChunk(new ScriptChunk(type.length, type))
                .data("SEND".getBytes())
                .data(Hex.decode(tokenId))
                .data(ByteBuffer.allocate(8).putLong(tokenAmount).array());
                if(changeAmount > 0) {
                    scriptBuilder = scriptBuilder.data(ByteBuffer.allocate(8).putLong(changeAmount).array());
                }
        this.script = scriptBuilder.build();
    }

    public Script getScript() {
        return this.script;
    }
}
