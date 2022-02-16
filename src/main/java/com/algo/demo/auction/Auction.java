/*
 * Copyright (C) 2020 ycy
 */
package com.algo.demo.auction;

import com.algorand.algosdk.account.Account;
import com.algorand.algosdk.crypto.Address;
import com.algorand.algosdk.crypto.TEALProgram;
import com.algorand.algosdk.logic.StateSchema;
import com.algorand.algosdk.transaction.Transaction;
import com.algorand.algosdk.v2.client.common.AlgodClient;
import com.algorand.algosdk.v2.client.model.PendingTransactionResponse;
import com.algorand.algosdk.v2.client.model.TransactionParametersResponse;
import com.google.common.collect.Lists;
import java.math.BigInteger;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Auction demo(java version): <br/>
 * https://developer.algorand.org/docs/get-started/dapps/pyteal/#application-overview <br/>
 * https://github.com/algorand/auction-demo <br/>
 *
 * @author chongyu.yuan
 * @since 2022/1/24
 */
public class Auction {

    // TODO CHANGEME local env
    private final String BASE_TEAL_PATH = "<-- PATH_FOR_TEAL_CONTRACT_FILES -->";

    // TODO CHANGEME testnet
    private final String ALGOD_API_ADDR = "https://testnet-algorand.api.purestake.io/ps2";
    private final Integer ALGOD_PORT = 443;
    private final String ALGOD_API_TOKEN_KEY = "X-API-Key";
    private final String ALGOD_API_TOKEN = "<-- API_TOKEN -->";
    private final Account FUNDING_ACCOUNT = new Account("<-- MNEMONIC_FOR_FUNDING_ACOUNT -->");

    private AlgodClient client;
    private Account creator;
    private Account seller;
    private Account buyer;

    private Long startTime;
    private Long endTime;
    private Long reserve = 100_000L;
    private Long increment = 10_000L;

    private Long nftID;
    private Long appID;

    public Auction() throws Exception {
        System.out.println("=== prepare account start");

        client = new AlgodClient(ALGOD_API_ADDR, ALGOD_PORT, ALGOD_API_TOKEN, ALGOD_API_TOKEN_KEY);
        System.out.println("Funding account: " + FUNDING_ACCOUNT.getAddress());
        System.out.println("Funding account balance(before): " + Utils.getAccountBalance(client, FUNDING_ACCOUNT));

        prepareAccount(client);
        System.out.println("Funding account balance(after): " + Utils.getAccountBalance(client, FUNDING_ACCOUNT));

        // print accounts info
        System.out.println(
            "Bob (creator account): " + creator.getAddress() + ", " + Utils.getAccountBalance(client, creator));
        System.out.println(
            "Alice (seller account): " + seller.getAddress() + ", " + Utils.getAccountBalance(client, seller));
        System.out.println(
            "Carla (buyer account): " + buyer.getAddress() + ", " + Utils.getAccountBalance(client, buyer));

        System.out.println("=== prepare account finish");
    }

