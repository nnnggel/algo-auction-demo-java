/*
 * Copyright (C) 2020 ycy
 */
package com.algo.demo.auction;

import com.algorand.algosdk.account.Account;
import com.algorand.algosdk.crypto.Address;
import com.algorand.algosdk.crypto.Digest;
import com.algorand.algosdk.crypto.LogicsigSignature;
import com.algorand.algosdk.transaction.SignedTransaction;
import com.algorand.algosdk.transaction.Transaction;
import com.algorand.algosdk.transaction.TxGroup;
import com.algorand.algosdk.util.Encoder;
import com.algorand.algosdk.v2.client.common.AlgodClient;
import com.algorand.algosdk.v2.client.common.Response;
import com.algorand.algosdk.v2.client.model.CompileResponse;
import com.algorand.algosdk.v2.client.model.NodeStatusResponse;
import com.algorand.algosdk.v2.client.model.PendingTransactionResponse;
import com.algorand.algosdk.v2.client.model.PostTransactionsResponse;
import com.algorand.algosdk.v2.client.model.TealKeyValue;
import com.algorand.algosdk.v2.client.model.TealValue;
import com.algorand.algosdk.v2.client.model.TransactionParametersResponse;
import com.google.common.collect.Lists;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Tools for Algo
 *
 * @author chongyu.yuan
 * @since 2022/1/24
 */
public class Utils {

    // utils
    public static byte[] signTransaction(Object obj, Transaction txn) throws IOException, NoSuchAlgorithmException {
        SignedTransaction signedTxn;
        if (obj instanceof Account) {
            signedTxn = ((Account) obj).signTransaction(txn);
        } else if (obj instanceof LogicsigSignature) {
            signedTxn = Account.signLogicsigTransaction((LogicsigSignature) obj, txn);
        } else {
            throw new RuntimeException("obj type unsupported");
        }
        return Encoder.encodeToMsgPack(signedTxn);
    }

    public static byte[] signTransactions(List<Object> objs, List<Transaction> txns) throws IOException {
        // group and sign
        byte groupTransactionBytes[];
        Digest gid = TxGroup.computeGroupID(txns.toArray(new Transaction[0]));
        try (ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream()) {
            for (int i = 0; i < txns.size(); i++) {
                Transaction txn = txns.get(i);
                txn.assignGroupID(gid);
                // sign txn
                SignedTransaction signedTxn;
                Object obj = objs.get(i);
                if (obj instanceof Account) {
                    signedTxn = ((Account) obj).signTransaction(txn);
                } else if (obj instanceof LogicsigSignature) {
                    // LogicSigTransaction(txn, lsig)
                    signedTxn = Account.signLogicsigTransaction((LogicsigSignature) obj, txn);
                } else {
                    throw new RuntimeException("obj type unsupported");
                }
                byteOutputStream.write(Encoder.encodeToMsgPack(signedTxn));
            }
            groupTransactionBytes = byteOutputStream.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("prepare failed.", e);
        }
        return groupTransactionBytes;
    }

    public static PendingTransactionResponse waitForConfirmation(AlgodClient client, String txID, Integer timeout)
        throws Exception {
        if (client == null || txID == null || timeout < 0) {
            throw new IllegalArgumentException("Bad arguments for waitForConfirmation.");
        }
        Response<NodeStatusResponse> resp = client.GetStatus().execute();
        if (!resp.isSuccessful()) {
            throw new Exception(resp.message());
        }
        NodeStatusResponse nodeStatusResponse = resp.body();
        Long startRound = nodeStatusResponse.lastRound + 1;
        Long currentRound = startRound;
        while (currentRound < (startRound + timeout)) {
            // Check the pending transactions
            Response<PendingTransactionResponse> resp2 = client.PendingTransactionInformation(txID).execute();
            if (resp2.isSuccessful()) {
                PendingTransactionResponse pendingInfo = resp2.body();
                if (pendingInfo != null) {
                    if (pendingInfo.confirmedRound != null && pendingInfo.confirmedRound > 0) {
                        // Got the completed Transaction
                        return pendingInfo;
                    }
                    if (pendingInfo.poolError != null && pendingInfo.poolError.length() > 0) {
                        // If there was a pool error, then the transaction has been rejected!
                        throw new Exception(
                            "The transaction has been rejected with a pool error: " + pendingInfo.poolError);
                    }
                }
            }
            resp = client.WaitForBlock(currentRound).execute();
            if (!resp.isSuccessful()) {
                throw new Exception(resp.message());
            }
            currentRound++;
        }
        throw new Exception("Transaction not confirmed after " + timeout + " rounds!");
    }

    public static String sendTransaction(AlgodClient client, byte[] rawTxn) throws Exception {
        Response<PostTransactionsResponse> resp = client.RawTransaction().rawtxn(rawTxn).execute();
        if (!resp.isSuccessful()) {
            throw new RuntimeException("Send Transaction failed, code: " + resp.code() + ", msg: " + resp.message());
        }
        return resp.body().txId;
    }

