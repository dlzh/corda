
package com.dz0189.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import com.dz0189.issue.AssetState
import com.dz0189.issue.AssetContract
import net.corda.core.contracts.Command
import com.twig.flow.BroadcastTransaction


/**
* This is the flow which handles issuance of new assets on the ledger.
* Gathering the counterparty's signature is handled by the [CollectSignaturesFlow].
* However, this is a self issue flow so there is no counterparty.
* Instead, you will need to broadcast the transaction to an observer node called
* the "regulator" using the BroadcastTransaction subflow.
* Notarisation (if required) and commitment to the ledger is handled by the [FinalityFlow].
* The flow returns the [SignedTransaction] that was committed to the ledger.
*/
object AssetIssueFlow {
    @InitiatingFlow(version = 2)
    @StartableByRPC
    class Initiator(val amount: Long) : FlowLogic<SignedTransaction>() {
        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction.")
            object BUILD_TRANSACTION_COMPONENTS : ProgressTracker.Step("Building states and commands of transaction.")
            object ADD_COMPONENTS_TO_TRANSACTION : ProgressTracker.Step("Adding components to transaction.")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }
            object BROADCAST_TRANSACTION_TO_REGULATOR : ProgressTracker.Step("Broadcasting transaction to Regulator.")

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    BUILD_TRANSACTION_COMPONENTS,
                    ADD_COMPONENTS_TO_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    FINALISING_TRANSACTION,
                    BROADCAST_TRANSACTION_TO_REGULATOR
            )
        }

        /** The progress tracker provides checkpoints indicating the progress of the flow to observers. */
        override val progressTracker = tracker()

        /** The flow logic is encapsulated within the call() method. */
        @Suspendable
        override fun call(): SignedTransaction {
            // Placeholder code to avoid type error when running the tests. Remove before starting the flow task!
            //return serviceHub.signInitialTransaction(
            //        TransactionBuilder(notary = null)
            //)

            // We retgetServiceHubrieve the notary identity from the network map.
            progressTracker.currentStep = GENERATING_TRANSACTION
            val notary = serviceHub.networkMapCache.notaryIdentities[0]
           // We create a transaction builder and initialise it with a notary.
            val builder = TransactionBuilder(notary = notary)
            // We create the transaction components (i.e. the state, the commands)
            progressTracker.currentStep = BUILD_TRANSACTION_COMPONENTS
            val outputState : AssetState = AssetState(owner = serviceHub.myInfo.legalIdentities.first(), amount = amount, name= "asset" )
            val txCommand = Command(AssetContract.Commands.Issue(), outputState.participants.map { it.owningKey })
            // We add the items to the builder.
            progressTracker.currentStep = ADD_COMPONENTS_TO_TRANSACTION
            builder.addOutputState(outputState, AssetContract.ID)
            builder.addCommand(txCommand)
            // Verifying the transaction.
            progressTracker.currentStep = VERIFYING_TRANSACTION
            builder.verify(serviceHub);                             //DZ collectSignature flow -- get signature from all participants. 
            // Signing the transaction.
            progressTracker.currentStep = SIGNING_TRANSACTION       //DZ then,, then the anything before this mutable after 
            val partSignedTx = serviceHub.signInitialTransaction(builder)
            // We finalise the transaction.
            progressTracker.currentStep = FINALISING_TRANSACTION
            val finalTxn = subFlow(FinalityFlow(partSignedTx))
            
            
            //DZ: Broadcasting .. receipt just 
            
           //progressTracker.currentStep = BROADCAST_TRANSACTION_TO_REGULATOR
            subFlow(BroadcastTransaction(finalTxn))
            return finalTxn
        }
    }
}