    /**
     * Prepare acounts for auction:<br/>
     * Bob as creator, <br/>
     * Alice as seller, <br/>
     * Carla as buyer. <br/>
     */
    private void prepareAccount(AlgodClient client) {
        try {
            // create 3 accounts
            List<Account> accounts = Utils.createAccounts(3);
            creator = accounts.get(0);
            seller = accounts.get(1);
            buyer = accounts.get(2);

            // initial funding
            long fundingAmount = 1_000_000;
            TransactionParametersResponse sp = Utils.getSuggestedParams(client);
            List<Transaction> txns = accounts.stream().map(
                    account -> Transaction.PaymentTransactionBuilder().sender(FUNDING_ACCOUNT.getAddress())
                        .receiver(account.getAddress()).amount(fundingAmount).suggestedParams(sp).build())
                .collect(Collectors.toList());
            List<Object> signAccounts = Lists.newArrayList(FUNDING_ACCOUNT, FUNDING_ACCOUNT, FUNDING_ACCOUNT);

            String txId = Utils.sendTransaction(client, Utils.signTransactions(signAccounts, txns));
            // Wait for transaction confirmation
            PendingTransactionResponse pTrx = Utils.waitForConfirmation(client, txId, 10);
            System.out.println("Transaction " + txId + " confirmed in round " + pTrx.confirmedRound);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Prepare NFT by seller
     */
    public void prepareNFT() throws Exception {
        //        txn = transaction.AssetCreateTxn(
        //            sender=account.getAddress(),
        //            total=total,
        //            decimals=0,
        //            default_frozen=False,
        //            manager=account.getAddress(),
        //            reserve=account.getAddress(),
        //            freeze=account.getAddress(),
        //            clawback=account.getAddress(),
        //            unit_name=f"D{randomNumber}",
        //            asset_name=f"Dummy {randomNumber}",
        //            url=f"https://dummy.asset/{randomNumber}",
        //            note=randomNote,
        //            sp=client.suggested_params(),
        //            )
        System.out.println("=== prepare nft start");
        System.out.println("Alice is generating an example NFT...");

        String randomNumber = Math.abs(new Random().nextInt(999)) + "";

        TransactionParametersResponse sp = Utils.getSuggestedParams(client);
        Address sellerAddress = seller.getAddress();
        Transaction txn = Transaction.AssetCreateTransactionBuilder().sender(sellerAddress).assetTotal(1)
            .assetDecimals(0).defaultFrozen(false).manager(sellerAddress).reserve(sellerAddress).freeze(sellerAddress)
            .clawback(sellerAddress).assetUnitName(randomNumber).assetName(randomNumber)
            .url("https://dummy.asset/" + randomNumber).noteUTF8(randomNumber).suggestedParams(sp).build();

        String txId = Utils.sendTransaction(client, (Utils.signTransaction(seller, txn)));

        // Wait for transaction confirmation
        PendingTransactionResponse pTrx = Utils.waitForConfirmation(client, txId, 10);
        nftID = pTrx.assetIndex;

        System.out.println("Transaction " + txId + " confirmed in round " + pTrx.confirmedRound);
        System.out.println("The NFT ID is: " + nftID);
        System.out.println("Alice's balances: " + Utils.getAccountBalance(client, seller));

        System.out.println("=== prepare nft finish");
    }

    /**
     * Create auction application by creator
     */
    public void createAuctionApp() throws Exception {
        System.out.println("=== create auction start");
        System.out.println("Bob is creating an auction that lasts 30 seconds to auction off the NFT...");

        byte[] programApproval = Utils.compileFile(client, BASE_TEAL_PATH + "auction_approval.teal");
        byte[] clearApproval = Utils.compileFile(client, BASE_TEAL_PATH + "auction_clear_state.teal");

        TransactionParametersResponse sp = Utils.getSuggestedParams(client);
        //        startTime = int(time()) + 10  # start time is 10 seconds in the future
        //        endTime = startTime + 30  # end time is 30 seconds after start
        //        reserve = 1_000_000  # 1 Algo
        //        increment = 100_000  # 0.1 Algo
        startTime = new Date().getTime() / 1000 + 10;
        endTime = startTime + 30;

        //        app_args = [
        //            encoding.decode_address(seller),
        //            nftID.to_bytes(8, "big"),
        //            startTime.to_bytes(8, "big"),
        //            endTime.to_bytes(8, "big"),
        //            reserve.to_bytes(8, "big"),
        //            minBidIncrement.to_bytes(8, "big"),
        //        ]

        //        txn = transaction.ApplicationCreateTxn(
        //            sender=sender.getAddress(),
        //            on_complete=transaction.OnComplete.NoOpOC,
        //            approval_program=approval,
        //            clear_program=clear,
        //            global_schema=globalSchema,
        //            local_schema=localSchema,
        //            app_args=app_args,
        //            sp=client.suggested_params(),
        //            )
        List<byte[]> args = Lists.newArrayList(seller.getAddress().getBytes(), BigInteger.valueOf(nftID).toByteArray(),
            BigInteger.valueOf(startTime).toByteArray(), BigInteger.valueOf(endTime).toByteArray(),
            BigInteger.valueOf(reserve).toByteArray(), BigInteger.valueOf(increment).toByteArray());

        Transaction txn = Transaction.ApplicationCreateTransactionBuilder().sender(creator.getAddress())
            .approvalProgram(new TEALProgram(programApproval)).clearStateProgram(new TEALProgram(clearApproval))
            .globalStateSchema(new StateSchema(7, 2)).localStateSchema(new StateSchema(0, 0)).args(args)
            .suggestedParams(sp).build();

        String txId = Utils.sendTransaction(client, Utils.signTransaction(creator, txn));
        PendingTransactionResponse pTrx = Utils.waitForConfirmation(client, txId, 10);
        appID = pTrx.applicationIndex;

        System.out.println("Transaction " + txId + " confirmed in round " + pTrx.confirmedRound);
        System.out.println(
            "The auction app ID is " + appID + ", and the escrow account is " + Address.forApplication(appID));
        System.out.println("=== create auction finish");
    }

    /**
     * Setup auction by creator
     */
    public void setupAuction() throws Exception {
        System.out.println("=== setup auction start");
        System.out.println("Alice is setting up and funding NFT auction...");

        Address appAddress = Address.forApplication(appID);
        TransactionParametersResponse sp = Utils.getSuggestedParams(client);
        //        fundingAmount = (
        //            # min account balance
        //            100_000
        //            # additional min balance to opt into NFT
        //                + 100_000
        //            # 3 * min txn fee
        //            + 3 * 1_000
        //        )
        long fundingAmount = 100_000 + 100_000 + 3 * 1_000;

        //        fundAppTxn = transaction.PaymentTxn(
        //            sender=funder.getAddress(),
        //            receiver=appAddr,
        //            amt=fundingAmount,
        //            sp=suggestedParams,
        //            )
        //
        //        setupTxn = transaction.ApplicationCallTxn(
        //            sender=funder.getAddress(),
        //            index=appID,
        //            on_complete=transaction.OnComplete.NoOpOC,
        //            app_args=[b"setup"],
        //            foreign_assets=[nftID],
        //            sp=suggestedParams,
        //        )
        //
        //        fundNftTxn = transaction.AssetTransferTxn(
        //            sender=nftHolder.getAddress(),
        //            receiver=appAddr,
        //            index=nftID,
        //            amt=nftAmount,
        //            sp=suggestedParams,
        //        )
        Transaction fundAppTxn = Transaction.PaymentTransactionBuilder().sender(creator.getAddress())
            .receiver(appAddress).amount(fundingAmount).suggestedParams(sp).build();
        Transaction setupTxn = Transaction.ApplicationCallTransactionBuilder().sender(creator.getAddress())
            .applicationId(appID).args(Lists.newArrayList("setup".getBytes())).foreignAssets(Lists.newArrayList(nftID))
            .suggestedParams(sp).build();
        Transaction fundNftTxn = Transaction.AssetTransferTransactionBuilder().sender(seller.getAddress())
            .assetReceiver(appAddress).assetIndex(nftID).assetAmount(1).suggestedParams(sp).build();

        List<Transaction> txns = Lists.newArrayList(fundAppTxn, setupTxn, fundNftTxn);
        List<Object> signAccounts = Lists.newArrayList(creator, creator, seller);

        String txId = Utils.sendTransaction(client, (Utils.signTransactions(signAccounts, txns)));
        // Wait for transaction confirmation
        PendingTransactionResponse pTrx = Utils.waitForConfirmation(client, txId, 10);

        System.out.println("Transaction " + txId + " confirmed in round " + pTrx.confirmedRound);
        System.out.println("Alice's balances: " + Utils.getAccountBalance(client, seller));

        // Wait to start
        Long lastRound = client.GetStatus().execute().body().lastRound;
        Long lastRoundTime = Long.valueOf(client.GetBlock(lastRound).execute().body().block.get("ts") + "");
        if (lastRoundTime < startTime + 5) {
            System.out.println("Wait to start: " + (startTime + 5 - lastRoundTime));
            TimeUnit.SECONDS.sleep(startTime + 5 - lastRoundTime);
        }
        System.out.println(
            "Auction escrow balances: " + Utils.getAccountBalance(client, Address.forApplication(appID)));

        System.out.println("=== setup auction finish");
    }

    /**
     * Place bid by buyer
     */
    public void placeBid() throws Exception {
        System.out.println("=== placeBid start");

        Long bidAmount = reserve;
        System.out.println("Carla wants to bid on NFT, her balances: " + Utils.getAccountBalance(client, buyer));
        System.out.println("Carla is placing bid for " + bidAmount);

        Address appAddress = Address.forApplication(appID);
        Map<String, Object> appGlobalState = Utils.getApplicationGlobalState(client, appID);
        Object _nftID = appGlobalState.get("nft_id");
        if (_nftID == null || !nftID.equals(((BigInteger) _nftID).longValue())) {
            throw new RuntimeException("!nftID.equals(_nftID), " + nftID + ":" + _nftID);
        }

        //        payTxn = transaction.PaymentTxn(
        //            sender=bidder.getAddress(),
        //            receiver=appAddr,
        //            amt=bidAmount,
        //            sp=suggestedParams,
        //            )
        //
        //        appCallTxn = transaction.ApplicationCallTxn(
        //            sender=bidder.getAddress(),
        //            index=appID,
        //            on_complete=transaction.OnComplete.NoOpOC,
        //            app_args=[b"bid"],
        //            foreign_assets=[nftID],
        //            # must include the previous lead bidder here to the app can refund that bidder's payment
        //            accounts=[prevBidLeader] if prevBidLeader is not None else [],
        //            sp=suggestedParams,
        //        )
        TransactionParametersResponse sp = Utils.getSuggestedParams(client);
        //prevBidLeader
        List<Address> addresses = Lists.newArrayList();
        Object bidAddress = appGlobalState.get("bid_account");
        if (bidAddress != null && bidAddress instanceof byte[]) {
            addresses.add(new Address((byte[]) bidAddress));
        }

        Transaction payTxn = Transaction.PaymentTransactionBuilder().sender(buyer.getAddress()).receiver(appAddress)
            .amount(bidAmount).suggestedParams(sp).build();
        Transaction appCallTxn = Transaction.ApplicationCallTransactionBuilder().sender(buyer.getAddress())
            .applicationId(appID).args(Lists.newArrayList("bid".getBytes())).foreignAssets(Lists.newArrayList(nftID))
            .accounts(addresses).suggestedParams(sp).build();

        List<Transaction> txns = Lists.newArrayList(payTxn, appCallTxn);
        List<Object> signAccounts = Lists.newArrayList(buyer, buyer);

        String txId = Utils.sendTransaction(client, (Utils.signTransactions(signAccounts, txns)));
        // Wait for transaction confirmation
        PendingTransactionResponse pTrx = Utils.waitForConfirmation(client, txId, 10);

        System.out.println("Transaction " + txId + " confirmed in round " + pTrx.confirmedRound);

        System.out.println("=== placeBid finish");
    }

    /**
     * Opt-in by buyer
     */
    public void optInToAsset() throws Exception {
        System.out.println("=== optInToAsset start");

        System.out.println("Carla is opting into NFT with ID " + nftID);
        //        txn = transaction.AssetOptInTxn(
        //            sender=account.getAddress(),
        //            index=assetID,
        //            sp=client.suggested_params(),
        //            )

        // AssetOptInTxn == AssetTransferTransactionBuilder
        //        super().__init__(
        //            sender=sender,
        //            sp=sp,
        //            receiver=sender,
        //            amt=0,
        //            index=index,
        //            note=note,
        //            lease=lease,
        //            rekey_to=rekey_to,
        //            )
        TransactionParametersResponse sp = Utils.getSuggestedParams(client);
        Transaction txn = Transaction.AssetTransferTransactionBuilder().sender(buyer.getAddress())
            .assetReceiver(buyer.getAddress()).assetIndex(nftID).assetAmount(0).suggestedParams(sp).build();

        String txId = Utils.sendTransaction(client, (Utils.signTransaction(buyer, txn)));
        // Wait for transaction confirmation
        PendingTransactionResponse pTrx = Utils.waitForConfirmation(client, txId, 10);

        System.out.println("Transaction " + txId + " confirmed in round " + pTrx.confirmedRound);

        System.out.println("=== optInToAsset finish");
    }

    /**
     * Close auction by creator
     */
    public void closeAuction() throws Exception {
        System.out.println("=== closeAuction start");

        // Wait to end
        Long lastRound = client.GetStatus().execute().body().lastRound;
        Long lastRoundTime = Long.valueOf(client.GetBlock(lastRound).execute().body().block.get("ts") + "");
        if (lastRoundTime < endTime + 5) {
            System.out.println("Wait to end: " + (endTime + 5 - lastRoundTime));
            TimeUnit.SECONDS.sleep(endTime + 5 - lastRoundTime);
        }
        System.out.println("Alice is closing out the auction");

        Map<String, Object> appGlobalState = Utils.getApplicationGlobalState(client, appID);
        Object _nftID = appGlobalState.get("nft_id");
        if (_nftID == null || !nftID.equals(((BigInteger) _nftID).longValue())) {
            throw new RuntimeException("!nftID.equals(_nftID), " + nftID + ":" + _nftID);
        }

        //        deleteTxn = transaction.ApplicationDeleteTxn(
        //            sender=closer.getAddress(),
        //            index=appID,
        //            accounts=accounts,
        //            foreign_assets=[nftID],
        //            sp=client.suggested_params(),
        //        )
        TransactionParametersResponse sp = Utils.getSuggestedParams(client);
        List<Address> addresses = Lists.newArrayList(new Address((byte[]) appGlobalState.get("seller")));
        Object bidAddress = appGlobalState.get("bid_account");
        if (bidAddress != null && bidAddress instanceof byte[]) {
            addresses.add(new Address((byte[]) bidAddress));
        }

        Transaction deleteTxn = Transaction.ApplicationDeleteTransactionBuilder().sender(seller.getAddress())
            .applicationId(appID).accounts(addresses).foreignAssets(Lists.newArrayList(nftID)).suggestedParams(sp)
            .build();
        String txId = Utils.sendTransaction(client, Utils.signTransaction(seller, deleteTxn));
        // Wait for transaction confirmation
        PendingTransactionResponse pTrx = Utils.waitForConfirmation(client, txId, 10);

        System.out.println("Transaction " + txId + " confirmed in round " + pTrx.confirmedRound);

        // check result
        //        actualAppBalances = getBalances(client, get_application_address(appID))
        //        expectedAppBalances = {0: 0}
        //        print("The auction escrow now holds the following:", actualAppBalances)
        //        assert actualAppBalances == expectedAppBalances
        //
        //        bidderNftBalance = getBalances(client, bidder.getAddress())[nftID]
        //        assert bidderNftBalance == nftAmount
        //
        //        actualSellerBalances = getBalances(client, seller.getAddress())
        //        print("Alice's balances after auction: ", actualSellerBalances, " Algos")
        //        actualBidderBalances = getBalances(client, bidder.getAddress())
        //        print("Carla's balances after auction: ", actualBidderBalances, " Algos")
        //        assert len(actualSellerBalances) == 2
        //        # seller should receive the bid amount, minus the txn fee
        //        assert actualSellerBalances[0] >= sellerAlgosBefore + bidAmount - 1_000
        //        assert actualSellerBalances[nftID] == 0

        System.out.println("The auction escrow now holds the following: " + Utils.getAccountBalance(client,
            Address.forApplication(appID)));
        System.out.println("Alice's balances after auction: " + Utils.getAccountBalance(client, seller));
        System.out.println("Carla's balances after auction: " + Utils.getAccountBalance(client, buyer));

        System.out.println("=== closeAuction finish");
    }

    public static void main(String[] args) throws Exception {
        Auction auction = new Auction();
        auction.prepareNFT();
        auction.createAuctionApp();
        auction.setupAuction();
        auction.placeBid();
        auction.optInToAsset();
        auction.closeAuction();
    }

}