    public static List<Account> createAccounts(int count) throws NoSuchAlgorithmException {
        List<Account> res = Lists.newArrayList();
        for (int i = 0; i < count; i++) {
            Account account = new Account();
            res.add(account);
        }
        return res;
    }

    public static com.algorand.algosdk.v2.client.model.Account getAccountInfo(AlgodClient client, Account account)
        throws Exception {
        Response<com.algorand.algosdk.v2.client.model.Account> respAcct = client.AccountInformation(
            account.getAddress()).execute();
        if (!respAcct.isSuccessful()) {
            throw new Exception(respAcct.message());
        }
        return respAcct.body();
    }

    public static com.algorand.algosdk.v2.client.model.Account getAccountInfo(AlgodClient client, Address address)
        throws Exception {
        Response<com.algorand.algosdk.v2.client.model.Account> respAcct = client.AccountInformation(address).execute();
        if (!respAcct.isSuccessful()) {
            throw new Exception(respAcct.message());
        }
        return respAcct.body();
    }

    public static com.algorand.algosdk.v2.client.model.Application getApplicationInfo(AlgodClient client, Long appID)
        throws Exception {
        Response<com.algorand.algosdk.v2.client.model.Application> respApp = client.GetApplicationByID(appID).execute();
        if (!respApp.isSuccessful()) {
            throw new Exception(respApp.message());
        }
        return respApp.body();
    }

    public static com.algorand.algosdk.v2.client.model.Asset getAssetInfo(AlgodClient client, Long assetID)
        throws Exception {
        Response<com.algorand.algosdk.v2.client.model.Asset> respAsset = client.GetAssetByID(assetID).execute();
        if (!respAsset.isSuccessful()) {
            throw new Exception(respAsset.message());
        }
        return respAsset.body();
    }

    // type=1 -> bytes -> byte[]
    // type=2 -> uint  -> BigInteger
    public static Map<String, Object> getApplicationGlobalState(AlgodClient client, Long appID) throws Exception {
        com.algorand.algosdk.v2.client.model.Application application = getApplicationInfo(client, appID);
        return getKV(application.params.globalState);
    }

    // type=1 -> bytes -> byte[]
    // type=2 -> uint  -> BigInteger
    public static Map<String, Object> getKV(List<TealKeyValue> tkv) {
        return tkv.stream().collect(Collectors.toMap(kv -> new String(Base64.getDecoder().decode(kv.key)), kv -> {
            TealValue tv = kv.value;
            if (tv.type.intValue() == 1) {
                return Base64.getDecoder().decode(tv.bytes);
            } else if (tv.type.intValue() == 2) {
                return tv.uint;
            }
            return null;
        }, (oldValue, newValue) -> newValue));
    }

    // type=1 -> bytes -> byte[]
    // type=2 -> uint  -> BigInteger
    public static Map<String, Object> getRawKV(List<TealKeyValue> tkv) {
        return tkv.stream().collect(Collectors.toMap(kv -> kv.key, kv -> {
            TealValue tv = kv.value;
            if (tv.type.intValue() == 1) {
                return Base64.getDecoder().decode(tv.bytes);
            } else if (tv.type.intValue() == 2) {
                return tv.uint;
            }
            return null;
        }, (oldValue, newValue) -> newValue));
    }

    public static Map<Long, BigInteger> getAccountBalance(AlgodClient client, Account account) throws Exception {
        Map<Long, BigInteger> res = new TreeMap<>();
        com.algorand.algosdk.v2.client.model.Account _account = getAccountInfo(client, account);
        res.put(0L, BigInteger.valueOf(_account.amount));
        _account.assets.forEach(asset -> res.put(asset.assetId, asset.amount));
        return res;
    }

    public static Map<Long, BigInteger> getAccountBalance(AlgodClient client, Address address) throws Exception {
        Map<Long, BigInteger> res = new TreeMap<>();
        com.algorand.algosdk.v2.client.model.Account _account = getAccountInfo(client, address);
        res.put(0L, BigInteger.valueOf(_account.amount));
        _account.assets.forEach(asset -> res.put(asset.assetId, asset.amount));
        return res;
    }

    public static TransactionParametersResponse getSuggestedParams(AlgodClient client) throws Exception {
        return client.TransactionParams().execute().body();
    }

    public static byte[] compileFile(AlgodClient client, String path) throws Exception {
        // read file
        byte[] data = Files.readAllBytes(Paths.get(path));
        // compile
        CompileResponse response = client.TealCompile().source(data).execute().body();
        byte[] program = Base64.getDecoder().decode(response.result);
        //        LogicsigSignature logicsig = new LogicsigSignature(program);
        //        System.out.println(logicsig.toAddress());
        return program;
    }

}
