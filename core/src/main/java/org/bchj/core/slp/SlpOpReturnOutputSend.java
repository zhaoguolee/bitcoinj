package org.bchj.core.slp;

import org.bchj.script.Script;
import org.bchj.script.ScriptBuilder;
import org.bchj.script.ScriptChunk;
import org.bchj.script.ScriptOpCodes;
import org.bouncycastle.util.encoders.Hex;

import java.nio.ByteBuffer;

public class SlpOpReturnOutputSend {
    private Script script;
    private byte[] lokad = new byte[]{83, 76, 80, 0};
    private byte[] type = new byte[]{1};
    private int PUSHDATA_BYTES = 8;

    public SlpOpReturnOutputSend(String tokenId, long tokenAmount, long changeAmount) {
        ScriptBuilder scriptBuilder = new ScriptBuilder()
                .op(ScriptOpCodes.OP_RETURN)
                .data(lokad)
                .addChunk(new ScriptChunk(type.length, type))
                .data("SEND".getBytes())
                .data(Hex.decode(tokenId))
                .data(ByteBuffer.allocate(PUSHDATA_BYTES).putLong(tokenAmount).array());
        if (changeAmount > 0) {
            scriptBuilder = scriptBuilder.data(ByteBuffer.allocate(PUSHDATA_BYTES).putLong(changeAmount).array());
        }
        this.script = scriptBuilder.build();
    }

    public Script getScript() {
        return this.script;
    }
}
