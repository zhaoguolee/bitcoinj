package org.bitcoinj.core.memo;

import com.google.common.base.Preconditions;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptOpCodes;
import org.bouncycastle.util.encoders.Hex;

public class MemoOpReturnOutputPostMemo extends MemoOpReturnOutput {
    public MemoOpReturnOutputPostMemo(String message) {
        Preconditions.checkArgument(message.getBytes().length <= 217);
        ScriptBuilder scriptBuilder = new ScriptBuilder()
                .op(ScriptOpCodes.OP_RETURN)
                .data(Hex.decode("6d02"))
                .data(message.getBytes());
        this.script = scriptBuilder.build();
    }
}
