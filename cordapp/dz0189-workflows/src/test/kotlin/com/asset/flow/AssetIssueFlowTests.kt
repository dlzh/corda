package com.dz0189.flow

import com.dz0189.issue.AssetContract
import com.dz0189.issue.AssetState
import net.corda.core.contracts.TransactionVerificationException
import kotlin.test.assertEquals
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
import kotlin.test.assertFailsWith

/**
 * Practical exercise instructions for flows part 1.
 * Uncomment the unit tests and use the hints + unit test body to complete the flows such that the unit tests pass.
 */
class AssetIssueFlowTests {

    lateinit var network: MockNetwork
    lateinit var ownerNode: StartedMockNode
    lateinit var regulatorNode: StartedMockNode
    lateinit var owner: Party
    lateinit var regulator: Party
    private val name = "asset"
    private val amount: Long = 100

    @Before
    fun setup() {
        network = MockNetwork(
                listOf("com.twig", "com.dz0189")
        )
        ownerNode = network.createPartyNode()
        regulatorNode = network.createPartyNode(CordaX500Name.parse("O=Regulator,L=Manila,C=PH"))
        owner = ownerNode.info.singleIdentity()
        regulator = regulatorNode.info.singleIdentity()
        network.runNetwork()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    /**
     * Task 1.
     * Build out the [AssetIssueFlow]!
     * TODO: Implement the [AssetIssueFlow] flow which builds and returns a partially [SignedTransaction].
     * Hint:
     * - There's a whole bunch of things you need to do to get this unit test to pass!
     * - Look at the comments in the [AssetIssueFlow] object for how to complete this task as well as the unit test below.
     * - Create a [TransactionBuilder] and pass it a notary reference. A notary [Party] object can be obtained from
     *   [FlowLogic.serviceHub.networkMapCache].
     * - Create an Asset Issue [Command].
     * - Add the Asset state (as an output) and the [Command] to the transaction builder.
     * - Sign the transaction and convert it to a [SignedTransaction] using the [ServiceHub.signInitialTransaction]
     *   method.
     * - Return the [SignedTransaction].
     */
    @Test
    fun flowReturnsCorrectlyFormedPartiallySignedTransaction() {
        val asset = AssetState(name, amount, owner)
        val flow = AssetIssueFlow.Initiator(amount)
        val future = ownerNode.startFlow(flow)
        network.runNetwork()
        // Return the unsigned(!) SignedTransaction object from the AssetIssueFlow.
        val ptx: SignedTransaction = future.getOrThrow()
        // Print the transaction for debugging purposes.
        println(ptx.tx)
        // Check the transaction is well formed...
        // No inputs, one output AssetState and a command with the right properties.
        assert(ptx.tx.inputs.isEmpty())
        assert(ptx.tx.outputs.single().data is AssetState)
        val command = ptx.tx.commands.single()
        assert(command.value is AssetContract.Commands.Issue)
        assert(command.signers.toSet() == asset.participants.map { it.owningKey }.toSet())
    }

    /**
     * Task 2.
     * Now we have a well formed transaction, we need to properly verify it using the [AssetContract].
     * TODO: Amend the [AssetIssueFlow] to verify the transaction as well as sign it.
     */
    @Test
fun flowReturnsVerifiedPartiallySignedTransaction() {
        // Check that a zero amount asset fails.
        val zeroFlow = AssetIssueFlow.Initiator(0)
        val futureOne = ownerNode.startFlow(zeroFlow)
        network.runNetwork()
        assertFailsWith<TransactionVerificationException> { futureOne.getOrThrow() }
        // Check a good asset passes.
        val flow = AssetIssueFlow.Initiator(100)
        val futureTwo = ownerNode.startFlow(flow)
        network.runNetwork()
        futureTwo.getOrThrow()
    }

    /**
     * Task 3.
     * Note that in this issuance flow, there has been no need to collect signatures from other parties as this is a self issue flow.
     * As such, we only need to store the final state in our own vault.
     * TODO: Amend the [AssetIssueFlow] by adding a call to [FinalityFlow].
     * Hint:
     * - As mentioned above, use the [FinalityFlow] to ensure the transaction is recorded in our [Party] vaults.
     * - The return value from the [FinalityFlow] call must be passed to [BroadcastTransaction].
     * - Remember to make this call as you will not receive the reward without it (you will also fail this test)
     * - The [FinalityFlow] determines if the transaction requires notarisation or not.
     * - We don't need the notary's signature as this is an issuance transaction without a timestamp. There are no
     *   inputs in the transaction that could be double spent! If we added a timestamp to this transaction then we
     *   would require the notary's signature as notaries act as a timestamping authority.
     */
    @Test
    fun flowRecordsTheSameTransactionInBothPartyVaults() {
        val flow = AssetIssueFlow.Initiator(amount)
        val future = ownerNode.startFlow(flow)
        network.runNetwork()
        val stx = future.getOrThrow()
        println("Signed transaction hash: ${stx.id}")
        // check both the owner's vault and the regulator's vault for the transaction
        listOf(ownerNode, regulatorNode).map {
            it.services.validatedTransactions.getTransaction(stx.id)
        }.forEach {
            val txHash = (it as SignedTransaction).id
            println("$txHash == ${stx.id}")
            assertEquals(stx.id, txHash)
        }
    }
}
