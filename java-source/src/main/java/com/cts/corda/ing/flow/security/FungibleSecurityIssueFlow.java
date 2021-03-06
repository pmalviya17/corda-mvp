package com.cts.corda.ing.flow.security;

import co.paralleluniverse.fibers.Suspendable;
import com.cts.corda.ing.contract.SecurityContract;
import lombok.extern.slf4j.Slf4j;
import net.corda.core.contracts.*;
import net.corda.core.flows.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.OpaqueBytes;
import net.corda.core.utilities.ProgressTracker;

import java.util.Set;
import java.util.stream.Collectors;

@StartableByRPC
@InitiatingFlow
@Slf4j
public class FungibleSecurityIssueFlow extends FlowLogic<SignedTransaction> {

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

    private String commodityCode;
    private String commodityDisplayName;
    private Long quantity;

    public FungibleSecurityIssueFlow(Long quantity, String commodityCode, String commodityDisplayName) {
        super();
        this.quantity = quantity;
        this.commodityCode = commodityCode;
        this.commodityDisplayName = commodityDisplayName;
    }


    public FungibleSecurityIssueFlow() {
        super();
    }

    @Override
    public ProgressTracker getProgressTracker() {
        return progressTracker;
    }

    @Suspendable
    @Override
    public SignedTransaction call() throws FlowException {
        log.info("Called CommodityIssueFlow for faceValue " + quantity + " commodityDisplayName " + commodityDisplayName + "  commodityCode " + commodityCode);



        PartyAndReference issuer = this.getOurIdentity().ref(OpaqueBytes.of((commodityCode + commodityDisplayName + quantity).getBytes()));
        Security commodity = new Security(commodityCode, commodityDisplayName, 0);
        Amount<Issued<Security>> commodityAmount = new Amount(quantity, new Issued<>(issuer, commodity));

        SecurityContract.State state = new SecurityContract.State(commodityAmount, getOurIdentity());


        final Command<SecurityContract.Commands.Issue> txCommand = new Command<>(new SecurityContract.Commands.Issue(),
                state.getParticipants().stream().map(AbstractParty::getOwningKey).collect(Collectors.toList()));

        log.info("Inside ProductIssue flow BUILDING tx");
        // Step 2. build tx.
        progressTracker.setCurrentStep(BUILDING);
        final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);
        final TransactionBuilder txBuilder = new TransactionBuilder(notary).withItems(new StateAndContract(state, SecurityContract.Companion.getSECURITY_PROGRAM_ID()), txCommand);
        //final TransactionBuilder txBuilder = new TransactionBuilder(notary);
        log.info("Inside ProductIssue flow verify tx");
        // Stage 3. verify tx
        progressTracker.setCurrentStep(VERIFYING_TRANSACTION);

        // Verify that the transaction is valid.
        log.info("Before verify TX");
        //    txBuilder.verify(getServiceHub());

        Set ss = new SecurityContract().generateIssue(txBuilder, commodityAmount, getOurIdentity(), notary);
        log.info("txBuilder.commands() " + txBuilder.commands());
        //     txBuilder.addCommand(txCommand);
        for (Command command : txBuilder.commands()
                ) {
            log.info("txBuilder.commands() " + command.toString());
            log.info("txBuilder.commands().value " + command.getValue());
        }


        log.info("txBuilder.commands() " + txBuilder.commands().size());
        // step 4 Sign the transaction.
        progressTracker.setCurrentStep(SIGNING);
        final SignedTransaction partSignedTx = getServiceHub().signInitialTransaction(txBuilder);

        log.info("Inside ProductIssue flow finalize tx");
        getServiceHub().recordTransactions(partSignedTx);
        // Stage 6. finalise tx;
        progressTracker.setCurrentStep(FINALISING_TRANSACTION);

        SignedTransaction notarisedTx = subFlow(new FinalityFlow(partSignedTx));
        return notarisedTx;


    }

    @InitiatedBy(FungibleSecurityIssueFlow.class)
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
                    log.info("Inside check transaction for self issue Product");
                }
            }

            return subFlow(new SignTxFlow(otherPartyFlow, SignTransactionFlow.Companion.tracker()));
        }
    }

}
