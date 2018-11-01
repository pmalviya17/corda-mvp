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
import net.corda.core.utilities.UntrustworthyData;

import java.util.stream.Collectors;

import static com.cts.corda.ing.contract.ProductIssueContract.SELF_ISSUE_PRODUCT_CONTRACT_ID;


@InitiatedBy(CustodianSellProductFlow.class)
@InitiatingFlow
public class DepositorySellProductFlow extends AbstractDepositoryFlow {

    private FlowSession flowSession;

    public DepositorySellProductFlow(FlowSession flowSession) {
        super(flowSession);
        this.flowSession = flowSession;
    }


    @Suspendable
    public String call() throws FlowException {
        System.out.println("The DepositorySellProductFlow start " + System.currentTimeMillis());

        UntrustworthyData<ProductTradeState> inputFromCustodian = flowSession.receive(ProductTradeState.class); // Input is Cash
        ProductTradeState productTradeStateCashInput = SerilazationHelper.getProductTradeState(inputFromCustodian);
        productTradeStateCashInput.setTradeStatus("UNMATCHED");
        System.out.println("DepositoryBuyProductFlow got input from custodian " + productTradeStateCashInput);

        if (productTradeBuyRequests.size() > 0) {
            ProductTradeState productSellState = (ProductTradeState) productTradeBuyRequests.toArray()[0];
            productTradeStateCashInput.setTradeStatus("MATCHED");
            productTradeStateCashInput.setProductName(productSellState.getProductName());
            productTradeStateCashInput.setQuantity(productSellState.getQuantity());
            productTradeBuyRequests.remove(productTradeStateCashInput);
            productSellState.setTradeStatus("MATCHED");
            persistProductTradeStateToVault(productSellState);
            System.out.println("Sending back response to custodian as match found in vault");
            flowSession.send(productSellState);
        } else {
            //wait to receive from seller flow
            productTradeBuyRequests.add(productTradeStateCashInput);
            UntrustworthyData<ProductTradeState> responseFromDepositorySellFlow = flowSession.receive(ProductTradeState.class);
            ProductTradeState productTradeResponse = SerilazationHelper.getProductTradeState(responseFromDepositorySellFlow);
            System.out.println("Sending back response to Cust1 as trade received from seller");
            productTradeBuyRequests.remove(productTradeStateCashInput);
            flowSession.send(productTradeResponse);
        }
        System.out.println("The DepositorySellProductFlow end ");
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

}