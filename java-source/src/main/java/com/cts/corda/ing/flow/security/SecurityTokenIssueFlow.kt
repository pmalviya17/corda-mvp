package com.cts.corda.ing.flow.security

import co.paralleluniverse.fibers.Suspendable
import com.cts.corda.ing.contract.SecurityContract
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Issued
import net.corda.core.contracts.PartyAndReference
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.NotaryException
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.OpaqueBytes
import net.corda.finance.flows.CashException


@StartableByRPC
class SecurityTokenIssueFlow(private val amount: Amount<Issued<Security>>,
                             private val issuerBankPartyRef: OpaqueBytes,
                             private val notary: Party) : FlowLogic<SignedTransaction>() {


    @Suspendable
    override fun call(): SignedTransaction {
        val builder = TransactionBuilder(notary)
        val issuer = ourIdentity.ref(issuerBankPartyRef)
        val signers = SecurityContract().generateIssue(builder, amount.issuedBy(issuer), ourIdentity, notary)
        val tx = serviceHub.signInitialTransaction(builder, signers)
        // There is no one to send the tx to as we're the only participants
        val notarised = finaliseTx(tx, emptySet(), "Unable to notarise issue")
        return notarised
    }

    infix fun Amount<Issued<Security>>.issuedBy(deposit: PartyAndReference) = Amount(quantity, displayTokenSize, token)

    @Suspendable
    protected fun finaliseTx(tx: SignedTransaction, extraParticipants: Set<Party>, message: String): SignedTransaction {
        try {
            return subFlow(FinalityFlow(tx, extraParticipants))
        } catch (e: NotaryException) {
            throw CashException(message, e)
        }
    }
}