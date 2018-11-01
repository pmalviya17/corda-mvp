package com.cts.corda.ing.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.cts.corda.ing.flows.tracker.ProductProgressTracker;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.corda.confidential.SwapIdentitiesFlow;
import net.corda.core.contracts.Amount;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.AnonymousParty;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import net.corda.core.utilities.UntrustworthyData;
import com.cts.corda.ing.obligation.ProductAsset;
import com.cts.corda.ing.obligation.ProductContract;
import com.cts.corda.ing.obligation.ProductObligation;

import java.security.PublicKey;
import java.time.Duration;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;

@InitiatingFlow
@StartableByRPC
public class ProductBuyInitiatorFlow extends AbstractIssueFlow {

    private final ProgressTracker progressTracker = new ProgressTracker(
            ProductProgressTracker.INITIALISING,
            ProductProgressTracker.BUILDING,
            ProductProgressTracker.SIGNING,
            ProductProgressTracker.COLLECTING,
            ProductProgressTracker.FINALISING
    );
    private Party fromParty;// AP
    private Party toParty;
    private ProductAsset productAsset;
    private Amount<Currency> amount;
    private String partyRole;
    private boolean anonymous;

    public ProductBuyInitiatorFlow(AbstractParty fromParty, AbstractParty toParty, ProductAsset productAsset) {

        //TODO
        if (this.getOurIdentity().getName().getCommonName().toUpperCase().contains("AP")) {
            partyRole = "AP";
        }

        if (this.getOurIdentity().getName().getCommonName().toUpperCase().contains("CST")) {
            partyRole = "CST";
        }

        if (this.getOurIdentity().getName().getCommonName().toUpperCase().contains("DTCC")) {
            partyRole = "CLR";
        }

        this.productAsset = productAsset;
    }

    @Override
    public ProgressTracker getProgressTracker() {
        return progressTracker;
    }

    public String getPartyRole() {
        return partyRole;
    }

    @Suspendable
    @Override
    public SignedTransaction call() throws FlowException {
        // Step 1. Initialisation.
        progressTracker.setCurrentStep(ProductProgressTracker.INITIALISING);

        ProductObligation obligation = createObligation();

        //   final Amount<Currency> amount = new Amount<Currency>(100,Currency.getInstance("GBP")); //TODO obtain amount from RPC

        final PublicKey ourSigningKey = obligation.getBorrower().getOwningKey();

        // Step 2. Building.
        progressTracker.setCurrentStep(ProductProgressTracker.BUILDING);
        final List<PublicKey> requiredSigners = obligation.getParticipantKeys();

        final TransactionBuilder utx = new TransactionBuilder(getFirstNotary())
                .addOutputState(obligation, ProductContract.OBLIGATION_CONTRACT_ID)
                .addCommand(new ProductContract.Commands.BuyProposal(), requiredSigners)
                .setTimeWindow(getServiceHub().getClock().instant(), Duration.ofSeconds(30));

        // Step 3. Sign the transaction.
        progressTracker.setCurrentStep(ProductProgressTracker.SIGNING);
        final SignedTransaction ptx = getServiceHub().signInitialTransaction(utx, ourSigningKey);

        // Step 4. Get the counter-party signature.
        progressTracker.setCurrentStep(ProductProgressTracker.COLLECTING);
        final FlowSession lenderFlow = initiateFlow(toParty);
        final SignedTransaction stx = subFlow(new CollectSignaturesFlow(
                ptx,
                ImmutableSet.of(lenderFlow),
                ImmutableList.of(ourSigningKey),
                CollectSignaturesFlow.tracker())
        );


        //Step 5. invoke to party for next processing
        FlowSession toPartySession = initiateFlow(toParty);
        UntrustworthyData<ProductAsset> receivedData = toPartySession.sendAndReceive(ProductAsset.class, productAsset);

        //ProductAsset receivedAsset = receivedData.getFromUntrustedWorld();

        // Step 5. Finalise the transaction.
        progressTracker.setCurrentStep(ProductProgressTracker.FINALISING);
        return subFlow(new FinalityFlow(stx, ProductProgressTracker.FINALISING.childProgressTracker()));

    }

    @Suspendable
    private ProductObligation createObligation() throws FlowException {
//
        if (anonymous) {
            final HashMap<Party, AnonymousParty> txKeys = subFlow(new SwapIdentitiesFlow(toParty));

            if (txKeys.size() != 2) {
                throw new IllegalStateException("Something went wrong when generating confidential identities.");
            } else if (!txKeys.containsKey(getOurIdentity())) {
                throw new FlowException("Couldn't create our conf. identity.");
            } else if (!txKeys.containsKey(toParty)) {
                throw new FlowException("Couldn't create lender's conf. identity.");
            }

            final AbstractParty anonymousLender = txKeys.get(toParty);
            final AbstractParty anonymousMe = txKeys.get(getOurIdentity());

            return new ProductObligation(productAsset, anonymousLender, anonymousMe, new UniqueIdentifier());
        } else {
            return new ProductObligation(productAsset, toParty, getOurIdentity(), new UniqueIdentifier());
        }
    }

    @InitiatedBy(ProductBuyInitiatorFlow.class)
    public static class Responder extends FlowLogic<SignedTransaction> {
        private final FlowSession otherFlow;

        public Responder(FlowSession otherFlow) {
            this.otherFlow = otherFlow;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            final SignedTransaction stx = subFlow(new ObligationBaseFlow.SignTxFlowNoChecking(otherFlow, SignTransactionFlow.Companion.tracker()));
            //TODO how to send back productAsset received from ToParty to fromParty

            return waitForLedgerCommit(stx.getId());
        }
    }
}
