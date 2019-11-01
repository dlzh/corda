package com.dz0189.trade

import com.dz0189.issue.AssetState
import com.twig.state.Asset
import com.twig.state.PrideState
import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

/**
 * This is where you'll add the contract code which defines how the [AssetStateNew] behaves. Looks at the unit tests in
 * [AssetContractNewTests] for instructions on how to complete the [AssetContractNew] class.
 * You can copy your old verifyIssue function. Make sure it is using the new state type though!
 */
class AssetContractNew : UpgradedContractWithLegacyConstraint<AssetState, AssetStateNew> {
    // This is used to identify our contract when building a transaction
    companion object {
        val ID = "com.dz0189.trade.AssetContractNew"
    }

    override val legacyContract = "com.dz0189.issue.AssetContract"
    override val legacyContractConstraint: AttachmentConstraint
                get() = AlwaysAcceptAttachmentConstraint

    override fun upgrade(state: AssetState) = AssetStateNew(state.amount, state.owner)

    // A transaction is considered valid if the verify() function of the contract of each of the transaction's input
    // and output states does not throw an exception.
    override fun verify(tx: LedgerTransaction) {
        // Verification logic goes here.

        when (tx.commands.select(Commands::class.java).requireSingleCommand<Commands>().value) {
            is Commands.Issue -> verifyIssue(tx)
            is Commands.Trade -> verifyTrade(tx)
        }
    }

    private fun verifyIssue(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands.Issue>()
        requireThat {
            "No inputs should be consumed when issuing an asset." using (tx.inputs.size == 0)
            "Only one output state should be created when issuing an asset." using (tx.outputs.size == 1)
            
            val out = tx.outputsOfType<AssetStateNew>()
            "A newly issued asset must have a positive amount." using (out.get(0).amount.quantity > 0)
            "Owner must sign asset issue transaction." using 
            (command.signers.toSet() == out.get(0).participants.map{it.owningKey}.toSet())
            "Cannot issue more than 100 Assets at a time." using (out.get(0).amount.quantity <= 100)
            
           
        } 
    }

    private fun verifyTrade(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands.Trade>()
        requireThat {
             val outputPrides = tx.outputsOfType<PrideState>()
            val outputPridesKeys = outputPrides.map{it.owner.owningKey}.toSet()
            
            val inputAssets = tx.inputsOfType<AssetStateNew>()
            val inputAssetKey = inputAssets.first().owner.owningKey
            "There must be output Prides paid to the recipient." using outputPridesKeys.contains(inputAssetKey)
            
            val outputAssets = tx.outputsOfType<AssetStateNew>()
            val outputAssetKey = tx.outputsOfType<AssetStateNew>().first().owner.owningKey
            val summerfyKey = tx.inputsOfType<PrideState>().first().owner.owningKey  
            
            "There must be output Assets paid to the recipient." using (outputAssetKey == summerfyKey)
            
            val outputAmount = outputAssets.map{it.amount.quantity}.sum()
            val inputAmount = inputAssets.map{it.amount.quantity}.sum()
             "Total number of input Assets must equal total number of output Assets." using (outputAmount == inputAmount)
        }
    }

    // Used to indicate the transaction's intent.
    interface Commands : CommandData {
        class Issue : Commands
        class Trade: Commands
    }
}

/**
 * This is where you'll add the definition of your state object. Look at the unit tests in [AssetStateNewTests] for
 * instructions on how to complete the [AssetStateNew] class.
 * You can copy your old AssetState. Make sure it is using the [AssetStateNew] though!
 */
data class AssetStateNew( override val amount: Amount<Issued<Asset>>, override val owner: AbstractParty) : FungibleAsset<Asset> {
    override var participants: List<AbstractParty> = listOf(owner)
    
    /*Add an 'amount' property of type [Amount] to the [AssetStateNew] class to get this test to pass.
     * Hint: [Amount] is a template class that takes a class parameter of the token you would like an [Amount] of.
     * Use [Issued<Asset>] as the token for the [Amount] class.*/
     
      constructor(name: String, amount: Long, owner: AbstractParty):
         this(Amount<Issued<Asset>>(amount, Issued<Asset>(owner.ref(0), Asset(name))), owner)
         
   //override fun withNewOwner(otherParty: AbstractParty): CommandAndState =
    override fun withNewOwner(newOwner: AbstractParty): CommandAndState =
            CommandAndState(AssetContractNew.Commands.Trade(), AssetStateNew(amount, newOwner
        ))
            
    override fun withNewOwnerAndAmount(newAmount: Amount<Issued<Asset>>, newOwner: AbstractParty): FungibleAsset<Asset>  {
        return copy(owner = newOwner)
    }
    override val exitKeys :Collection<PublicKey> = listOf(owner.owningKey)
}