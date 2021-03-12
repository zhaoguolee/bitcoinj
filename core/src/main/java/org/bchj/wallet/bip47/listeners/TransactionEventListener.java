/* Copyright (c) 2017 Stash
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.bchj.wallet.bip47.listeners;

import org.bchj.core.Coin;
import org.bchj.core.Transaction;
import org.bchj.kits.WalletKitCore;
import org.bchj.wallet.Wallet;
import org.bchj.wallet.listeners.WalletCoinsReceivedEventListener;
import org.bchj.wallet.listeners.WalletCoinsSentEventListener;

/**
 * Created by jimmy on 9/29/17.
 */

public abstract class TransactionEventListener implements WalletCoinsReceivedEventListener, WalletCoinsSentEventListener {
    protected WalletKitCore wallet;

    public void setWallet(WalletKitCore wallet) {
        this.wallet = wallet;
    }

    @Override
    public void onCoinsReceived(Wallet wallet, Transaction transaction, Coin coin, Coin coin1) {
        onTransactionReceived(this.wallet, transaction);
    }

    @Override
    public void onCoinsSent(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
        onTransactionSent(this.wallet, tx);
    }

    public abstract void onTransactionReceived(WalletKitCore wallet, Transaction transaction);

    public abstract void onTransactionSent(WalletKitCore wallet, Transaction transaction);
}
