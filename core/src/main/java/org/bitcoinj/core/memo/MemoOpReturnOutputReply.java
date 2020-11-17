package org.bitcoinj.core.memo;

import com.google.common.base.Preconditions;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptOpCodes;
import org.bouncycastle.util.encoders.Hex;

public class MemoOpReturnOutputReply extends MemoOpReturnOutput {
    public MemoOpReturnOutputReply(String postTxId, String message) {
        Preconditions.checkArgument(Hex.decode(postTxId).length <= 32);
        Preconditions.checkArgument(message.getBytes().length <= 184);
        ScriptBuilder scriptBuilder = new ScriptBuilder()
                .op(ScriptOpCodes.OP_RETURN)
                .data(Hex.decode("6d03"))
                .data(Hex.decode(postTxId))
                .data(message.getBytes());
        this.script = scriptBuilder.build();
    }
}
