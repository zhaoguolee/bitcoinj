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

import com.google.common.util.concurrent.*;
import javafx.scene.input.*;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.kits.MultisigAppKit;
import org.bitcoinj.kits.SlpAppKit;
import org.bitcoinj.kits.SlpBIP47AppKit;
import org.bitcoinj.utils.AppDataDirectory;
import org.bitcoinj.params.*;
import org.bitcoinj.script.Script;
import org.bitcoinj.utils.BriefLogFormatter;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.DeterministicSeed;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;
import wallettemplate.controls.NotificationBarPane;
import wallettemplate.utils.GuiUtils;
import wallettemplate.utils.TextFieldValidator;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;

import static wallettemplate.utils.GuiUtils.*;

public class Main extends Application {
    public static NetworkParameters params = TestNetAsertParams.get();
    public static final Script.ScriptType PREFERRED_OUTPUT_SCRIPT_TYPE = Script.ScriptType.P2PKH;
    public static final String APP_NAME = "Multisig";
    private static final String WALLET_FILE_NAME = APP_NAME.replaceAll("[^a-zA-Z0-9.-]", "_") + "-"
            + params.getPaymentProtocolId();
    private static final String p2pkh_name = "WalletTemplate".replaceAll("[^a-zA-Z0-9.-]", "_") + "-"
            + params.getPaymentProtocolId();

    public static SlpBIP47AppKit p2pkh;
    public static MultisigAppKit bitcoin;
    public static MultisigAppKit bitcoinCosigner1;
    public static MultisigAppKit bitcoinCosigner2;
    public static Main instance;

    private StackPane uiStack;
    private Pane mainUI;
    public MainController controller;
    public NotificationBarPane notificationBar;
    public Stage mainWindow;

    @Override
    public void start(Stage mainWindow) throws Exception {
        try {
            realStart(mainWindow);
        } catch (Throwable e) {
            GuiUtils.crashAlert(e);
            throw e;
        }
    }

    private void realStart(Stage mainWindow) throws IOException, UnreadableWalletException {
        this.mainWindow = mainWindow;
        instance = this;
        // Show the crash dialog for any exceptions that we don't handle and that hit the main loop.
        GuiUtils.handleCrashesOnThisThread();

        if (Utils.isMac()) {
            // We could match the Mac Aqua style here, except that (a) Modena doesn't look that bad, and (b)
            // the date picker widget is kinda broken in AquaFx and I can't be bothered fixing it.
            // AquaFx.style();
        }

        // Load the GUI. The MainController class will be automagically created and wired up.
        URL location = getClass().getResource("main.fxml");
        FXMLLoader loader = new FXMLLoader(location);
        mainUI = loader.load();
        controller = loader.getController();
        // Configure the window with a StackPane so we can overlay things on top of the main UI, and a
        // NotificationBarPane so we can slide messages and progress bars in from the bottom. Note that
        // ordering of the construction and connection matters here, otherwise we get (harmless) CSS error
        // spew to the logs.
        notificationBar = new NotificationBarPane(mainUI);
        mainWindow.setTitle(APP_NAME);
        uiStack = new StackPane();
        Scene scene = new Scene(uiStack);
        TextFieldValidator.configureScene(scene);   // Add CSS that we need.
        scene.getStylesheets().add(getClass().getResource("wallet.css").toString());
        uiStack.getChildren().add(notificationBar);
        mainWindow.setScene(scene);

        // Make log output concise.
        BriefLogFormatter.init();
        // Tell bitcoinj to run event handlers on the JavaFX UI thread. This keeps things simple and means
        // we cannot forget to switch threads when adding event handlers. Unfortunately, the DownloadListener
        // we give to the app kit is currently an exception and runs on a library thread. It'll get fixed in
        // a future version.
        Threading.USER_THREAD = Platform::runLater;
        // Create the app kit. It won't do any heavyweight initialization until after we start it.

        //setupWalletKit(new DeterministicSeed("stuff select coin rib then cargo elite whale jealous person turn notice", null, "", (System.currentTimeMillis() / 1000) - (3600L*24L)));
        //setupWalletKit(null);

        //setupCosigner1(new DeterministicSeed("glimpse grunt power pig math auto save region wasp pact sleep opera", null, "", (System.currentTimeMillis() / 1000) - (3600L*24L)));
        //setupCosigner1(null);

        //setupCosigner2(new DeterministicSeed("cruise apology smart pottery avocado asthma fever able cheap prevent token cupboard", null, "", (System.currentTimeMillis() / 1000) - (3600L*24L)));
        //setupCosigner2(null);

        setupP2PKH(null);

        mainWindow.show();

        WalletSetPasswordController.estimateKeyDerivationTimeMsec();

        /*bitcoin.addListener(new Service.Listener() {
            @Override
            public void failed(Service.State from, Throwable failure) {
                GuiUtils.crashAlert(failure);
            }
        }, Platform::runLater);*/
        //bitcoin.startAsync();
        //bitcoinCosigner1.startAsync();
        //bitcoinCosigner2.startAsync();
        p2pkh.startAsync();

        scene.getAccelerators().put(KeyCombination.valueOf("Shortcut+F"), () -> bitcoin.peerGroup().getDownloadPeer().close());
    }

