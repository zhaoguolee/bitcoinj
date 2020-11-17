package org.bitcoinj.core.memo;

import com.google.common.base.Preconditions;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptOpCodes;
import org.bouncycastle.util.encoders.Hex;

public class MemoOpReturnOutputFollow extends MemoOpReturnOutput {
    public MemoOpReturnOutputFollow(String addressHash160) {
        Preconditions.checkArgument(Hex.decode(addressHash160).length <= 20);
        ScriptBuilder scriptBuilder = new ScriptBuilder()
                .op(ScriptOpCodes.OP_RETURN)
                .data(Hex.decode("6d06"))
                .data(Hex.decode(addressHash160));
        this.script = scriptBuilder.build();
    }
}
