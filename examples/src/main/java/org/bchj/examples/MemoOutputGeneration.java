/*
 * Copyright by the original author or authors.
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

package org.bchj.examples;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import org.bchj.core.*;
import org.bchj.core.memo.MemoOpReturnOutput;
import org.bchj.kits.WalletAppKit;
import org.bchj.params.MainNetParams;
import org.bchj.params.TestNet3Params;
import org.bchj.wallet.SendRequest;
import org.bchj.wallet.Wallet;
import org.bchj.wallet.Wallet.BalanceType;

import java.io.File;

/**
 * The following example shows you how to create a SendRequest to send coins from a wallet to a given address.
 */
public class MemoOutputGeneration {

    public static void main(String[] args) throws Exception {

        //Create transaction for memo action
        SendRequest req = SendRequest.memoAction(Wallet.createBasic(MainNetParams.get()), new MemoOpReturnOutput.SetName("myusername"));

        //Memo action example
        MemoOpReturnOutput memoOutput = new MemoOpReturnOutput.PostMemo("hello world!");
        System.out.println(memoOutput.getScript());

        //Adding that action as an output in a transaction
        SendRequest req2 = SendRequest.forTx(new Transaction(MainNetParams.get()));
        req2.tx.addOutput(Coin.ZERO, memoOutput.getScript());
    }
}
