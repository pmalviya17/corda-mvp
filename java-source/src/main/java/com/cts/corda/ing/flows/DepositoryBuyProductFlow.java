package com.cts.corda.ing.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.cts.corda.ing.contract.ProductIssueContract;
import com.cts.corda.ing.state.ProductTradeState;
import com.cts.corda.ing.util.SerilazationHelper;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.StateAndContract;
import net.corda.core.flows.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import net.corda.core.utilities.UntrustworthyData;

import java.util.stream.Collectors;

import static com.cts.corda.ing.contract.ProductIssueContract.SELF_ISSUE_PRODUCT_CONTRACT_ID;

@InitiatedBy(CustodianBuyProductFlow.class)
@InitiatingFlow
public class DepositoryBuyProductFlow extends AbstractDepositoryFlow {

    private FlowSession flowSession;

    public DepositoryBuyProductFlow(FlowSession flowSession) {
        super(flowSession);
        this.flowSession = flowSession;
    }

    @Suspendable
    public String call() throws FlowException {
        System.out.println("The DepositoryBuyProductFlow start " + System.currentTimeMillis());

        UntrustworthyData<ProductTradeState> inputFromCustodian = flowSession.receive(ProductTradeState.class); // Input is Cash
        ProductTradeState productTradeStateCashInput = SerilazationHelper.getProductTradeState(inputFromCustodian);
        productTradeStateCashInput.setTradeStatus("UNMATCHED");
        System.out.println("DepositoryBuyProductFlow got input from custodian " + productTradeStateCashInput);

        if (productTradeSellRequests.size() > 0) {
            ProductTradeState productSellState = (ProductTradeState) productTradeSellRequests.toArray()[0];
            productTradeStateCashInput.setTradeStatus("MATCHED");
            productTradeStateCashInput.setProductName(productSellState.getProductName());
            productTradeStateCashInput.setQuantity(productSellState.getQuantity());
            productTradeSellRequests.remove(productTradeStateCashInput);
            productSellState.setTradeStatus("MATCHED");
            System.out.println("Sending back response to Cust1 as match found in vault");
            flowSession.send(productSellState);
        } else {
            //wait to receive from seller flow
            productTradeBuyRequests.add(productTradeStateCashInput);
            UntrustworthyData<ProductTradeState> responseFromDepositorySellFlow = flowSession.receive(ProductTradeState.class);
            ProductTradeState productTradeResponse = SerilazationHelper.getProductTradeState(responseFromDepositorySellFlow);
            System.out.println("Received trade from seller " + productTradeResponse);

            System.out.println("Sending back response to buyer custodian");
            productTradeSellRequests.remove(productTradeStateCashInput);

            flowSession.send(productTradeResponse);
        }

        System.out.println("Depo buy flow end");
        getLogger().info("completed depository buy flow");
        return "SUCCESS";

    }


    private SignedTransaction persistProductTradeStateToVault(ProductTradeState productTradeStateCashInput) throws FlowException {
        final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);
        final Command<ProductIssueContract.Commands.SelfIssueProduct> txCommand = new Command<>(new ProductIssueContract.Commands.SelfIssueProduct(),
                productTradeStateCashInput.getParticipants().stream().map(AbstractParty::getOwningKey).collect(Collectors.toList()));
        final TransactionBuilder txBuilder = new TransactionBuilder(notary)
                .withItems(new StateAndContract(productTradeStateCashInput, SELF_ISSUE_PRODUCT_CONTRACT_ID), txCommand);
        final SignedTransaction partSignedTx = getServiceHub().signInitialTransaction(txBuilder);
        return subFlow(new FinalityFlow(partSignedTx));
    }

    public static class DepositorySignatureAcceptorFlow extends FlowLogic<SignedTransaction> {

        private final FlowSession otherPartyFlow;

        public DepositorySignatureAcceptorFlow(FlowSession otherPartyFlow) {
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