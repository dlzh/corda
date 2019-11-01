package com.dz0189.flow

import co.paralleluniverse.fibers.Suspendable
import com.dz0189.trade.AssetContractNew
import com.dz0189.trade.AssetStateNew
import com.twig.contract.PrideContract
import com.twig.flow.BroadcastTransaction
import com.twig.state.Asset
import com.twig.state.PrideState
import com.twig.utility.Helpers.Companion.getIssuerParty
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import java.time.Duration

/**
* This is the flow which handles trading of new assets on the ledger.
* The flow is initialised by the seller (Smurfies) and aims to trade with the buyer (Devons)
* There is another counterparty (Twig) who will be receiving commissions.
* Remember to broadcast the transaction to an observer node called "regulator" using the
* BroadcastTransaction subflow.
*/
object AssetTradeFlow {
    @InitiatingFlow(version = 2)
    @StartableByRPC
    class Buyer(val assetAmount: Long,
                    val amountToPay: Long,
                    val otherParty: Party,
                val commission: Double = 0.05) : FlowLogic<SignedTransaction>() {

        companion object {
            object QUERYING_PRIDE_STATES: ProgressTracker.Step("Querying Pride states for transaction.")
            object SENDING_PAYLOAD: ProgressTracker.Step("Sending Payload to Seller.")

            fun tracker() = ProgressTracker(
                    QUERYING_PRIDE_STATES,
                    SENDING_PAYLOAD
            )
        }

        @CordaSerializable
        data class BuyerTradeInfo(
                val assetAmount: Long,
                val amountToPay: Long,
                val commission: Double
        )

        /** The progress tracker provides checkpoints indicating the progress of the flow to observers. */
        override val progressTracker = tracker()

        /** The flow logic is encapsulated within the call() method. */
        @Suspendable
        override fun call(): SignedTransaction {
            val maxRetries = 30
            // Query Pride States from Smurfie's vault.
            progressTracker.currentStep = QUERYING_PRIDE_STATES
            val queryCriteria = QueryCriteria.FungibleAssetQueryCriteria(owner = listOf(serviceHub.myInfo.legalIdentities.first()))
            val stateAndRef =  serviceHub.vaultService.queryBy<PrideState>(queryCriteria).states
            
            // Create a session with the other party. Then send the payload across to the other party.
            // Hint: use the SendStateAndRefFlow subflow.
            progressTracker.currentStep = SENDING_PAYLOAD
            val otherPartyFlow = initiateFlow(otherParty)
            subFlow(SendStateAndRefFlow(otherPartyFlow,stateAndRef))
            
            
            val hello = BuyerTradeInfo(assetAmount,amountToPay,commission)
            otherPartyFlow.send(hello)
            
            val signTransactionFlow = object : SignTransactionFlow(otherPartyFlow) {
                override fun checkTransaction(stx: SignedTransaction) {
                    val outputs = stx.tx.outputsOfType<PrideState>()
                }
            }

            return subFlow(signTransactionFlow)
        }
    }

    @InitiatingFlow
    @InitiatedBy(Buyer::class)
    class Seller(val otherPartyFlow: FlowSession) : FlowLogic<SignedTransaction>() {

        class NotEnoughAssetsException: FlowException("Not enough assets found.")
        class NotEnoughPridesException: FlowException("Not enough prides found.")

        companion object {
            object UNWRAPPING_PAYLOAD : ProgressTracker.Step("Unwrapping payload from Seller.")
            object QUERYING_ASSET_STATES: ProgressTracker.Step("Querying asset states for transaction.")
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction.")
            object GENERATING_ASSET_OUTPUTS : ProgressTracker.Step("Generating Asset outputs for Buyer and Seller.")
            object CHECKING_FOR_ASSET_CHANGE : ProgressTracker.Step("Generating Asset change for Buyer and Seller.")
            object GENERATING_PRIDE_OUTPUTS : ProgressTracker.Step("Generating Pride outputs for Buyer and Seller.")
            object CHECKING_FOR_PRIDE_CHANGE : ProgressTracker.Step("Generating Pride change for Buyer and Seller.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty's signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    UNWRAPPING_PAYLOAD,
                    QUERYING_ASSET_STATES,
                    GENERATING_TRANSACTION,
                    GENERATING_ASSET_OUTPUTS,
                    CHECKING_FOR_ASSET_CHANGE,
                    GENERATING_PRIDE_OUTPUTS,
                    CHECKING_FOR_PRIDE_CHANGE,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {
        
            // Unwrap payload received from Smurfies
            // Call receiveAndValidateTradeRequest helper function to unwrap payload.
            progressTracker.currentStep = UNWRAPPING_PAYLOAD
            val (prideList,tradeInfo) = receiveAndValidateTradeRequest()
            // Query for asset states from Devon vault.
            // Then call assembleSharedTx helper function to generate the transaction.
            progressTracker.currentStep = QUERYING_ASSET_STATES
            
            //DZ get owner information from serviceHub ...)
            val queryCriteria = QueryCriteria.FungibleAssetQueryCriteria(owner = listOf(serviceHub.myInfo.legalIdentities.first()))
            val stateAndRef =  serviceHub.vaultService.queryBy<AssetStateNew>(queryCriteria).states
            
            val txBuilder = assembleSharedTX(stateAndRef,tradeInfo.assetAmount,tradeInfo.amountToPay,tradeInfo.commission,prideList)
            
            
            // Sign transaction.
            progressTracker.currentStep = SIGNING_TRANSACTION
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)
            
            // Gather signatures from other parties.
            progressTracker.currentStep = GATHERING_SIGS
            val twig = getIssuerParty(serviceHub)
            val twigSession = initiateFlow(twig)
            
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, listOf(otherPartyFlow, twigSession)))
            
            
            // Finalise the transaction and broadcast it to the regulator.
            progressTracker.currentStep = FINALISING_TRANSACTION
            return fullySignedTx
        }

