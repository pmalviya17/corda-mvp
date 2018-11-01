package com.cts.corda.ing.contract

import com.cts.corda.ing.flow.security.Security
import net.corda.core.contracts.*
import net.corda.core.contracts.Amount.Companion.sumOrThrow
import net.corda.core.contracts.Amount.Companion.sumOrZero
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.finance.contracts.asset.OnLedgerAsset
import java.security.PublicKey
import java.util.*


open class SecurityContract : OnLedgerAsset<Security, SecurityContract.Commands, SecurityContract.State>() {
    companion object {
        // Just a fake program identifier for now. In a real system it could be, for instance, the hash of the program bytecode.
        val SECURITY_PROGRAM_ID = "com.cts.corda.ing.contract.SecurityContract"
    }

    /** A state representing a security claim against some party */
    data class State(
            override val amount: Amount<Issued<Security>>,
            /** There must be a MoveCommand signed by this key to claim the amount */
            override val owner: AbstractParty
    ) : FungibleAsset<Security> {
        constructor(deposit: PartyAndReference, amount: Amount<Security>, owner: AbstractParty)
                : this(Amount(amount.quantity, Issued(deposit, amount.token)), owner)

        override val exitKeys: Set<PublicKey> = Collections.singleton(owner.owningKey)
        override val participants = listOf(owner)

        override fun withNewOwnerAndAmount(newAmount: Amount<Issued<Security>>, newOwner: AbstractParty): FungibleAsset<Security> = copy(amount = amount.copy(newAmount.quantity), owner = newOwner)

        override fun toString() = "Security($amount at ${amount.token.issuer} owned by $owner)"

        override fun withNewOwner(newOwner: AbstractParty) = CommandAndState(Commands.Move(), copy(owner = newOwner))
    }

    // Just for grouping
    @CordaSerializable
    interface Commands : CommandData {
        /**
         * A command stating that money has been moved, optionally to fulfil another contract.
         *
         * @param contract the contract this move is for the attention of. Only that contract's verify function
         * should take the moved states into account when considering whether it is valid. Typically this will be
         * null.
         */
        data class Move(override val contract: Class<out Contract>? = null) : MoveCommand

        /**
         * Allows new security states to be issued into existence.
         */
        class Issue : TypeOnlyCommandData()

        /**
         * A command stating that money has been withdrawn from the shared ledger and is now accounted for
         * in some other way.
         */
        data class Exit(val amount: Amount<Issued<Security>>) : CommandData
    }

    override fun verify(tx: LedgerTransaction) {
        // Each group is a set of input/output states with distinct (reference, security) attributes. These types
        // of security are not fungible and must be kept separated for bookkeeping purposes.
        val groups = tx.groupStates { it: State -> it.amount.token }

        for ((inputs, outputs, key) in groups) {
            // Either inputs or outputs could be empty.
            val issuer = key.issuer
            val security = key.product
            val party = issuer.party

            requireThat {
                "there are no zero sized outputs" using (outputs.none { it.amount.quantity == 0L })
            }

            val issueCommand = tx.commands.select<Commands.Issue>().firstOrNull()
            if (issueCommand != null) {
                verifyIssueCommand(inputs, outputs, tx, issueCommand, security, issuer)
            } else {
                val inputAmount = inputs.sumSecurities()
                        ?: throw IllegalArgumentException("there is at least one security input for this group")
                val outputAmount = outputs.sumSecuritiesOrZero(Issued(issuer, security))

                // If we want to remove security from the ledger, that must be signed for by the issuer.
                // A mis-signed or duplicated exit command will just be ignored here and result in the exit amount being zero.
                val exitCommand = tx.commands.select<Commands.Exit>(party = party).singleOrNull()
                val amountExitingLedger = exitCommand?.value?.amount ?: Amount(0, Issued(issuer, security))

                requireThat {
                    "there are no zero sized inputs" using (inputs.none { it.amount.quantity == 0L })
                    "for reference ${issuer.reference} at issuer ${party.nameOrNull()} the amounts balance" using
                            (inputAmount == outputAmount + amountExitingLedger)
                }

                verifyMoveCommand<Commands.Move>(inputs, tx.commands)
            }
        }
    }

    private fun verifyIssueCommand(inputs: List<State>,
                                   outputs: List<State>,
                                   tx: LedgerTransaction,
                                   issueCommand: CommandWithParties<Commands.Issue>,
                                   security: Security,
                                   issuer: PartyAndReference) {
        val inputAmount = inputs.sumSecuritiesOrZero(Issued(issuer, security))
        val outputAmount = outputs.sumSecurities()
        val securityCommands = tx.commands.select<Commands>()
        requireThat {
            "output deposits are owned by a command signer" using (issuer.party in issueCommand.signingParties)
            "output values sum to more than the inputs" using (outputAmount > inputAmount)
            "there is only a single issue command" using (securityCommands.count() == 1)
        }
    }

    override fun extractCommands(commands: Collection<CommandWithParties<CommandData>>): List<CommandWithParties<Commands>> = commands.select<Commands>()

    /**
     * Puts together an issuance transaction from the given template, that starts out being owned by the given pubkey.
     */
    fun generateIssue(tx: TransactionBuilder, tokenDef: Issued<Security>, pennies: Long, owner: AbstractParty, notary: Party) = generateIssue(tx, Amount(pennies, tokenDef), owner, notary)

    /**
     * Puts together an issuance transaction for the specified amount that starts out being owned by the given pubkey.
     */
    fun generateIssue(tx: TransactionBuilder, amount: Amount<Issued<Security>>, owner: AbstractParty, notary: Party) = generateIssue(tx, TransactionState(State(amount, owner), SECURITY_PROGRAM_ID, notary), Commands.Issue())


    public override fun deriveState(txState: TransactionState<State>, amount: Amount<Issued<Security>>, owner: AbstractParty) = txState.copy(data = txState.data.copy(amount = amount, owner = owner))

    override fun generateExitCommand(amount: Amount<Issued<Security>>) = Commands.Exit(amount)
    override fun generateMoveCommand() = Commands.Move()

    fun Iterable<ContractState>.sumSecuritiesOrZero(currency: Issued<Security>) = filterIsInstance<State>().map { it.amount }.sumOrZero(currency)

    fun Iterable<ContractState>.sumSecurities() = filterIsInstance<State>().map { it.amount }.sumOrThrow()
}
