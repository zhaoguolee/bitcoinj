/*
 * Copyright 2011 Google Inc.
 * Copyright 2014 Andreas Schildbach
 * Copyright 2017 Thomas König
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bitcoinj.script;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.bitcoinj.core.*;
import org.bitcoinj.core.Transaction.SigHash;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.Script.VerifyFlag;
import org.bouncycastle.util.encoders.Hex;
import org.hamcrest.core.IsNot;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.bitcoinj.core.Transaction.SERIALIZE_TRANSACTION_NO_WITNESS;
import static org.bitcoinj.core.Utils.HEX;
import static org.bitcoinj.script.ScriptOpCodes.OP_0;
import static org.bitcoinj.script.ScriptOpCodes.OP_INVALIDOPCODE;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.*;

public class ScriptReverseBytesTest {
    @Test
    public void testReverseBytes() throws Exception {
        byte[] opReverseBytes = HEX.decode("196173616e74616c69766564617361646576696c61746e61736103");
        ScriptBuilder opReverseBytesTestScriptBuilder = new ScriptBuilder().data(opReverseBytes).op(ScriptOpCodes.OP_DUP).op(ScriptOpCodes.OP_REVERSEBYTES).op(ScriptOpCodes.OP_EQUAL);
        Script opReverseBytesTestScript = opReverseBytesTestScriptBuilder.build();

        LinkedList<byte[]> stack = new LinkedList<>();
        Script.executeScript(null, 0, opReverseBytesTestScript, stack, Script.ALL_VERIFY_FLAGS);
        assertEquals(new String(Hex.encode(stack.get(0)), StandardCharsets.UTF_8), "01");
    }
}
