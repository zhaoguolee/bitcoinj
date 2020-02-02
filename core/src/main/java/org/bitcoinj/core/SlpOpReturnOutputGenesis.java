package org.bitcoinj.core;

import com.google.common.primitives.Longs;
import com.subgraph.orchid.encoders.Hex;
import com.sun.org.apache.xpath.internal.compiler.OpCodes;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptChunk;
import org.bitcoinj.script.ScriptOpCodes;

import java.nio.ByteBuffer;
import java.util.Collections;

public class SlpOpReturnOutputGenesis {
    private Script script;
    private byte[] lokad = new byte[] {83, 76, 80, 0};
    private byte[] type = new byte[] {1};
    private int PUSHDATA_BYTES = 8;
    public SlpOpReturnOutputGenesis(String ticker, String name, String url, int decimals, long tokenAmount) {
        if(decimals > 9) {
            throw new IllegalArgumentException("Decimal count cannot be greater than 9.");
        } else if(decimals < 0) {
            throw new IllegalArgumentException("Decimal count must be greater than or equal to 0.");
        }

        String start = "1";
        StringBuilder builder = new StringBuilder(start);
        for (int i = 0; i < decimals; i++) {
            builder.append("0");
        }
        long multiplier = Long.parseLong(builder.toString());
        long amountTotal = tokenAmount * multiplier;

        ScriptBuilder scriptBuilder = new ScriptBuilder()
                .op(ScriptOpCodes.OP_RETURN)
                .data(lokad)
                .addChunk(new ScriptChunk(type.length, type))
                .data("GENESIS".getBytes());

        if(ticker.equals("")) {
            scriptBuilder = scriptBuilder.addChunk(new ScriptChunk(ScriptOpCodes.OP_PUSHDATA1, ticker.getBytes()));
        } else {
            scriptBuilder = scriptBuilder.data(ticker.getBytes());
        }

        if(name.equals("")) {
            scriptBuilder = scriptBuilder.addChunk(new ScriptChunk(ScriptOpCodes.OP_PUSHDATA1, name.getBytes()));
        } else {
            scriptBuilder = scriptBuilder.data(name.getBytes());
        }

        if(url.equals("")) {
            scriptBuilder = scriptBuilder.addChunk(new ScriptChunk(ScriptOpCodes.OP_PUSHDATA1, url.getBytes()));
        } else {
            scriptBuilder = scriptBuilder.data(url.getBytes());
        }

        scriptBuilder = scriptBuilder.addChunk(new ScriptChunk(ScriptOpCodes.OP_PUSHDATA1, "".getBytes())) // Document hash.
                .addChunk(new ScriptChunk(Hex.decode("0"+decimals).length, Hex.decode("0"+decimals)))
                .addChunk(new ScriptChunk(ScriptOpCodes.OP_PUSHDATA1, "".getBytes())) // Mint baton.
                .data(ByteBuffer.allocate(PUSHDATA_BYTES).putLong(amountTotal).array());

        this.script = scriptBuilder.build();
    }

    public Script getScript() {
        return this.script;
    }
}
