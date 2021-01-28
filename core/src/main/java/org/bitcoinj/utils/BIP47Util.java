/* Copyright (c) 2017 Stash
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.bitcoinj.utils;

import org.bitcoinj.core.*;
import org.bitcoinj.core.bip47.BIP47PaymentAddress;
import org.bitcoinj.core.bip47.BIP47PaymentCode;
import org.bitcoinj.crypto.BIP47SecretPoint;
import org.bitcoinj.kits.BIP47AppKit;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptChunk;
import org.bitcoinj.script.ScriptException;
import org.bitcoinj.signers.MissingSigResolutionSigner;
import org.bitcoinj.signers.TransactionSigner;
import org.bitcoinj.wallet.*;
import org.bitcoinj.wallet.bip47.NotSecp256k1Exception;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.bitcoinj.core.Utils.HEX;

/**
 * Created by jimmy on 10/3/17.
 */

public class BIP47Util {
    private static final String TAG = "BIP47Util";
    private static final Logger log = LoggerFactory.getLogger(BIP47Util.class);

    public static ArrayList<TransactionOutput> findNtxInputs(Wallet wallet) {
        ArrayList<TransactionOutput> selected = new ArrayList<>();
        long sendSatoshi = 546;
        long opReturnOutput = 146;
        long ntxOutput = 546;
        long changeOutput = 146;
        sendSatoshi = opReturnOutput + ntxOutput + changeOutput;
        List<TransactionOutput> utxos = wallet.getUtxos();
        long inputSatoshi = 0;
        for(TransactionOutput utxo : utxos) {
            if(inputSatoshi < sendSatoshi) {
                inputSatoshi += utxo.getValue().value;
                selected.add(utxo);
            }
        }

        return selected;
    }

    /**
     * <p>Given a send request containing transaction, attempts to sign it's inputs. This method expects transaction
     * to have all necessary inputs connected or they will be ignored.</p>
     * <p>Actual signing is done by pluggable signers and it's not guaranteed that
     * transaction will be complete in the end.</p>
     */
    static void signTransaction(org.bitcoinj.wallet.Wallet vWallet, SendRequest req, byte[] pubKey, BIP47PaymentCode myBIP47PaymentCode) {
        Transaction tx = req.tx;
        List<TransactionInput> inputs = tx.getInputs();
        List<TransactionOutput> outputs = tx.getOutputs();
        checkState(inputs.size() > 0);
        checkState(outputs.size() > 0);

        KeyBag maybeDecryptingKeyBag = new DecryptingKeyBag(vWallet, req.aesKey);

        int numInputs = tx.getInputs().size();
        for (int i = 0; i < numInputs; i++) {
            TransactionInput txIn = tx.getInput(i);
            if (txIn.getConnectedOutput() == null) {
                // Missing connected output, assuming already signed.
                continue;
            }

            try {
                // We assume if its already signed, its hopefully got a SIGHASH type that will not invalidate when
                // we sign missing pieces (to check this would require either assuming any signatures are signing
                // standard output types or a way to get processed signatures out of script execution)
                txIn.getScriptSig().correctlySpends(tx, i, txIn.getConnectedOutput().getScriptPubKey());
                continue;
            } catch (ScriptException e) {
                log.debug("Input contained an incorrect signature", e);
                // Expected.
            }

            Script scriptPubKey = txIn.getConnectedOutput().getScriptPubKey();
            RedeemData redeemData = txIn.getConnectedRedeemData(maybeDecryptingKeyBag);
            checkNotNull(redeemData, "StashTransaction exists in wallet that we cannot redeem: %s", txIn.getOutpoint().getHash());
            Script scriptSig = scriptPubKey.createEmptyInputScript(redeemData.keys.get(0), redeemData.redeemScript);
            txIn.setScriptSig(scriptSig);
            if (i == 0) {
                log.debug("Keys: " + redeemData.keys.size());
                log.debug("Private key 0?: " + redeemData.keys.get(0).hasPrivKey());
                byte[] privKey = redeemData.getFullKey().getPrivKeyBytes();
                log.debug("Private key: " + HEX.encode(privKey));
                log.debug("Public Key: " + HEX.encode(pubKey));
                byte[] outpoint = txIn.getOutpoint().bitcoinSerialize();

                byte[] mask = null;
                try {
                    BIP47SecretPoint BIP47SecretPoint = new BIP47SecretPoint(privKey, pubKey);
                    log.debug("Secret Point: " + HEX.encode(BIP47SecretPoint.ECDHSecretAsBytes()));
                    log.debug("Outpoint: " + HEX.encode(outpoint));
                    mask = BIP47PaymentCode.getMask(BIP47SecretPoint.ECDHSecretAsBytes(), outpoint);
                } catch (InvalidKeyException e) {
                    e.printStackTrace();
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                } catch (NoSuchProviderException e) {
                    e.printStackTrace();
                } catch (InvalidKeySpecException e) {
                    e.printStackTrace();
                }
                log.debug("My payment code: " + myBIP47PaymentCode.toString());
                log.debug("Mask: " + HEX.encode(mask));
                byte[] op_return = BIP47PaymentCode.blind(myBIP47PaymentCode.getPayload(), mask);

                tx.addOutput(Coin.ZERO, ScriptBuilder.createOpReturnScript(op_return));
            }
        }

        tx.shuffleOutputs();

        TransactionSigner.ProposedTransaction proposal = new TransactionSigner.ProposedTransaction(tx, true);
        for (TransactionSigner signer : vWallet.getTransactionSigners()) {
            if (!signer.signInputs(proposal, maybeDecryptingKeyBag))
                log.debug(signer.getClass().getName() + " returned false for the tx");
        }

        // resolve missing sigs if any
        new MissingSigResolutionSigner(req.missingSigsMode).signInputs(proposal, maybeDecryptingKeyBag);
    }

