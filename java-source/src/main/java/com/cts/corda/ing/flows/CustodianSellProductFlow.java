package com.cts.corda.ing.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.cts.corda.ing.state.ProductTradeState;
import com.cts.corda.ing.util.IdentityHelper;
import com.cts.corda.ing.util.SerilazationHelper;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.identity.PartyAndCertificate;
import net.corda.core.utilities.UntrustworthyData;

import static com.cts.corda.ing.util.SerilazationHelper.getProductTradeState;

@InitiatedBy(APSellProductFLow.class)
@InitiatingFlow
public class CustodianSellProductFlow extends FlowLogic<String> {

    private String dipositoryName;

    private FlowSession flowSession;

    public CustodianSellProductFlow(FlowSession flowSession) {
        this.flowSession = flowSession;
        this.dipositoryName = "DEPOSITORY";
        System.out.println("Inside custodian called by " + flowSession.getCounterparty());
    }

    @Suspendable
    public String call() throws FlowException {
        System.out.print("The custodian sell flow started at " + System.currentTimeMillis());

        UntrustworthyData<ProductTradeState> inputFromAP = flowSession.receive(ProductTradeState.class);
        ProductTradeState productTradeStateFromAp = getProductTradeState(inputFromAP);

        System.out.println("Custodian sell flow received input from AP " + productTradeStateFromAp);

        productTradeStateFromAp.setFromParty(getServiceHub().getMyInfo().getLegalIdentities().get(0));
        productTradeStateFromAp.setToParty(getDipository(dipositoryName));

        System.out.println("**In call method for custodian flow -->" + productTradeStateFromAp);

        Party custodianParty = getDipository(dipositoryName);
        Party myParty = getServiceHub().getMyInfo().getLegalIdentities().get(0);

        System.out.println("Sending trade for execution at depository");

        FlowSession toPartySession = initiateFlow(getDipository(dipositoryName));
        UntrustworthyData<ProductTradeState> output = toPartySession.sendAndReceive(ProductTradeState.class,
                productTradeStateFromAp);
        ProductTradeState outputFromDepository = SerilazationHelper.getProductTradeState(output);
        System.out.print("Received trade from depository after execution " + outputFromDepository);
        flowSession.send(outputFromDepository);
        //
/*

		final Command<ProductIssueContract.Commands.ProductBuyCommand> txCommand = new Command<>(new ProductIssueContract.Commands.ProductBuyCommand(),
				ImmutableList.of(custodianParty,myParty).stream().map(AbstractParty::getOwningKey).collect(Collectors.toList()));
		final TransactionBuilder txBuilder = new TransactionBuilder(getNotary()).withItems(new StateAndContract(outputFromDepository, SELF_ISSUE_ETF_CONTRACT_ID),txCommand);
		txBuilder.verify(getServiceHub());
		final SignedTransaction partSignedTx = getServiceHub().signInitialTransaction(txBuilder);
		final SignedTransaction fullySignedTx = subFlow(new CollectSignaturesFlow(partSignedTx, Sets.newHashSet(toPartySession), CollectSignaturesFlow.Companion.tracker()));


		System.out.println("**In call method for custodian flow output from depository-->" + outputFromDepository);
		//
		subFlow(new FinalityFlow(fullySignedTx));
*/


        System.out.print("The custodian Sell end " + System.currentTimeMillis());

        return " SELL-CUSTODIAN-SUCCESS ";
    }

    protected Party getNotary() {
        return getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);
    }


    private Party getDipository(String dipositoryName) {
        Iterable<PartyAndCertificate> partyAndCertificates = this.getServiceHub().getIdentityService()
                .getAllIdentities();
        return IdentityHelper.getPartyWithName(partyAndCertificates, dipositoryName);
    }
}
