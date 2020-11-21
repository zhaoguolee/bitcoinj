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

package wallettemplate;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import org.apache.commons.lang3.tuple.Pair;
import org.bitcoinj.core.*;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.util.encoders.Hex;
import wallettemplate.controls.BitcoinAddressValidator;
import wallettemplate.utils.TextFieldValidator;
import wallettemplate.utils.WTUtils;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkState;
import static wallettemplate.utils.GuiUtils.*;

public class SendMoneyController {
    public Button sendBtn;
    public Button cancelBtn;
    public TextField address;
    public Label titleLabel;
    public TextField amountEdit;
    public Label btcLabel;

    public Main.OverlayUI overlayUI;

    private Wallet.SendResult sendResult;
    private KeyParameter aesKey;

    // Called by FXMLLoader
    public void initialize() {
        Coin balance = Main.bitcoin.wallet().getBalance(Wallet.BalanceType.ESTIMATED);
        checkState(!balance.isZero());
        //new BitcoinAddressValidator(Main.params, address, sendBtn);
        new TextFieldValidator(amountEdit, text ->
                !WTUtils.didThrow(() -> checkState(Coin.parseCoin(text).compareTo(balance) <= 0)));
        amountEdit.setText(balance.toPlainString());
        address.setPromptText(Address.fromKey(Main.params, new ECKey()).toString());
    }

    public void cancel(ActionEvent event) {
        overlayUI.done();
    }

    public void send(ActionEvent event) {
        // Address exception cannot happen as we validated it beforehand.
        try {
            String payload = address.getText();
            Pair<SendRequest, String> pairReq = SendRequest.createFlipstarterPledge(Main.bitcoin.wallet(), payload);
            Main.bitcoin.peerGroup().broadcastTransaction(pairReq.getLeft().tx);
            System.out.println("INPUT TX:: " + Hex.toHexString(pairReq.getLeft().tx.bitcoinSerialize()));
            System.out.println("PLEDGE:: " + pairReq.getRight());
        } catch (InsufficientMoneyException e) {
            informationalAlert("Could not empty the wallet",
                    "You may have too little money left in the wallet to make a transaction.");
            overlayUI.done();
        } catch (ECKey.KeyIsEncryptedException e) {
            askForPasswordAndRetry();
        }
    }

    private void askForPasswordAndRetry() {
        Main.OverlayUI<WalletPasswordController> pwd = Main.instance.overlayUI("wallet_password.fxml");
        final String addressStr = address.getText();
        final String amountStr = amountEdit.getText();
        pwd.controller.aesKeyProperty().addListener((observable, old, cur) -> {
            // We only get here if the user found the right password. If they don't or they cancel, we end up back on
            // the main UI screen. By now the send money screen is history so we must recreate it.
            checkGuiThread();
            Main.OverlayUI<SendMoneyController> screen = Main.instance.overlayUI("send_money.fxml");
            screen.controller.aesKey = cur;
            screen.controller.address.setText(addressStr);
            screen.controller.amountEdit.setText(amountStr);
            screen.controller.send(null);
        });
    }

    private void updateTitleForBroadcast() {
        final int peers = sendResult.tx.getConfidence().numBroadcastPeers();
        titleLabel.setText(String.format("Broadcasting ... seen by %d peers", peers));
    }
}