    private static int estimateBytesForSigning(org.bitcoinj.wallet.Wallet vWallet, CoinSelection selection) {
        int size = 0;
        for (TransactionOutput output : selection.gathered) {
            try {
                Script script = output.getScriptPubKey();
                ECKey key = null;
                Script redeemScript = null;
                if (script.isSentToAddress()) {
                    key = vWallet.findKeyFromPubHash(script.getPubKeyHash());
                    checkNotNull(key, "Coin selection includes unspendable outputs");
                } else if (script.isPayToScriptHash()) {
                    redeemScript = vWallet.findRedeemDataFromScriptHash(script.getPubKeyHash()).redeemScript;
                    checkNotNull(redeemScript, "Coin selection includes unspendable outputs");
                }
                size += script.getNumberOfBytesRequiredToSpend(key, redeemScript);
            } catch (ScriptException e) {
                // If this happens it means an output script in a wallet tx could not be understood. That should never
                // happen, if it does it means the wallet has got into an inconsistent state.
                throw new IllegalStateException(e);
            }
        }
        return size;
    }

    /**
     * Finds the first output in a transaction whose op code is OP_RETURN.
     */
    @Nullable
    public static TransactionOutput getOpCodeOutput(Transaction tx) {
        List<TransactionOutput> outputs = tx.getOutputs();
        for (TransactionOutput o : outputs) {
            if (o.getScriptPubKey().isOpReturn()) {
                return o;
            }
        }
        return null;
    }

    /**
     * Returns true if the OP_RETURN op code begins with the byte 0x01 (version 1),
     */
    public static boolean isValidNotificationTransactionOpReturn(TransactionOutput transactionOutput) {
        byte[] data = getOpCodeData(transactionOutput);
        return data != null && HEX.encode(data, 0, 1).equals("01");
    }

    /**
     * Return the payload of the first op code e.g. OP_RETURN.
     */
    private static byte[] getOpCodeData(TransactionOutput opReturnOutput) {
        List<ScriptChunk> chunks = opReturnOutput.getScriptPubKey().getChunks();
        for (ScriptChunk chunk : chunks) {
            if (!chunk.isOpCode() && chunk.data != null) {
                return chunk.data;
            }
        }
        return null;
    }

    /* Extract the payment code from an incoming notification transaction */
    public static BIP47PaymentCode getPaymentCodeInNotificationTransaction(byte[] privKeyBytes, Transaction tx) {
        log.debug("Getting pub key");
        byte[] pubKeyBytes = tx.getInput(0).getScriptSig().getPubKey();

        log.debug("Private Key: " + HEX.encode(privKeyBytes));
        log.debug("Public Key: " + HEX.encode(pubKeyBytes));

        log.debug("Getting op_code data");
        TransactionOutput opReturnOutput = getOpCodeOutput(tx);
        if (opReturnOutput == null) {
            return null;
        }
        byte[] data = getOpCodeData(opReturnOutput);

        try {
            log.debug("Getting secret point..");
            BIP47SecretPoint BIP47SecretPoint = new BIP47SecretPoint(privKeyBytes, pubKeyBytes);
            log.debug("Secret Point: " + HEX.encode(BIP47SecretPoint.ECDHSecretAsBytes()));
            log.debug("Outpoint: " + HEX.encode(tx.getInput(0).getOutpoint().bitcoinSerialize()));
            log.debug("Getting mask...");
            byte[] s = BIP47PaymentCode.getMask(BIP47SecretPoint.ECDHSecretAsBytes(), tx.getInput(0).getOutpoint().bitcoinSerialize());
            log.debug("Getting payload...");
            log.debug("OpCode Data: " + HEX.encode(data));
            log.debug("Mask: " + HEX.encode(s));
            byte[] payload = BIP47PaymentCode.blind(data, s);
            log.debug("Getting payment code...");
            BIP47PaymentCode BIP47PaymentCode = new BIP47PaymentCode(payload);
            log.debug("Payment Code: " + BIP47PaymentCode.toString());
            return BIP47PaymentCode;

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Derives the receive address at idx in depositWallet for senderPaymentCode to deposit, in the wallet's bip47 0th account, i.e. <pre>m / 47' / coin_type' / 0' / idx' .</pre>.
     */
    public static BIP47PaymentAddress getReceiveAddress(BIP47AppKit depositWallet, String senderPaymentCode, int idx) throws AddressFormatException, NotSecp256k1Exception {
        ECKey accountKey = depositWallet.getAccount(0).keyAt(idx);
        return getPaymentAddress(depositWallet.params(), new BIP47PaymentCode(senderPaymentCode), 0, accountKey);
    }

    /**
     * Get the address of receiverBIP47PaymentCode's owner to send a payment to, using BTC as coin_type
     */
    public static BIP47PaymentAddress getSendAddress(BIP47AppKit spendWallet, BIP47PaymentCode receiverBIP47PaymentCode, int idx) throws AddressFormatException, NotSecp256k1Exception {
        ECKey key = spendWallet.getAccount(0).keyAt(0);
        return getPaymentAddress(spendWallet.params(), receiverBIP47PaymentCode, idx, key);
    }

    /**
     * Creates a BIP47PaymentAddress object that the sender will use to pay, using the hardened key at idx
     */
    private static BIP47PaymentAddress getPaymentAddress(NetworkParameters networkParameters, BIP47PaymentCode pcode, int idx, ECKey key) throws AddressFormatException, NotSecp256k1Exception {
        return new BIP47PaymentAddress(networkParameters, pcode, idx, key.getPrivKeyBytes());
    }
}