        /*
         * Helper function that receives the state from other party
         * Hint: use the ReceiveStateAndRefFlow subflow.
         */
        @Suspendable
        private fun receiveAndValidateTradeRequest(): Pair<List<StateAndRef<PrideState>>, Buyer.BuyerTradeInfo> {
            val assetForSale = subFlow(ReceiveStateAndRefFlow<PrideState>(otherPartyFlow))
            return assetForSale to otherPartyFlow.receive<Buyer.BuyerTradeInfo>().unwrap {it}
        }

        /*
         * Helper function that builds the transaction.
         */
        @Suspendable
        private fun assembleSharedTX(assets: List<StateAndRef<AssetStateNew>>, assetAmount: Long, amountToPay: Long, commission: Double, prides: List<StateAndRef<PrideState>>): TransactionBuilder {
           
            // Generate Asset outputs.
            // Hint: take advantage of the .copy() command available on Kotlin data classes.
            // This is available on the State and Amount classes.
            // Don't forget add the appropriate commands!
            progressTracker.currentStep = GENERATING_ASSET_OUTPUTS
            val notary = serviceHub.networkMapCache.notaryIdentities[0]
            val ptx = TransactionBuilder(notary)
            
            
            //FROM QUYEN
            // Account for Asset change if change exists.
            progressTracker.currentStep = CHECKING_FOR_ASSET_CHANGE

            // Generate Pride outputs.
            // Hint: take advantage of the .copy() command available on Kotlin data classes.
            // This is available on the State and Amount classes.
            progressTracker.currentStep = GENERATING_PRIDE_OUTPUTS

            // Account for Pride change if change exists.
            progressTracker.currentStep = CHECKING_FOR_PRIDE_CHANGE

            // Don't forget add the appropriate commands!
            
            
            val tx = TransactionBuilder(notary)
             
            val pPride = prides.first().state.data
            val pAsset = assets.first().state.data
            val amtPride = prides.map{it.state.data.amount.quantity}.sum()
            val change = amtPride - amountToPay 
            val amount1 = pPride.amount
            
            val amtAsset = assets.map{it.state.data.amount.quantity}.sum()
            val assetChange = amtAsset - assetAmount
            val astAmount = pAsset.amount
            
            val newPride = pPride.withNewOwnerAndAmount(amount1.copy(amountToPay),pAsset.owner)
            val newPrideChange = pPride.withNewOwnerAndAmount(amount1.copy(change),pPride.owner)
            val newAsset = pAsset.withNewOwnerAndAmount(astAmount.copy(pPride.owner), ).ownableState
            val newAssetChange = pAsset.withNewOwnerAndAmount(astAmount.copy(astAmount, pAsset.owner)
            
            val assetKeys = pAsset.participants.map {it.owningKey}
            val prideKeys = pPride.participants.map {it.owningKey}
            val twig = getIssuerParty(serviceHub).owningKey
            val signers = prideKeys +  twig
            
            val assetCommand = Command(AssetContractNew.Commands.Trade(), assetKeys)
            val prideCommand = Command(PrideContract.Commands.Transfer(),signers)
            
            assets.map{tx.addInputState(it)}
            prides.map{tx.addInputState(it)}
            
            tx.addOutputState(newPride, PrideContract.PRIDE_CONTRACT_ID)
                .addOutputState(newPrideChange, PrideContract.PRIDE_CONTRACT_ID)
                .addOutputState(newAsset, AssetContractNew.ID)
                .addOutputState(newAssetChange, AssetContractNew.ID)
                .addCommand(assetCommand)
                .addCommand(prideCommand)
           
             return tx

        }
    }

    @InitiatedBy(Seller::class)
    class Commission(val otherPartyFlow: FlowSession) : FlowLogic<SignedTransaction>() {
        /** The flow logic is encapsulated within the call() method. */
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartyFlow) {
                override fun checkTransaction(stx: SignedTransaction) {
                    val outputs = stx.tx.outputsOfType<PrideState>()
                }
            }

            return subFlow(signTransactionFlow)
        }
    }
}