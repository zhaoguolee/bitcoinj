package org.bitcoinj.core.memo;

import com.google.common.base.Preconditions;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptOpCodes;
import org.bouncycastle.util.encoders.Hex;

public class MemoOpReturnOutput {
    public Script script;
    public Script getScript() {
        return this.script;
    }

    public static class Follow extends MemoOpReturnOutput {
        public Follow(String addressHash160) {
            Preconditions.checkArgument(Hex.decode(addressHash160).length <= 20);
            ScriptBuilder scriptBuilder = new ScriptBuilder()
                    .op(ScriptOpCodes.OP_RETURN)
                    .data(Hex.decode("6d06"))
                    .data(Hex.decode(addressHash160));
            this.script = scriptBuilder.build();
        }
    }

    public static class Unfollow extends MemoOpReturnOutput {
        public Unfollow(String addressHash160) {
            Preconditions.checkArgument(Hex.decode(addressHash160).length <= 20);
            ScriptBuilder scriptBuilder = new ScriptBuilder()
                    .op(ScriptOpCodes.OP_RETURN)
                    .data(Hex.decode("6d07"))
                    .data(Hex.decode(addressHash160));
            this.script = scriptBuilder.build();
        }
    }


    public static class Like extends MemoOpReturnOutput {
        public Like(String postTxId) {
            Preconditions.checkArgument(Hex.decode(postTxId).length <= 32);
            ScriptBuilder scriptBuilder = new ScriptBuilder()
                    .op(ScriptOpCodes.OP_RETURN)
                    .data(Hex.decode("6d04"))
                    .data(Hex.decode(postTxId));
            this.script = scriptBuilder.build();
        }
    }

    public static class PostMemo extends MemoOpReturnOutput {
        public PostMemo(String message) {
            Preconditions.checkArgument(message.getBytes().length <= 217);
            ScriptBuilder scriptBuilder = new ScriptBuilder()
                    .op(ScriptOpCodes.OP_RETURN)
                    .data(Hex.decode("6d02"))
                    .data(message.getBytes());
            this.script = scriptBuilder.build();
        }
    }

    public static class Reply extends MemoOpReturnOutput {
        public Reply(String postTxId, String message) {
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

    public static class SetName extends MemoOpReturnOutput {
        public SetName(String name) {
            Preconditions.checkArgument(name.getBytes().length <= 217);
            ScriptBuilder scriptBuilder = new ScriptBuilder()
                    .op(ScriptOpCodes.OP_RETURN)
                    .data(Hex.decode("6d01"))
                    .data(name.getBytes());
            this.script = scriptBuilder.build();
        }
    }

    public static class SetProfilePicture extends MemoOpReturnOutput {
        public SetProfilePicture(String imgUrl) {
            Preconditions.checkArgument(imgUrl.getBytes().length <= 217);
            ScriptBuilder scriptBuilder = new ScriptBuilder()
                    .op(ScriptOpCodes.OP_RETURN)
                    .data(Hex.decode("6d0a"))
                    .data(imgUrl.getBytes());
            this.script = scriptBuilder.build();
        }
    }
}
