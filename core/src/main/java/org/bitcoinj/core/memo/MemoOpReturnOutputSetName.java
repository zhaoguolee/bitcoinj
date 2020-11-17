package org.bitcoinj.core.memo;

import com.google.common.base.Preconditions;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptChunk;
import org.bitcoinj.script.ScriptOpCodes;
import org.bouncycastle.util.encoders.Hex;

import java.nio.ByteBuffer;

public class MemoOpReturnOutputSetName extends MemoOpReturnOutput {
    public MemoOpReturnOutputSetName(String name) {
        Preconditions.checkArgument(name.getBytes().length <= 217);
        ScriptBuilder scriptBuilder = new ScriptBuilder()
                .op(ScriptOpCodes.OP_RETURN)
                .data(Hex.decode("6d01"))
                .data(name.getBytes());
        this.script = scriptBuilder.build();
    }
}
