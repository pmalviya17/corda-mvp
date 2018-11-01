package com.cts.corda.ing.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.cts.corda.ing.contract.ProductIssueContract;
import com.cts.corda.ing.model.ProductTradeRequest;
import com.cts.corda.ing.model.TradeType;
import com.cts.corda.ing.state.ProductTradeState;
import com.cts.corda.ing.util.BalanceHelper;
import com.cts.corda.ing.util.IdentityHelper;
import com.cts.corda.ing.util.SerilazationHelper;
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

import java.util.stream.Collectors;

import static com.cts.corda.ing.contract.ProductIssueContract.SELF_ISSUE_PRODUCT_CONTRACT_ID;

@InitiatingFlow
@StartableByRPC
public class APBuyProductFLow extends AbstractApFlow {

    private ProductTradeRequest productTradeRequest;
    private String custodianName;


    public APBuyProductFLow(ProductTradeRequest productTradeRequest, String custodianName) {
        this.productTradeRequest = productTradeRequest;
        this.custodianName = custodianName;
        System.out.println("The input is " + productTradeRequest + " for custodian " + custodianName);
    }

    private Party getPartyByName(String custodianName) {
        Iterable<PartyAndCertificate> partyAndCertificates = this.getServiceHub().getIdentityService()
                .getAllIdentities();
        return IdentityHelper.getPartyWithName(partyAndCertificates, custodianName);
    }

    protected Party getNotary() {
        return getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);
    }

    @Suspendable
    public SignedTransaction call() throws FlowException {
        Party custodianParty = getPartyByName(custodianName);
        Party myParty = getServiceHub().getMyInfo().getLegalIdentities().get(0);

        System.out.println("The APBuyFLow is initiated time " + System.currentTimeMillis());

        ProductTradeState productTradeState = null;
        for (ProductTradeState productTradeState1 : new BalanceHelper().getBalance(getServiceHub(), "ISSUECASH")) {
            productTradeState1.setFromParty(myParty);
            productTradeState1.setToParty(custodianParty);
            productTradeState1.setTradeType(TradeType.BUY.name());
            productTradeState1.setProductName(productTradeRequest.getProductName());
            productTradeState1.setQuantity(productTradeRequest.getQuantity());
            productTradeState = productTradeState1;
            break;
        }

        if (productTradeState == null) {
            return null;
        }

        System.out.println("The APBuyFLow : sending Product buy trade request to custodian : ");

        FlowSession toPartySession = initiateFlow(getCustodian(custodianName));
        UntrustworthyData<ProductTradeState> output = toPartySession.sendAndReceive(ProductTradeState.class, productTradeState);
        ProductTradeState outPutValue = SerilazationHelper.getProductTradeState(output);

        System.out.println("The APBuyFLow : received Product trade : " + outPutValue);

        final Command<ProductIssueContract.Commands.SelfIssueProduct> txCommand = new Command<>(new ProductIssueContract.Commands.SelfIssueProduct(),
                productTradeState.getParticipants().stream().map(AbstractParty::getOwningKey).collect(Collectors.toList()));
        final TransactionBuilder txBuilder = new TransactionBuilder(getNotary())
                .withItems(new StateAndContract(productTradeState, SELF_ISSUE_PRODUCT_CONTRACT_ID), txCommand);
        final SignedTransaction partSignedTx = getServiceHub().signInitialTransaction(txBuilder);

        //SignedTransaction tx = persistproductTrade(productTradeState);

        System.out.println("The APBuyFLow end " + System.currentTimeMillis());

        return partSignedTx;
    }

    private SignedTransaction persistproductTrade(ProductTradeState productTradeState) throws FlowException {
        final Command<ProductIssueContract.Commands.SelfIssueProduct> txCommand = new Command<>(new ProductIssueContract.Commands.SelfIssueProduct(),
                productTradeState.getParticipants().stream().map(AbstractParty::getOwningKey).collect(Collectors.toList()));


        System.out.println("Inside APBuyFlow persist trade");
        final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);
        final TransactionBuilder txBuilder = new TransactionBuilder(notary)
                .withItems(new StateAndContract(productTradeState, SELF_ISSUE_PRODUCT_CONTRACT_ID), txCommand);

        System.out.println("APBuyFLow flow verify tx");
        // Stage 3. verify tx

        // Verify that the transaction is valid.
        System.out.println("Before verify TX");
        txBuilder.verify(getServiceHub());
        System.out.println("Verified TX");

        // step 4 Sign the transaction.
        final SignedTransaction partSignedTx = getServiceHub().signInitialTransaction(txBuilder);

        System.out.println("Inside APBuyFLow flow finalize tx");
        // Stage 6. finalise tx;
        SignedTransaction notarisedTx = subFlow(new FinalityFlow(partSignedTx));
        System.out.println("Persisted producttrade in APBuy vault");
        return notarisedTx;
    }

    private Party getCustodian(String custodianName) {
        Iterable<PartyAndCertificate> partyAndCertificates = this.getServiceHub().getIdentityService()
                .getAllIdentities();

        return IdentityHelper.getPartyWithName(partyAndCertificates, custodianName);
    }

    //@InitiatedBy(APBuyProductFLow.class)
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
