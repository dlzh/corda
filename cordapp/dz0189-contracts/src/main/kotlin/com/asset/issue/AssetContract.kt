package com.dz0189.issue

import com.twig.state.Asset
import com.dz0189.issue.AssetContract
import com.dz0189.issue.AssetState
import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey
import com.twig.flow.BroadcastTransaction
import net.corda.core.contracts.Command

/**
 * This is where you'll add the contract code which defines how the [AssetState] behaves. Looks at the unit tests in
 * [AssetContractTests] for instructions on how to complete the [AssetContract] class.
 */
class AssetContract : Contract {
    // This is used to identify our contract when building a transaction
    companion object {
        val ID = "com.dz0189.issue.AssetContract"
    }

    /**
     * The contract code for the [AssetContract].
     * The constraints are self documenting so don't require any additional explanation.
     */
    override fun verify(tx: LedgerTransaction) {
        when (tx.commands.select(Commands::class.java).requireSingleCommand<Commands>().value) {
            is Commands.Issue -> verifyIssue(tx)
        }
    }

    private fun verifyIssue(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands.Issue>()
        requireThat {
            // Constraints on the shape of the transaction.
            "No inputs should be consumed when issuing an asset." using (tx.inputs.isEmpty()) //when the isEmpty() is false, then use the information.
            "Only one output state should be created when issuing an asset." using (tx.outputs.size == 1)
             val out = tx.outputsOfType<AssetState>()
            "A newly issued asset must have a positive amount." using (out.get(0).amount.quantity > 0)
            "Owner must sign asset issue transaction." using (command.signers.toSet() == out.get(0).participants.map{
                it.owningKey
            }.toSet())
            "Cannot issue more than 100 Assets at a time." using (out.get(0).amount.quantity <= 100)
        }
    }
    
    
    /**
     * Add any commands required for this contract as classes within this interface.
     * It is useful to encapsulate your commands inside an interface, so you can use the [requireSingleCommand]
     * function to check for a number of commands which implement this interface.
     */
    interface Commands : CommandData {
        class Issue : Commands
        class Trade : Commands
    }
}

/**
 * This is where you'll add the definition of your state object. Look at the unit tests in [AssetStateTests] for
 * instructions on how to complete the [AssetState] class.
 */
data class AssetState( override val amount: Amount<Issued<Asset>>, override val owner: AbstractParty) : FungibleAsset<Asset> {
    override var participants: List<AbstractParty> = listOf(owner)
    
    /*Add an 'amount' property of type [Amount] to the [AssetState] class to get this test to pass.
     * Hint: [Amount] is a template class that takes a class parameter of the token you would like an [Amount] of.
     * Use [Issued<Asset>] as the token for the [Amount] class.*/
     
      constructor(name: String, amount: Long, owner: AbstractParty):
         this(Amount<Issued<Asset>>(amount, Issued<Asset>(owner.ref(0), Asset(name))), owner)
         
    override fun withNewOwner(otherParty: AbstractParty): CommandAndState =
            CommandAndState(AssetContract.Commands.Trade(), AssetState(amount, otherParty))
            
    override fun withNewOwnerAndAmount(newAmount: Amount<Issued<Asset>>, newOwner: AbstractParty): FungibleAsset<Asset>  {
        return copy(owner = newOwner)
    }
    override val exitKeys :Collection<PublicKey> = listOf(owner.owningKey)
}