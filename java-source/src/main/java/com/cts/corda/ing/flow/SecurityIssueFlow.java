package com.cts.corda.ing.flow;

import co.paralleluniverse.fibers.Suspendable;
import com.cts.corda.ing.contract.SecurityStock;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.PartyAndReference;
import net.corda.core.contracts.StateAndContract;
import net.corda.core.flows.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.OpaqueBytes;
import net.corda.core.utilities.ProgressTracker;

import java.util.stream.Collectors;

import static com.cts.corda.ing.contract.SecurityStock.SECURITY_STOCK_CONTRACT;

@StartableByRPC
@InitiatingFlow
public class SecurityIssueFlow extends FlowLogic<SignedTransaction> {

    private final ProgressTracker.Step INITIALISING = new ProgressTracker.Step("Performing initial steps.");
    private final ProgressTracker.Step VERIFYING_TRANSACTION = new ProgressTracker.Step("Verifying contract constraints.");
    private final ProgressTracker.Step BUILDING = new ProgressTracker.Step("Performing initial steps.");
    private final ProgressTracker.Step SIGNING = new ProgressTracker.Step("Signing transaction.");
    private final ProgressTracker.Step GATHERING_SIGS = new ProgressTracker.Step("Gathering the counterparty's signature.") {
        @Override
        public ProgressTracker childProgressTracker() {
            return CollectSignaturesFlow.Companion.tracker();
        }
    };
    private final ProgressTracker.Step FINALISING_TRANSACTION = new ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
        @Override
        public ProgressTracker childProgressTracker() {
            return FinalityFlow.Companion.tracker();
        }
    };
    private final ProgressTracker progressTracker = new ProgressTracker(
            INITIALISING, VERIFYING_TRANSACTION, BUILDING, SIGNING, GATHERING_SIGS, FINALISING_TRANSACTION
    );

    private Long guantity;
    private String securityName;

    public SecurityIssueFlow(Long quantity, String securityName) {
        super();
        this.guantity = quantity;
        this.securityName = securityName;
    }

    @Override
    public ProgressTracker getProgressTracker() {
        return progressTracker;
    }

    @Suspendable
    @Override
    public SignedTransaction call() throws FlowException {

        System.out.print("Called SecurityIssueFlow for quantity " + guantity + " securityName " + securityName);

        PartyAndReference issuer = this.getOurIdentity().ref(OpaqueBytes.of((securityName + guantity).getBytes()));

        SecurityStock.State productTradeState = new SecurityStock.State(issuer, getOurIdentity(), securityName, guantity);

        System.out.print("productTradeState -->> " + productTradeState);

        final Command<SecurityStock.Commands.Issue> txCommand = new Command<>(new SecurityStock.Commands.Issue(), productTradeState.getParticipants().stream().map(AbstractParty::getOwningKey).collect(Collectors.toList()));

        System.out.println("Inside ProductIssue flow BUILDING tx");

        // Step 2. build tx.
        progressTracker.setCurrentStep(BUILDING);
        final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);
        final TransactionBuilder txBuilder = new TransactionBuilder(notary).withItems(new StateAndContract(productTradeState, SECURITY_STOCK_CONTRACT), txCommand);

        System.out.println("Inside ProductIssue flow verify tx");
        // Stage 3. verify tx
        progressTracker.setCurrentStep(VERIFYING_TRANSACTION);

        // Verify that the transaction is valid.
        getLogger().info("Before verify TX");
        txBuilder.verify(getServiceHub());

        // step 4 Sign the transaction.
        progressTracker.setCurrentStep(SIGNING);
        final SignedTransaction partSignedTx = getServiceHub().signInitialTransaction(txBuilder);

        System.out.println("Inside ProductIssue flow finalize tx");

        // Stage 6. finalise tx;
        progressTracker.setCurrentStep(FINALISING_TRANSACTION);
        // Notarise and record the transaction in both parties' vaults.
        SignedTransaction notarisedTx = subFlow(new FinalityFlow(partSignedTx));
        return notarisedTx;


    }

    @InitiatedBy(SecurityIssueFlow.class)
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
