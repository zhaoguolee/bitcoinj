package org.bitcoinj.core.memo;

import com.google.common.base.Preconditions;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptOpCodes;
import org.bouncycastle.util.encoders.Hex;

public class MemoOpReturnOutputLike extends MemoOpReturnOutput {
    public MemoOpReturnOutputLike(String postTxId) {
        Preconditions.checkArgument(Hex.decode(postTxId).length <= 32);
        ScriptBuilder scriptBuilder = new ScriptBuilder()
                .op(ScriptOpCodes.OP_RETURN)
                .data(Hex.decode("6d04"))
                .data(Hex.decode(postTxId));
        this.script = scriptBuilder.build();
    }
}
