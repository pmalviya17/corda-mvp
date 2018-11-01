package com.cts.corda.ing.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.cts.corda.ing.contract.ProductIssueContract;
import com.cts.corda.ing.model.ProductTradeRequest;
import com.cts.corda.ing.model.TradeType;
import com.cts.corda.ing.state.ProductTradeState;
import com.cts.corda.ing.util.BalanceHelper;
import com.cts.corda.ing.util.IdentityHelper;
import com.cts.corda.ing.util.SerilazationHelper;
import net.corda.core.contracts.Amount;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.StateAndContract;
import net.corda.core.flows.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.identity.PartyAndCertificate;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import net.corda.core.utilities.UntrustworthyData;

import java.util.Currency;
import java.util.HashSet;
import java.util.stream.Collectors;

import static com.cts.corda.ing.contract.ProductIssueContract.SELF_ISSUE_PRODUCT_CONTRACT_ID;

@InitiatingFlow
@StartableByRPC
public class APSellProductFLow extends AbstractApFlow {
    public static HashSet<com.cts.corda.ing.state.ProductTradeState> productTradeBuyRequests = new HashSet<com.cts.corda.ing.state.ProductTradeState>(); //
    public static HashSet<com.cts.corda.ing.state.ProductTradeState> productTradeSellRequests = new HashSet<com.cts.corda.ing.state.ProductTradeState>(); //

    private ProductTradeRequest productTradeRequest;

    private String custodianName;

    public APSellProductFLow(ProductTradeRequest productTradeRequest, String custodianName) {
        this.productTradeRequest = productTradeRequest;
        this.custodianName = custodianName;
        System.out.println("The input is " + productTradeRequest + " for custodian " + custodianName);
    }

    protected Party getNotary() {
        return getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);
    }

    @Suspendable
    public SignedTransaction call() throws FlowException {
        Party custodianParty = getCustodian(custodianName);
        Party myParty = getServiceHub().getMyInfo().getLegalIdentities().get(0);

        System.out.println("The APSellFLow is initiated in time " + System.currentTimeMillis());
        com.cts.corda.ing.state.ProductTradeState productTradeState = null;
        for (ProductTradeState productTradeState1 : new BalanceHelper().getBalance(getServiceHub(), "ISSUEPRODUCT")) {
            productTradeState1.setFromParty(myParty);
            productTradeState1.setToParty(custodianParty);
            productTradeState1.setTradeType(TradeType.SELL.name());
            productTradeState1.setAmount(new Amount<Currency>(productTradeRequest.getAmount(), Currency.getInstance("GBP")));
            productTradeState1.setQuantity(productTradeRequest.getQuantity());
            productTradeState = productTradeState1;
            break;
        }

        if (productTradeState == null) {
            return null;// "FAILED TO SELL AS NO Product IN VAULT";
        }

        FlowSession toPartySession = initiateFlow(getCustodian(custodianName));
        UntrustworthyData<ProductTradeState> output = toPartySession.sendAndReceive(ProductTradeState.class, productTradeState);
        com.cts.corda.ing.state.ProductTradeState outPutValue = SerilazationHelper.getProductTradeState(output);

        System.out.print("Received trade from custodian : " + outPutValue);

//
        final Command<ProductIssueContract.Commands.SelfIssueProduct> txCommand = new Command<>(new ProductIssueContract.Commands.SelfIssueProduct(),
                productTradeState.getParticipants().stream().map(AbstractParty::getOwningKey).collect(Collectors.toList()));
        final TransactionBuilder txBuilder = new TransactionBuilder(getNotary())
                .withItems(new StateAndContract(productTradeState, SELF_ISSUE_PRODUCT_CONTRACT_ID), txCommand);
        final SignedTransaction partSignedTx = getServiceHub().signInitialTransaction(txBuilder);

        return partSignedTx;//"SUCCESS";
    }


    private SignedTransaction persistProductTrade(ProductTradeState productTradeState) throws FlowException {
        final Command<ProductIssueContract.Commands.SelfIssueProduct> txCommand = new Command<>(new ProductIssueContract.Commands.SelfIssueProduct(),
                productTradeState.getParticipants().stream().map(AbstractParty::getOwningKey).collect(Collectors.toList()));


        System.out.println("Inside APSellFlow persist trade");
        final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);
        final TransactionBuilder txBuilder = new TransactionBuilder(notary)
                .withItems(new StateAndContract(productTradeState, SELF_ISSUE_PRODUCT_CONTRACT_ID), txCommand);

        System.out.println("APSellFlow  flow verify tx");
        // Stage 3. verify tx

        // Verify that the transaction is valid.
        System.out.println("Before verify TX");
        txBuilder.verify(getServiceHub());
        System.out.println("Verified TX");

        // step 4 Sign the transaction.
        final SignedTransaction partSignedTx = getServiceHub().signInitialTransaction(txBuilder);

        System.out.println("Inside APSellFlow  flow finalize tx");
        // Stage 6. finalise tx;
        SignedTransaction notarisedTx = subFlow(new FinalityFlow(partSignedTx));
        System.out.println("Persisted producttrade in APSell vault");
        return notarisedTx;
    }

    private Party getCustodian(String custodianName) {
        Iterable<PartyAndCertificate> partyAndCertificates = this.getServiceHub().getIdentityService()
                .getAllIdentities();

        return IdentityHelper.getPartyWithName(partyAndCertificates, custodianName);
    }

    public static class Acceptor extends FlowLogic<SignedTransaction> {

        private final FlowSession otherPartyFlow;

        public Acceptor(FlowSession otherPartyFlow) {
            this.otherPartyFlow = otherPartyFlow;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            class SignTxFlow extends SignTransactionFlow {
                private SignTxFlow(FlowSession otherPartyFlow, ProgressTracker progressTracker) {
                    super(otherPartyFlow, progressTracker);
                }

                @Override
                protected void checkTransaction(SignedTransaction stx) {
                    System.out.print("Inside check transaction for self issue product");
                }
            }
            return subFlow(new SignTxFlow(otherPartyFlow, SignTransactionFlow.Companion.tracker()));
        }
    }

}

