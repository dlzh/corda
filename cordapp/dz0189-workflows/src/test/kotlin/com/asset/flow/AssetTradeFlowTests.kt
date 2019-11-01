package com.dz0189.flow

import com.dz0189.issue.AssetState
import com.dz0189.trade.AssetContractNew
import com.dz0189.trade.AssetStateNew
import com.twig.contract.PrideContract
import com.twig.flow.PrideIssueFlow
import com.twig.state.PrideState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.ContractUpgradeFlow
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.lang.Math.floor
import kotlin.test.assertEquals

/**
 * Practical exercise instructions for flows part 2.
 * Uncomment the unit tests and use the hints + unit test body to complete the flows such that the unit tests pass.
 */
class AssetTradeFlowTests {

    lateinit var network: MockNetwork
    lateinit var ownerNode: StartedMockNode
    lateinit var smurfiesNode: StartedMockNode
    lateinit var twigNode: StartedMockNode
    lateinit var regulatorNode: StartedMockNode
    lateinit var owner: Party
    lateinit var smurfies: Party
    lateinit var twig: Party
    lateinit var regulator: Party

    @Before
    fun setup() {
        network = MockNetwork(
                listOf("com.twig", "com.dz0189")
        )
        ownerNode = network.createPartyNode()
        smurfiesNode = network.createPartyNode()
        twigNode = network.createPartyNode(CordaX500Name.parse("O=Twig,L=Manila,C=PH"))
        regulatorNode = network.createPartyNode(CordaX500Name.parse("O=Regulator,L=Manila,C=PH"))
        owner = ownerNode.info.singleIdentity()
        smurfies = smurfiesNode.info.singleIdentity()
        twig = twigNode.info.singleIdentity()
        regulator = regulatorNode.info.singleIdentity()
        // For real nodes this happens automatically, but we have to manually register the flow for tests.
        listOf(ownerNode, smurfiesNode, twigNode, regulatorNode).forEach {
            it.registerInitiatedFlow(AssetTradeFlow.Seller::class.java)
            it.registerInitiatedFlow(PrideIssueFlow.Acceptor::class.java)
        }
        network.runNetwork()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    /*
     * Helper function to upgrade AssetState to AssetStateNew.
     * Since [AssetIssueFlow] creates states of type [AssetState]  (and thus, references [AssetContract],
     * we must upgrade them to [AssetStateNew] before we can trade them.
     */
    private fun upgradeAssetStateToAssetStateNew(stateAndRef: StateAndRef<AssetState>) {
        // Authorise AssetState Upgrade
        val contractAuthoriseFlow = ContractUpgradeFlow.Authorise(stateAndRef, AssetContractNew::class.java)
        val contractAuthoriseFuture = ownerNode.startFlow(contractAuthoriseFlow)
        network.runNetwork()
        contractAuthoriseFuture.getOrThrow()

        // Upgrade AssetState to AssetStateNew
        val contractUpgradeFlow = ContractUpgradeFlow.Initiate(stateAndRef, AssetContractNew::class.java)
        val contractUpgradeFuture = ownerNode.startFlow(contractUpgradeFlow)
        network.runNetwork()
        contractUpgradeFuture.getOrThrow()
    }

    /**
     * Task 1.
     * Build out the [AssetTradeFlow]!
     * TODO: Implement the [AssetTradeFlow] flow which builds and returns a partially [SignedTransaction].
     * Hint:
     * - As before, there are a lot of things you need to do to get this unit test to pass. The overall steps
     *   behind the flow are the same: you need to generate a transaction, sign it and submit it. See the AssetTradeFlow
     *   for an overview.
     */
    @Test
    fun flowReturnsCorrectlyFormedPartiallySignedTransaction() {
        val prideAmount: Long = 1000
        val assetAmount: Long = 100
        val commission: Double = 0.0

        // Issue Prides
        val prideIssueFlow = PrideIssueFlow.Initiator(prideAmount, smurfies)
        val prideIssueFuture = twigNode.startFlow(prideIssueFlow)
        network.runNetwork()
        prideIssueFuture.getOrThrow()

        // Issue Assets
        val assetIssueFlow = AssetIssueFlow.Initiator(assetAmount)
        val assetIssueFuture = ownerNode.startFlow(assetIssueFlow)
        network.runNetwork()
        val stateAndRef = assetIssueFuture.getOrThrow().tx.outRefsOfType<AssetState>().single()
        
        upgradeAssetStateToAssetStateNew(stateAndRef)

        val flow = AssetTradeFlow.Buyer(assetAmount, prideAmount, owner, commission)
        val future = smurfiesNode.startFlow(flow)
        network.runNetwork()
        // Return the unsigned(!) SignedTransaction object from the AssetTradeFlow.
        val ptx: SignedTransaction = future.getOrThrow()
        // Print the transaction for debugging purposes.
        println("WHATIS THE COMMAND?" + ptx.tx) 
        val commands = ptx.tx.commands
        val tradeCommand = commands.filter { it.value is AssetContractNew.Commands.Trade }.single()
        val transferCommand = commands.filter { it.value is PrideContract.Commands.Transfer }.single()
        assert(tradeCommand.signers.toSet() == setOf(owner.owningKey))
        assert(transferCommand.signers.toSet() == setOf(smurfies.owningKey, twig.owningKey))
        ptx.verifySignaturesExcept(smurfies.owningKey, twig.owningKey,
                network.defaultNotaryNode.info.legalIdentitiesAndCerts.first().owningKey)
    }
    /**
     * Task 2.
     * TODO: Implement asset trade for the simple case where owner and smurfies trade the full amount of assets and prides.
     * Hint:
     * - We will test that the output (owner) Pride amount is equal to the input (smurfies) Pride amount
     * - We will test that the output (smurfies) Asset amount is equal to the input (owner) Asset amount
     * - [AssetContractNew] already checks that no new Prides or Assets are created so we do not need to check that.
     */
    @Test
    fun flowCorrectlyConductsAssetTradeAssumingNoChange() {
        val prideAmount: Long = 1000
        val assetAmount: Long = 100
        val commission: Double = 0.0

        // Issue Prides
        val prideIssueFlow = PrideIssueFlow.Initiator(prideAmount, smurfies)
        val prideIssueFuture = twigNode.startFlow(prideIssueFlow)
        network.runNetwork()
        prideIssueFuture.getOrThrow()

        // Issue Assets
        val assetIssueFlow = AssetIssueFlow.Initiator(assetAmount)
        val assetIssueFuture = ownerNode.startFlow(assetIssueFlow)
        network.runNetwork()
        val stateAndRef = assetIssueFuture.getOrThrow().tx.outRefsOfType<AssetState>().single()

        upgradeAssetStateToAssetStateNew(stateAndRef)

        // Conduct Asset Trade
        val assetTradeFlow = AssetTradeFlow.Buyer(assetAmount = assetAmount,
                amountToPay = prideAmount, otherParty = owner, commission = commission)
        val assetTradeFuture = smurfiesNode.startFlow(assetTradeFlow)
        network.runNetwork()
        val tx = assetTradeFuture.getOrThrow().tx

        val outputPrides = tx.outputsOfType<PrideState>()
        val outputAssets = tx.outputsOfType<AssetStateNew>()

        // no change, so owner should receive all the prides
        val ownerPrides = outputPrides.filter { it.owner.owningKey == owner.owningKey }
        assert(ownerPrides.size == 1)
        assert(ownerPrides.single().amount.quantity == prideAmount)

        // no change, so smurfies should receive all of the assets
        val smurfiesAssets = outputAssets.filter { it.owner.owningKey == smurfies.owningKey }
        assert(smurfiesAssets.size == 1)
        assert(smurfiesAssets.single().amount.quantity == assetAmount)
    }

    /**
     * Task 3.
     * Now that we have done the easy case of assuming no change, we want to extend this so that it works even if there is change.
     * Change occurs when the amount of assets bought is smaller than the size of the asset state passed. (Read about UTXOs).
     * e.g. Smurfies is issued 100 Prides as a single state. Smurfies buys 1 Asset for 10 Prides. As per the UTXO model,
     * the Devon will receive 10 Prides and the Smurfies will receive 90 Prides.
     * TODO: Implement asset trade for the case where there is no Asset change but there is change for Prides.
     */
    @Test
    fun flowCorrectlyConductsAssetTradeAssumingPrideChange() {
       val prideAmount: Long = 1000
      val amountToPay: Long = 400
        val assetAmount: Long = 100
        val commission: Double = 0.0

        // Issue Prides
        val prideIssueFlow = PrideIssueFlow.Initiator(prideAmount, smurfies)
        val prideIssueFuture = twigNode.startFlow(prideIssueFlow)
        network.runNetwork()
        prideIssueFuture.getOrThrow()

        // Issue Assets
        val assetIssueFlow = AssetIssueFlow.Initiator(assetAmount)
        val assetIssueFuture = ownerNode.startFlow(assetIssueFlow)
        network.runNetwork()
        val stateAndRef = assetIssueFuture.getOrThrow().tx.outRefsOfType<AssetState>().single()

        upgradeAssetStateToAssetStateNew(stateAndRef)

        // Conduct Asset Trade
        val assetTradeFlow = AssetTradeFlow.Buyer(assetAmount = assetAmount,
                amountToPay = amountToPay, otherParty = owner, commission = commission)
        val assetTradeFuture = smurfiesNode.startFlow(assetTradeFlow)
        network.runNetwork()
        val tx = assetTradeFuture.getOrThrow().tx

        val outputPrides = tx.outputsOfType<PrideState>()
        val outputAssets = tx.outputsOfType<AssetStateNew>()

        // there is change, so both owner and Smurfies must receive Prides
        val ownerPrides = outputPrides.filter { it.owner.owningKey == owner.owningKey }
        val smurfiesPrides = outputPrides.filter { it.owner.owningKey == smurfies.owningKey }
        assert(ownerPrides.size == 1)
        assert(ownerPrides.single().amount.quantity == amountToPay)
        assert(smurfiesPrides.size == 1)
        assert(smurfiesPrides.single().amount.quantity == (prideAmount - amountToPay))

        // no change, so smurfies should receive all of the assets
        // as there is no change, there should be only one state
        val smurfiesAssets = outputAssets.filter { it.owner.owningKey == smurfies.owningKey }
        assert(smurfiesAssets.size == 1)
        assert(smurfiesAssets.single().amount.quantity == assetAmount)
    }

    /**
     * Task 4.
     * This is the same as task 3 except we will now account for change in Assets.
     * TODO: Implement asset trade for the case where there is no Pride change but there is change for Assets.
     */
    @Test
    fun flowCorrectlyConductsAssetTradeAssumingAssetChange() {
        val prideAmount: Long = 1000
        val assetAmount: Long = 100
        val assetAmountToBuy: Long = 21
        val commission = 0.0

        // Issue Prides
        val prideIssueFlow = PrideIssueFlow.Initiator(prideAmount, smurfies)
        val prideIssueFuture = twigNode.startFlow(prideIssueFlow)
        network.runNetwork()
        prideIssueFuture.getOrThrow()

        // Issue Assets
        val assetIssueFlow = AssetIssueFlow.Initiator(assetAmount)
        val assetIssueFuture = ownerNode.startFlow(assetIssueFlow)
        network.runNetwork()
        val stateAndRef = assetIssueFuture.getOrThrow().tx.outRefsOfType<AssetState>().single()

        upgradeAssetStateToAssetStateNew(stateAndRef)

        // Conduct Asset Trade
        val assetTradeFlow = AssetTradeFlow.Buyer(assetAmount = assetAmountToBuy,
                amountToPay = prideAmount, otherParty = owner, commission = commission)
        val assetTradeFuture = smurfiesNode.startFlow(assetTradeFlow)
     network.runNetwork()
        val tx = assetTradeFuture.getOrThrow().tx

        val outputPrides = tx.outputsOfType<PrideState>()
        val outputAssets = tx.outputsOfType<AssetStateNew>()

        // no change, so owner should receive all the prides
        // as there is no change, there should be only one state
        val ownerPrides = outputPrides.filter { it.owner.owningKey == owner.owningKey }
        assert(ownerPrides.size == 1)
        assert(ownerPrides.single().amount.quantity == prideAmount)

        // there is change, so both owner and Smurfies must receive assets
        val smurfiesAssets = outputAssets.filter { it.owner.owningKey == smurfies.owningKey }
        val ownerAssets = outputAssets.filter { it.owner.owningKey == owner.owningKey }
        assert(smurfiesAssets.size == 1)
        assert(smurfiesAssets.single().amount.quantity == assetAmountToBuy)
        assert(ownerAssets.size == 1)
        assert(ownerAssets.single().amount.quantity == (assetAmount - assetAmountToBuy))
    }

    /**
     * Task 5.
     * Implement commissions.
     * TODO: Modify your current flow such that it allows for commissions in the simple case of no change.
     * Commissions is passed in as a variable to the flow. Commissions are based off the total value of the assets.
     * E.g. if there are 10 Assets worth 100 Prides each with commissions of 5% (0.05), total commissions will be 5 Prides.
     * The remaining 95 Prides goes to the owner (i.e. you, the developer). The commission is given to the Twig node.
     * Commissions are always rounded down (floor), so if those 10 Assets were only worth 10 Prides each. If commissions are 5%,
     * then commissions will be equal to 10 * 0.05 = 0.5 which will be rounded down to 0.
     * Hint:
     * - Make use of kotlin's data class "copy" function to modify amounts
     * - withNewOwnerAndAmount will also be helpful for you to modify states
     * - Ensure your commission structure works for any value (not just 5%!)
     */
//    @Test
//    fun flowCorrectlyConductsAssetTradeWithCommissionAssumingNoChange() {
//        val prideAmount: Long = 1000
//        val assetAmount: Long = 100
//        val commission = 0.09
//
//        // Issue Prides
//        val prideIssueFlow = PrideIssueFlow.Initiator(prideAmount, smurfies)
//        val prideIssueFuture = twigNode.startFlow(prideIssueFlow)
//        network.runNetwork()
//        prideIssueFuture.getOrThrow()
//
//        // Issue Assets
//        val assetIssueFlow = AssetIssueFlow.Initiator(assetAmount)
//        val assetIssueFuture = ownerNode.startFlow(assetIssueFlow)
//        network.runNetwork()
//        val stateAndRef = assetIssueFuture.getOrThrow().tx.outRefsOfType<AssetState>().single()
//
//        upgradeAssetStateToAssetStateNew(stateAndRef)
//
//        // Conduct Asset Trade
//        val assetTradeFlow = AssetTradeFlow.Buyer(assetAmount = assetAmount,
//                amountToPay = prideAmount, otherParty = owner, commission = commission)
//        val assetTradeFuture = smurfiesNode.startFlow(assetTradeFlow)
//        network.runNetwork()
//        val tx = assetTradeFuture.getOrThrow().tx
//
//        val outputPrides = tx.outputsOfType<PrideState>()
//        val outputAssets = tx.outputsOfType<AssetStateNew>()
//
//        val prideCommission = (floor(prideAmount * commission)).toLong()
//        val leftover = (prideAmount - prideCommission)
//
//        val ownerPrides = outputPrides.filter { it.owner.owningKey == owner.owningKey }
//        val twigPrides = outputPrides.filter { it.owner.owningKey == twig.owningKey }
//        assert(ownerPrides.size == 1)
//        assert(ownerPrides.single().amount.quantity == leftover)
//        assert(twigPrides.size == 1)
//        assert(twigPrides.single().amount.quantity == prideCommission)
//
//        // no change, so smurfies should receive all of the assets
//        val smurfiesAssets = outputAssets.filter { it.owner.owningKey == smurfies.owningKey }
//        assert(smurfiesAssets.size == 1)
//        assert(smurfiesAssets.single().amount.quantity == assetAmount)
//    }

    /**
     * Task 6.
     * This is the same as task 5, but now with Pride change.
     * TODO: Modify your current flow such that it allows for commissions in the case with Pride change.
     */
//    @Test
//    fun flowCorrectlyConductsAssetTradeWithCommissionAssumingPrideChange() {
//        val prideAmount: Long = 1000
//        val amountToPay: Long = 400
//        val assetAmount: Long = 100
//        val commission = 0.09
//
//        // Issue Prides
//        val prideIssueFlow = PrideIssueFlow.Initiator(prideAmount, smurfies)
//        val prideIssueFuture = twigNode.startFlow(prideIssueFlow)
//        network.runNetwork()
//        prideIssueFuture.getOrThrow()
//
//        // Issue Assets
//        val assetIssueFlow = AssetIssueFlow.Initiator(assetAmount)
//        val assetIssueFuture = ownerNode.startFlow(assetIssueFlow)
//        network.runNetwork()
//        val stateAndRef = assetIssueFuture.getOrThrow().tx.outRefsOfType<AssetState>().single()
//
//        upgradeAssetStateToAssetStateNew(stateAndRef)
//
//        // Conduct Asset Trade
//        val assetTradeFlow = AssetTradeFlow.Buyer(assetAmount = assetAmount,
//                amountToPay = amountToPay, otherParty = owner, commission = commission)
//        val assetTradeFuture = smurfiesNode.startFlow(assetTradeFlow)
//        network.runNetwork()
//        val tx = assetTradeFuture.getOrThrow().tx
//
//        val outputPrides = tx.outputsOfType<PrideState>()
//        val outputAssets = tx.outputsOfType<AssetStateNew>()
//
//        val prideCommission = floor(amountToPay.times(commission)).toLong()
//        val leftover = amountToPay - prideCommission
//        val change = prideAmount - prideCommission - leftover
//
//        val ownerPrides = outputPrides.filter { it.owner.owningKey == owner.owningKey }
//        val smurfiesPrides = outputPrides.filter { it.owner.owningKey == smurfies.owningKey }
//        val twigPrides = outputPrides.filter { it.owner.owningKey == twig.owningKey }
//        assert(ownerPrides.size == 1)
//        assert(ownerPrides.single().amount.quantity == leftover)
//        assert(smurfiesPrides.size == 1)
//        assert(smurfiesPrides.single().amount.quantity == change)
//        assert(twigPrides.size == 1)
//        assert(twigPrides.single().amount.quantity == prideCommission)
//
//        val smurfiesAssets = outputAssets.filter { it.owner.owningKey == smurfies.owningKey }
//        assert(smurfiesAssets.size == 1)
//        assert(smurfiesAssets.single().amount.quantity == assetAmount)
//    }

    /**
     * Task 7.
     * Now we have a well formed transaction, we need to properly verify it using the [AssetContractNew].
     * TODO: Amend the [AssetTradeFlow] to verify the transaction as well as sign it.
     */
//    @Test
//    fun flowReturnsVerifiedPartiallySignedTransaction() {
//        val prideAmount: Long = 1000
//        val amountToPay: Long = 400
//        val assetAmount: Long = 100
//        val commission = 0.0
//
//        val prideIssueFlow = PrideIssueFlow.Initiator(prideAmount, smurfies)
//        val prideIssueFuture = twigNode.startFlow(prideIssueFlow)
//        network.runNetwork()
//        prideIssueFuture.getOrThrow()
//
//        val assetIssueFlow = AssetIssueFlow.Initiator(assetAmount)
//        val assetIssueFuture = ownerNode.startFlow(assetIssueFlow)
//        network.runNetwork()
//        val stateAndRef = assetIssueFuture.getOrThrow().tx.outRefsOfType<AssetState>().single()
//
//        upgradeAssetStateToAssetStateNew(stateAndRef)
//
//        val flow = AssetTradeFlow.Buyer(assetAmount, amountToPay, owner, commission)
//        val future = smurfiesNode.startFlow(flow)
//        network.runNetwork()
//        val stx = future.getOrThrow()
//
//        val commands = stx.tx.commands
//        val tradeCommand = commands.filter { it.value is AssetContractNew.Commands.Trade }.single()
//        val transferCommand = commands.filter { it.value is PrideContract.Commands.Transfer }.single()
//        assert(tradeCommand.signers.toSet() == setOf(owner.owningKey))
//        assert(transferCommand.signers.toSet() == setOf(smurfies.owningKey, twig.owningKey))
//
//        // full transaction from seller will have all signatures
//        val ftx = ownerNode.services.validatedTransactions.getTransaction(stx.id)
//        ftx?.verifyRequiredSignatures()
//    }

    /**
     * Task 8.
     * TODO: Amend the [AssetTradeFlow] by adding a call to [FinalityFlow].
     * Hint:
     * - This task is the same as asset issue flow.
     * - We must still broadcast the transaction to the regulator as we did in the asset issue flow.
     * - We must also pass the transaction to twig (how else will they receive their commissions). Make sure
     *   you initialise a flow session for Twig and pass it to Twig as well.
     */
//    @Test
//    fun flowRecordsTheSameTransactionInBothPartyVaults() {
//        val prideAmount: Long = 1000
//        val amountToPay: Long = 400
//        val assetAmount: Long = 100
//        val commission = 0.0
//
//        val prideIssueFlow = PrideIssueFlow.Initiator(prideAmount, smurfies)
//        val prideIssueFuture = twigNode.startFlow(prideIssueFlow)
//        network.runNetwork()
//        prideIssueFuture.getOrThrow()
//
//        val assetIssueFlow = AssetIssueFlow.Initiator(assetAmount)
//        val assetIssueFuture = ownerNode.startFlow(assetIssueFlow)
//        network.runNetwork()
//        val stateAndRef = assetIssueFuture.getOrThrow().tx.outRefsOfType<AssetState>().single()
//
//        upgradeAssetStateToAssetStateNew(stateAndRef)
//
//        val flow = AssetTradeFlow.Buyer(assetAmount, amountToPay, owner, commission)
//        val future = smurfiesNode.startFlow(flow)
//        network.runNetwork()
//        val stx = future.getOrThrow()
//        println("Signed transaction hash: ${stx.id}")
//        // check that the devon, smurfies, twig and regulator have all received the transaction
//        listOf(ownerNode, smurfiesNode, twigNode, regulatorNode).map {
//            it.services.validatedTransactions.getTransaction(stx.id)
//        }.forEach {
//            val txHash = (it as SignedTransaction).id
//            println("$txHash == ${stx.id}")
//            assertEquals(stx.id, txHash)
//        }
//    }
}