    public void setupWalletKit(@Nullable DeterministicSeed seed) throws UnreadableWalletException {
        // If seed is non-null it means we are restoring from backup.
        File appDataDirectory = AppDataDirectory.get(APP_NAME).toFile();
        System.out.println(appDataDirectory.getAbsolutePath());
        ArrayList<DeterministicKey> followingKeys = new ArrayList<>();
        DeterministicKey cosigner1 = DeterministicKey.deserializeB58("tpubDDCUxGJ6KbriHKanzx9a1LDCZTm63rhf2b6ZMgnVUbePKAk7UxsMoxVy371eLkobw9BEwAW39gBKWkkCygTZ1SDpqQZBnQ3cxaZ1woJWjtC", params).setPath(DeterministicKeyChain.BIP44_ACCOUNT_ZERO_PATH);
        DeterministicKey cosigner2 = DeterministicKey.deserializeB58("tpubDCq12vdZJ6thJWoRjjPtWZntuqpwf8L9Vf9UZyFPRYFfLNHJB8VsCobD2hKvzMPHWaRQcqkiFQCYVdowXXiDrziv8Kbuuf9ZGny6yLXwEsb", params).setPath(DeterministicKeyChain.BIP44_ACCOUNT_ZERO_PATH);
        followingKeys.add(cosigner1);
        followingKeys.add(cosigner2);
        int m = 3; //How many signatures are required to spend these coins? Setting to 0 means it will be determined automatically using: (cosigners_amount + 1) / 2 + 1
        bitcoin = new MultisigAppKit(params, new File("."), WALLET_FILE_NAME, followingKeys, m) {
            @Override
            public void onSetupCompleted() {
                Platform.runLater(controller::onBitcoinSetup);

                System.out.println("Address:: " + wallet().currentReceiveAddress().toString());
            }
        };
        // Now configure and start the appkit. This will take a second or two - we could show a temporary splash screen
        // or progress widget to keep the user engaged whilst we initialise, but we don't.
        bitcoin.setDownloadListener(controller.progressBarUpdater());
        bitcoin.setBlockingStartup(false);

        if(seed != null) {
            bitcoin.restoreWalletFromSeed(seed);
        }

        bitcoin.setPeerNodes(null);
        try {
            bitcoin.setPeerNodes(new PeerAddress(params, InetAddress.getByName("78.97.206.149")));
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    public void setupCosigner1(@Nullable DeterministicSeed seed) throws UnreadableWalletException {
        // If seed is non-null it means we are restoring from backup.
        File appDataDirectory = AppDataDirectory.get(APP_NAME).toFile();
        System.out.println(appDataDirectory.getAbsolutePath());
        ArrayList<DeterministicKey> followingKeys = new ArrayList<>();
        DeterministicKey cosigner1 = DeterministicKey.deserializeB58("tpubDCq3GCXfqqKE2rRiMsrkBzWvENPZeR8NRk5RHXKShir1XcCCMRr1PF4hvQ1jYUz4ak4PUKxsrGwR54SEyLFvyqmjFUTpoxX5oWm35ypG8GE", params).setPath(DeterministicKeyChain.BIP44_ACCOUNT_ZERO_PATH);
        DeterministicKey cosigner2 = DeterministicKey.deserializeB58("tpubDCq12vdZJ6thJWoRjjPtWZntuqpwf8L9Vf9UZyFPRYFfLNHJB8VsCobD2hKvzMPHWaRQcqkiFQCYVdowXXiDrziv8Kbuuf9ZGny6yLXwEsb", params).setPath(DeterministicKeyChain.BIP44_ACCOUNT_ZERO_PATH);
        followingKeys.add(cosigner1);
        followingKeys.add(cosigner2);
        int m = 3; //How many signatures are required to spend these coins? Setting to 0 means it will be determined automatically using: (cosigners_amount + 1) / 2 + 1
        bitcoinCosigner1 = new MultisigAppKit(params, new File("."), WALLET_FILE_NAME+"_cosigner1", followingKeys, m) {
            @Override
            public void onSetupCompleted() {
                //Platform.runLater(controller::onBitcoinSetup);
                System.out.println("Address:: " + wallet().currentReceiveAddress().toString());
            }
        };
        // Now configure and start the appkit. This will take a second or two - we could show a temporary splash screen
        // or progress widget to keep the user engaged whilst we initialise, but we don't.
        bitcoinCosigner1.setDownloadListener(controller.progressBarUpdater());
        bitcoinCosigner1.setBlockingStartup(false);

        if(seed != null) {
            bitcoinCosigner1.restoreWalletFromSeed(seed);
        }

        bitcoinCosigner1.setPeerNodes(null);
        try {
            bitcoinCosigner1.setPeerNodes(new PeerAddress(params, InetAddress.getByName("78.97.206.149")));
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    public void setupP2PKH(@Nullable DeterministicSeed seed) throws UnreadableWalletException {
        p2pkh = new SlpBIP47AppKit(params, new File("."), p2pkh_name) {
            @Override
            public void onSetupCompleted() {
                System.out.println("P2PKH Address:: " + currentSlpReceiveAddress().toString());
            }
        };
        // Now configure and start the appkit. This will take a second or two - we could show a temporary splash screen
        // or progress widget to keep the user engaged whilst we initialise, but we don't.
        p2pkh.setDownloadListener(controller.progressBarUpdater());
        p2pkh.setBlockingStartup(false);

        if(seed != null) {
            p2pkh.restoreWalletFromSeed(seed);
        }

        p2pkh.setPeerNodes(null);
        try {
            p2pkh.setPeerNodes(new PeerAddress(params, InetAddress.getByName("78.97.206.149")));
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    public void setupCosigner2(@Nullable DeterministicSeed seed) throws UnreadableWalletException {
        File appDataDirectory = AppDataDirectory.get(APP_NAME).toFile();
        System.out.println(appDataDirectory.getAbsolutePath());
        ArrayList<DeterministicKey> followingKeys = new ArrayList<>();
        DeterministicKey cosigner1 = DeterministicKey.deserializeB58("tpubDCq3GCXfqqKE2rRiMsrkBzWvENPZeR8NRk5RHXKShir1XcCCMRr1PF4hvQ1jYUz4ak4PUKxsrGwR54SEyLFvyqmjFUTpoxX5oWm35ypG8GE", params).setPath(DeterministicKeyChain.BIP44_ACCOUNT_ZERO_PATH);
        DeterministicKey cosigner2 = DeterministicKey.deserializeB58("tpubDDCUxGJ6KbriHKanzx9a1LDCZTm63rhf2b6ZMgnVUbePKAk7UxsMoxVy371eLkobw9BEwAW39gBKWkkCygTZ1SDpqQZBnQ3cxaZ1woJWjtC", params).setPath(DeterministicKeyChain.BIP44_ACCOUNT_ZERO_PATH);
        followingKeys.add(cosigner1);
        followingKeys.add(cosigner2);
        int m = 3; //How many signatures are required to spend these coins? Setting to 0 means it will be determined automatically using: (cosigners_amount + 1) / 2 + 1
        bitcoinCosigner2 = new MultisigAppKit(params, new File("."), WALLET_FILE_NAME+"_cosigner2", followingKeys, m) {
            @Override
            public void onSetupCompleted() {
                //Platform.runLater(controller::onBitcoinSetup);
                System.out.println("Address:: " + wallet().currentReceiveAddress().toString());
            }
        };
        // Now configure and start the appkit. This will take a second or two - we could show a temporary splash screen
        // or progress widget to keep the user engaged whilst we initialise, but we don't.
        bitcoinCosigner2.setDownloadListener(controller.progressBarUpdater());
        bitcoinCosigner2.setBlockingStartup(false);

        if(seed != null) {
            bitcoinCosigner2.restoreWalletFromSeed(seed);
        }

        bitcoinCosigner2.setPeerNodes(null);
        try {
            bitcoinCosigner2.setPeerNodes(new PeerAddress(params, InetAddress.getByName("78.97.206.149")));
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
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

    public static void main(String[] args) {
        launch(args);
    }
}
