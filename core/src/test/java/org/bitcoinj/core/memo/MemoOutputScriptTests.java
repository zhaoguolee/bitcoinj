package org.bitcoinj.core.memo;

import org.bitcoinj.script.Script;
import org.junit.Test;

public class MemoOutputScriptTests {
    @Test
    public void setName() {
        Script setNameOutputScript = new MemoOpReturnOutput.SetName("username").getScript();
        System.out.println(setNameOutputScript);
    }

    @Test
    public void postMemo() {
        Script postMemoOutputScript = new MemoOpReturnOutput.PostMemo("this is a message").getScript();
        System.out.println(postMemoOutputScript);
    }
}
