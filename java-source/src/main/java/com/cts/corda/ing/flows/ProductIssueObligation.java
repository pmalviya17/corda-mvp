package com.cts.corda.ing.flows;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.corda.confidential.SwapIdentitiesFlow;
import net.corda.core.contracts.Amount;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.*;
import net.corda.core.identity.AnonymousParty;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import net.corda.core.utilities.ProgressTracker.Step;
import com.cts.corda.ing.obligation.ObligationContract;
import com.cts.corda.ing.obligation.ProductAsset;
import com.cts.corda.ing.obligation.ProductObligation;

import java.security.PublicKey;
import java.time.Duration;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;

public class ProductIssueObligation {

    @InitiatingFlow
    @StartableByRPC
    public static class Initiator extends ObligationBaseFlow {
        private final Amount<Currency> amount;
        ;
        private final ProductAsset productAsset;
        ;
        private final Party lender;
        private final Boolean anonymous;
        private final Boolean isCash;

        private final Step INITIALISING = new Step("Performing initial steps.");
        private final Step BUILDING = new Step("Performing initial steps.");
        private final Step SIGNING = new Step("Signing transaction.");
        private final Step COLLECTING = new Step("Collecting counterparty signature.") {
            @Override
            public ProgressTracker childProgressTracker() {
                return CollectSignaturesFlow.Companion.tracker();
            }
        };

        private final Step FINALISING = new Step("Finalising transaction.") {
            @Override
            public ProgressTracker childProgressTracker() {
                return FinalityFlow.Companion.tracker();
            }
        };

        private final ProgressTracker progressTracker = new ProgressTracker(
                INITIALISING, BUILDING, SIGNING, COLLECTING, FINALISING
        );

        public Initiator(Amount<Currency> amount, Party lender, Boolean anonymous) {
            this.amount = amount;
            this.productAsset = null;
            this.isCash = true;
            this.lender = lender;
            this.anonymous = anonymous;
        }

        public Initiator(ProductAsset productAsset, Party lender, Boolean anonymous) {
            this.productAsset = productAsset;
            this.amount = null;
            this.isCash = false;
            this.lender = lender;
            this.anonymous = anonymous;
        }

        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            // Step 1. Initialisation.
            progressTracker.setCurrentStep(INITIALISING);
            final ProductObligation obligation = createProductObligation();
            final PublicKey ourSigningKey = obligation.getBorrower().getOwningKey();

            // Step 2. Building.
            progressTracker.setCurrentStep(BUILDING);
            final List<PublicKey> requiredSigners = obligation.getParticipantKeys();

            final TransactionBuilder utx = new TransactionBuilder(getFirstNotary())
                    .addOutputState(obligation, ObligationContract.OBLIGATION_CONTRACT_ID)
                    .addCommand(new ObligationContract.Commands.Issue(), requiredSigners)
                    .setTimeWindow(getServiceHub().getClock().instant(), Duration.ofSeconds(30));

            // Step 3. Sign the transaction.
            progressTracker.setCurrentStep(SIGNING);
            final SignedTransaction ptx = getServiceHub().signInitialTransaction(utx, ourSigningKey);

            // Step 4. Get the counter-party signature.
            progressTracker.setCurrentStep(COLLECTING);
            final FlowSession lenderFlow = initiateFlow(lender);
            final SignedTransaction stx = subFlow(new CollectSignaturesFlow(
                    ptx,
                    ImmutableSet.of(lenderFlow),
                    ImmutableList.of(ourSigningKey),
                    COLLECTING.childProgressTracker())
            );

            // Step 5. Finalise the transaction.
            progressTracker.setCurrentStep(FINALISING);
            return subFlow(new FinalityFlow(stx, FINALISING.childProgressTracker()));
        }

        @Suspendable
        private ProductObligation createProductObligation() throws FlowException {
            if (anonymous) {
                final HashMap<Party, AnonymousParty> txKeys = subFlow(new SwapIdentitiesFlow(lender));

                if (txKeys.size() != 2) {
                    throw new IllegalStateException("Something went wrong when generating confidential identities.");
                } else if (!txKeys.containsKey(getOurIdentity())) {
                    throw new FlowException("Couldn't create our conf. identity.");
                } else if (!txKeys.containsKey(lender)) {
                    throw new FlowException("Couldn't create lender's conf. identity.");
                }

                final AnonymousParty anonymousMe = txKeys.get(getOurIdentity());
                final AnonymousParty anonymousLender = txKeys.get(lender);

                if (isCash) {
                    return new ProductObligation(amount, anonymousLender, anonymousMe, null);
                } else {
                    return new ProductObligation(productAsset, anonymousLender, anonymousMe, new UniqueIdentifier());
                }

            } else {
                if (isCash) {
                    return new ProductObligation(amount, lender, getOurIdentity(), null);
                } else {
                    return new ProductObligation(productAsset, lender, getOurIdentity(), new UniqueIdentifier());
                }
            }
        }
    }

    @InitiatedBy(Initiator.class)
    public static class Responder extends FlowLogic<SignedTransaction> {
        private final FlowSession otherFlow;

        public Responder(FlowSession otherFlow) {
            this.otherFlow = otherFlow;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            final SignedTransaction stx = subFlow(new AbstractIssueFlow.SignTxFlowNoChecking(otherFlow, SignTransactionFlow.Companion.tracker()));
            return waitForLedgerCommit(stx.getId());
        }
    }
}