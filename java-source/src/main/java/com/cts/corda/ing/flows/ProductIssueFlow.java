package com.cts.corda.ing.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.cts.corda.ing.contract.ProductIssueContract;
import com.cts.corda.ing.model.ProductAsset;
import com.cts.corda.ing.state.ProductTradeState;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.StateAndContract;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;

import java.util.stream.Collectors;

import static com.cts.corda.ing.contract.ProductIssueContract.SELF_ISSUE_PRODUCT_CONTRACT_ID;

@StartableByRPC
@InitiatingFlow
public class ProductIssueFlow extends AbstractIssueFlow {

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
    private ProductAsset productAsset;

    public ProductIssueFlow(ProductAsset productAsset) {
        super();
        this.productAsset = productAsset;
    }

    @Override
    public ProgressTracker getProgressTracker() {
        return progressTracker;
    }

    @Suspendable
    @Override
    public SignedTransaction call() throws FlowException {
        System.out.println("Inside ProductIssue flow call init");
        // Step 1. Initialisation.
        progressTracker.setCurrentStep(INITIALISING);

        ProductTradeState productTradeState = new ProductTradeState(
                getServiceHub().getMyInfo().getLegalIdentities().get(0),
                getServiceHub().getMyInfo().getLegalIdentities().get(0),
                productAsset.getProductName(),
                productAsset.getQuantity(),
                null, "ISSUEPRODUCT",
                new UniqueIdentifier());

        System.out.print("productTradeState -->> " + productTradeState);
        final Command<ProductIssueContract.Commands.SelfIssueProduct> txCommand = new Command<>(new ProductIssueContract.Commands.SelfIssueProduct(),
                productTradeState.getParticipants().stream().map(AbstractParty::getOwningKey).collect(Collectors.toList()));

        System.out.println("Inside ProductIssue flow BUILDING tx");
        // Step 2. build tx.
        progressTracker.setCurrentStep(BUILDING);
        final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);
        final TransactionBuilder txBuilder = new TransactionBuilder(notary)
                .withItems(new StateAndContract(productTradeState, SELF_ISSUE_PRODUCT_CONTRACT_ID), txCommand);

        System.out.println("Inside ProductIssue flow verify tx");
        // Stage 3. verify tx
        progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
        // Verify that the transaction is valid.
        getLogger().info("Before verify TX");
        txBuilder.verify(getServiceHub());

        getLogger().info("Verified TX");


        // step 4 Sign the transaction.
        progressTracker.setCurrentStep(SIGNING);
        final SignedTransaction partSignedTx = getServiceHub().signInitialTransaction(txBuilder);
        getLogger().info("Signed TX");

        System.out.println("Inside ProductIssue flow finalize tx");

        // Stage 6. finalise tx;
        progressTracker.setCurrentStep(FINALISING_TRANSACTION);
        // Notarise and record the transaction in both parties' vaults.
        SignedTransaction notarisedTx = subFlow(new FinalityFlow(partSignedTx));
        getLogger().info("Notarised TX");
        return notarisedTx;


    }

    @InitiatedBy(ProductIssueFlow.class)
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
                    System.out.print("Inside check transaction for self issue Product");
                    /*requireThat(require -> {
                        ContractState output = stx.getTx().getOutputs().get(0).getData();
                        require.using("This must be an IOU transaction.", output instanceof IOUState);
                        IOUState iou = (IOUState) output;
                        require.using("I won't accept IOUs with a value over 100.", iou.getValue() <= 100);
                        return null;
                    });*/
                }
            }

            return subFlow(new SignTxFlow(otherPartyFlow, SignTransactionFlow.Companion.tracker()));
        }
    }

}
