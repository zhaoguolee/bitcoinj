/*
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

import com.subgraph.orchid.encoders.Base64Encoder;
import com.subgraph.orchid.encoders.Hex;
import org.bitcoinj.core.*;
import org.bitcoinj.core.bip47.BIP47Channel;
import org.bitcoinj.kits.BIP47AppKit;
import org.bitcoinj.kits.SlpAppKit;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.net.NetHelper;
import org.bitcoinj.net.SlpDbTokenDetails;
import org.bitcoinj.params.*;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.wallet.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.bitcoinj.wallet.bip47.listeners.BlockchainDownloadProgressTracker;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;
import org.spongycastle.util.encoders.Base64;
import wallettemplate.controls.NotificationBarPane;
import wallettemplate.utils.GuiUtils;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static wallettemplate.utils.GuiUtils.*;

public class Main extends Application {
    private boolean USE_TEST_WALLET = false;
    public static NetworkParameters params = MainNetParams.get();
    public static final String APP_NAME = "WalletTemplate";
    private static final String WALLET_FILE_NAME = APP_NAME.replaceAll("[^a-zA-Z0-9.-]", "_") + "-"
            + params.getPaymentProtocolId();

    public static WalletAppKit bitcoin;
    public static Main instance;

    private StackPane uiStack;
    private Pane mainUI;
    public MainController controller;
    public NotificationBarPane notificationBar;
    public Stage mainWindow;

    @Override
    public void start(Stage mainWindow) throws Exception {

    }

    private void realStart(Stage mainWindow, boolean useTestWallet) throws IOException {

    }

    public void setupWalletKit(@Nullable DeterministicSeed seed) {
        // If seed is non-null it means we are restoring from backup.
        bitcoin = new WalletAppKit(params, new File("."), WALLET_FILE_NAME) {
            @Override
            protected void onSetupCompleted() {
                // Don't make the user wait for confirmations for now, as the intention is they're sending it
                // their own money!
                bitcoin.wallet().allowSpendingUnconfirmedTransactions();
                Platform.runLater(controller::onBitcoinSetup);
            }
        };
        // Now configure and start the appkit. This will take a second or two - we could show a temporary splash screen
        // or progress widget to keep the user engaged whilst we initialise, but we don't.
        if (params == RegTestParams.get()) {
            bitcoin.connectToLocalHost();   // You should run a regtest mode bitcoind locally.
        } else if (params == TestNet3Params.get()) {
            // As an example!
            //bitcoin.useTor();
            // bitcoin.setDiscovery(new HttpDiscovery(params, URI.create("http://localhost:8080/peers"), ECKey.fromPublicOnly(BaseEncoding.base16().decode("02cba68cfd0679d10b186288b75a59f9132b1b3e222f6332717cb8c4eb2040f940".toUpperCase()))));
        }
        bitcoin.setDownloadListener(controller.progressBarUpdater())
               .setBlockingStartup(false)
               .setUserAgent(APP_NAME, "1.0");
        if (seed != null)
            bitcoin.restoreWalletFromSeed(seed);
    }

    private Node stopClickPane = new Pane();

    public class OverlayUI<T> {
        public Node ui;
        public T controller;

        public OverlayUI(Node ui, T controller) {
            this.ui = ui;
            this.controller = controller;
        }

        public void show() {
            checkGuiThread();
            if (currentOverlay == null) {
                uiStack.getChildren().add(stopClickPane);
                uiStack.getChildren().add(ui);
                blurOut(mainUI);
                //darken(mainUI);
                fadeIn(ui);
                zoomIn(ui);
            } else {
                // Do a quick transition between the current overlay and the next.
                // Bug here: we don't pay attention to changes in outsideClickDismisses.
                explodeOut(currentOverlay.ui);
                fadeOutAndRemove(uiStack, currentOverlay.ui);
                uiStack.getChildren().add(ui);
                ui.setOpacity(0.0);
                fadeIn(ui, 100);
                zoomIn(ui, 100);
            }
            currentOverlay = this;
        }

        public void outsideClickDismisses() {
            stopClickPane.setOnMouseClicked((ev) -> done());
        }

        public void done() {
            checkGuiThread();
            if (ui == null) return;  // In the middle of being dismissed and got an extra click.
            explodeOut(ui);
            fadeOutAndRemove(uiStack, ui, stopClickPane);
            blurIn(mainUI);
            //undark(mainUI);
            this.ui = null;
            this.controller = null;
            currentOverlay = null;
        }
    }

    @Nullable
    private OverlayUI currentOverlay;

    public <T> OverlayUI<T> overlayUI(Node node, T controller) {
        checkGuiThread();
        OverlayUI<T> pair = new OverlayUI<T>(node, controller);
        // Auto-magically set the overlayUI member, if it's there.
        try {
            controller.getClass().getField("overlayUI").set(controller, pair);
        } catch (IllegalAccessException | NoSuchFieldException ignored) {
        }
        pair.show();
        return pair;
    }

    /** Loads the FXML file with the given name, blurs out the main UI and puts this one on top. */
    public <T> OverlayUI<T> overlayUI(String name) {
        try {
            checkGuiThread();
            // Load the UI from disk.
            URL location = GuiUtils.getResource(name);
            FXMLLoader loader = new FXMLLoader(location);
            Pane ui = loader.load();
            T controller = loader.getController();
            OverlayUI<T> pair = new OverlayUI<T>(ui, controller);
            // Auto-magically set the overlayUI member, if it's there.
            try {
                if (controller != null)
                    controller.getClass().getField("overlayUI").set(controller, pair);
            } catch (IllegalAccessException | NoSuchFieldException ignored) {
                ignored.printStackTrace();
            }
            pair.show();
            return pair;
        } catch (IOException e) {
            throw new RuntimeException(e);  // Can't happen.
        }
    }

    @Override
    public void stop() throws Exception {
        bitcoin.stopAsync();
        bitcoin.awaitTerminated();
        // Forcibly terminate the JVM because Orchid likes to spew non-daemon threads everywhere.
        Runtime.getRuntime().exit(0);
    }

    public static void main(String[] args) throws Exception {
        /*BIP47AppKit aliceBip47Wallet = new BIP47AppKit().initialize(params, new File("."), "aliceBip47Wallet", null);
        System.out.println("Alice's Notification Address " + aliceBip47Wallet.getAccount(0).getNotificationAddress().toString());
        System.out.println("Alice's Wallet Address " + aliceBip47Wallet.getvWallet().freshReceiveAddress().toString());
        System.out.println("Alice's Payment Code " + aliceBip47Wallet.getPaymentCode());
        aliceBip47Wallet.startAsync();*/

        BIP47AppKit bobBip47Wallet = new BIP47AppKit(params, new File("."), "bobBip47Wallet") {
            @Override
            public void onSetupCompleted() {
                System.out.println("Bob's Notification Address " + getAccount(0).getNotificationAddress().toString());
                System.out.println("Bob's Wallet Address " + getvWallet().freshReceiveAddress().toString());
                System.out.println("Bob's Payment Code " + getPaymentCode());
            }
        };

        bobBip47Wallet.startAsync();
/*
        for(int x = 0; x < bobBip47Wallet.getTransactions().size(); x++) {
            System.out.println(bobBip47Wallet.getTransactions().get(x).getHashAsString());
        }

        SendRequest emptyTx = SendRequest.emptyWallet(params, "bitcoincash:qqc5f26uscyx6mzklsasfkugc86ywdrceqd8fmx3cu");
        Transaction tx = bobBip47Wallet.getvWallet().sendCoinsOffline(emptyTx);
        System.out.println("RAW TX " + new String(Hex.encode(tx.bitcoinSerialize()), StandardCharsets.UTF_8));*/


        /*BIP47Channel paymentChannel = aliceBip47Wallet.getBip47MetaForPaymentCode("PM8TJi4YMtm8uLX3k7tEq38P4WdQ6qf9SuLP1p3toCgcLBf9ffnZoHaLHiJcKvT1qNueFzCgR3oaSLkLBVJsZLhc94PKwPrRnMcBedYdoQL6cLxKms9N");
        Runnable callback = () -> System.out.println("Transaction sent");
        Executor executor = Executors.newSingleThreadExecutor();

        if(paymentChannel == null) {
            //If payment channel is null, we can assume notification tx has not been sent. It's possible one has been sent, but the payment channel is null because the user deleted the .bip47 file, but that's a non-issue.
            System.out.println("Constructing notification tx...");
            SendRequest notification = aliceBip47Wallet.makeNotificationTransaction("PM8TJi4YMtm8uLX3k7tEq38P4WdQ6qf9SuLP1p3toCgcLBf9ffnZoHaLHiJcKvT1qNueFzCgR3oaSLkLBVJsZLhc94PKwPrRnMcBedYdoQL6cLxKms9N");
            aliceBip47Wallet.broadcastTransaction(notification.tx);
            aliceBip47Wallet.putPaymenCodeStatusSent("PM8TJi4YMtm8uLX3k7tEq38P4WdQ6qf9SuLP1p3toCgcLBf9ffnZoHaLHiJcKvT1qNueFzCgR3oaSLkLBVJsZLhc94PKwPrRnMcBedYdoQL6cLxKms9N", notification.tx);
        }

        String depositAddress = null;
        if (paymentChannel != null && paymentChannel.isNotificationTransactionSent()) {
            depositAddress = aliceBip47Wallet.getCurrentOutgoingAddress(paymentChannel);

            System.out.println("Received Bob's Deposit Address " + depositAddress);
            paymentChannel.incrementOutgoingIndex();
            aliceBip47Wallet.saveBip47MetaData();
            Coin amount = Coin.valueOf(1234);

            Transaction payment = aliceBip47Wallet.createSend(depositAddress, amount.value);

            aliceBip47Wallet.broadcastTransaction(payment).addListener(callback, executor);
        }*/
    }
}
