package com.dz0189.trade

import com.twig.contract.PrideContract
import com.twig.state.PrideState
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test

/**
 * Practical exercise instructions for Contracts Part 2.
 * The objective here is to write some contract code that verifies a transaction to settle an [AssetStateNew].
 * Trading is more complicated than issuing as it requires you to use multiple state types in a
 * transaction.
 * As with the [AssetContractNewIssueTests] uncomment each unit test and run them one at a time. Use the body
 * of the tests and the task description to determine how to get the tests to pass.
 */
class AssetContractNewTradeTests {
    private val ledgerServices = MockServices(listOf("com.dz0189.trade", "com.twig"))
    private val owner  = TestIdentity(CordaX500Name("MiniCorp", "New York", "US"))
    private val smurfies = TestIdentity(CordaX500Name("Smurfies", "Seoul", "KR"))
    private val twig = TestIdentity(CordaX500Name("Twig", "Sydney", "AU"))
    private val dummy = TestIdentity(CordaX500Name("Dummy", "London", "GB"))
    private val twigIssuerAndRef = twig.ref(0)

    private val name = "I am an asset!"
    private val amount: Long = 100

    // A pre-defined dummy command.
    class DummyCommand : TypeOnlyCommandData()

    /**
     * Task 1.
     * We need to add another case to deal with settling in the [AssetContract.verify] function.
     * TODO: Add the [AssetContractNew.Commands.Trade] case to the verify function.
     * Hint: You can leave the body empty for now.
     */
    @Test
    fun mustIncludeTradeCommand() {
        val inputAsset = AssetStateNew(name, amount, owner.party)
        val outputAsset = inputAsset.withNewOwner(newOwner = smurfies.party).ownableState
        val inputPrides = PrideState(amount, twigIssuerAndRef, smurfies.party)
        val outputPrides = inputPrides.withNewOwner(newOwner = owner.party).ownableState
        ledgerServices.ledger {
            transaction {
                input(AssetContractNew.ID, inputAsset)
                output(AssetContractNew.ID, outputAsset)
                input(PrideContract.PRIDE_CONTRACT_ID, inputPrides)
                output(PrideContract.PRIDE_CONTRACT_ID, outputPrides)
                command(smurfies.publicKey, PrideContract.Commands.Transfer())
                this.fails()
            }
            transaction {
                input(AssetContractNew.ID, inputAsset)
                output(AssetContractNew.ID, outputAsset)
                input(PrideContract.PRIDE_CONTRACT_ID, inputPrides)
                output(PrideContract.PRIDE_CONTRACT_ID, outputPrides)
                command(smurfies.publicKey, PrideContract.Commands.Transfer())
                command(owner.publicKey, DummyCommand()) // Wrong type.
                this.fails()
            }
            transaction {
                input(AssetContractNew.ID, inputAsset)
                output(AssetContractNew.ID, outputAsset)
                input(PrideContract.PRIDE_CONTRACT_ID, inputPrides)
                output(PrideContract.PRIDE_CONTRACT_ID, outputPrides)
                command(smurfies.publicKey, PrideContract.Commands.Transfer())
                command(listOf(smurfies.publicKey, twig.publicKey, owner.publicKey), AssetContractNew.Commands.Trade()) // Correct Type.
                this.verifies()
            }
        }
    }

    /**
     * Task 2.
     * We need to check that the payer is cofrrectly assigning us as the new owner of the Pride states.
     * TODO: Add a constraint to check that we are the new owner of the output Prides.
     * Hint:
     * - Not all of the Prides may be assigned to us as some of the input Prides may be sent back to the payer as change.
     * - We need to use the [PrideState.owner] property to check to see that it is the value of our public key.
     * - We want to compare the public key of the input Asset States to that of the output Pride States (as we are trading
     *     assets for Prides.
     * - Use the Kotline [filter] command to filter over the list of Prides states to get the ones which are being assigned to us.
     * - Once we have this filtered list, we can sum the Prides being paid to us so we know how much is being traded.
     */
    @Test
    fun mustBePrideOutputStatesWithRecipientAsOwner() {
        val inputAsset = AssetStateNew(name, amount, owner.party)
        val outputAsset = inputAsset.withNewOwner(newOwner = smurfies.party).ownableState
        val inputPrides = PrideState(amount, twigIssuerAndRef, smurfies.party)
        val invalidPridePayment = inputPrides.withNewOwner(newOwner = dummy.party)
        val validPridePayment = inputPrides.withNewOwner(newOwner = owner.party)
        ledgerServices.ledger {
            transaction {
                input(AssetContractNew.ID, inputAsset)
                input(PrideContract.PRIDE_CONTRACT_ID, inputPrides)
                output(AssetContractNew.ID, outputAsset)
                output(PrideContract.PRIDE_CONTRACT_ID, "outputs Prides", invalidPridePayment.ownableState)
                command(smurfies.publicKey, invalidPridePayment.command)
                command(owner.publicKey, AssetContractNew.Commands.Trade())
                this `fails with` "There must be output Prides paid to the recipient."
            }
            transaction {
                input(AssetContractNew.ID, inputAsset)
                input(PrideContract.PRIDE_CONTRACT_ID, inputPrides)
                output(AssetContractNew.ID, outputAsset)
                output(PrideContract.PRIDE_CONTRACT_ID, validPridePayment.ownableState)
                command(smurfies.publicKey, validPridePayment.command)
                command(owner.publicKey, AssetContractNew.Commands.Trade())
                this.verifies()
            }
        }
    }

    /**
     * Task 3.
     * We need to check that the asset owner is correctly assigning Smurfies as the new owner of the asset states.
     * TODO: Add a constraint to check that Smurfies is the new owner of the output assets.
     * Hint:
     * - Not all of the assets may be assigned to us as some of the input assets may be sent back to the payer as change.
     * - We need to use the [AssetStateNew.owner] property to check to see that it is the value of our public key.
     * - We want to compare the public key of the input Pride States to that of the output Asset States (as we are trading
     *     assets for Prides.
     * - Use [filter] to filter over the list of asset states to get the ones which are being assigned to Smurfies.
     * - Once we have this filtered list, we can sum the assets being paid to us so we know how much is being traded.
     */
    @Test
    fun mustBeAssetOutputStatesWithRecipientAsOwner() {
        val inputAsset = AssetStateNew(name, amount, owner.party)
        val inputPrides = PrideState(amount, twigIssuerAndRef, smurfies.party)
        val outputPrides = inputPrides.withNewOwner(newOwner = owner.party).ownableState
        val invalidAssetPayment = inputAsset.withNewOwner(newOwner = dummy.party)
        val validAssetPayment = inputAsset.withNewOwner(newOwner = smurfies.party)
        ledgerServices.ledger {
            transaction {
                input(AssetContractNew.ID, inputAsset)
                input(PrideContract.PRIDE_CONTRACT_ID, inputPrides)
                output(PrideContract.PRIDE_CONTRACT_ID, outputPrides)
                output(AssetContractNew.ID, "outputs Assets", invalidAssetPayment.ownableState)
                command(owner.publicKey, invalidAssetPayment.command)
                command(smurfies.publicKey, PrideContract.Commands.Transfer())
                this `fails with` "There must be output Assets paid to the recipient."
            }
            transaction {
                input(AssetContractNew.ID, inputAsset)
                input(PrideContract.PRIDE_CONTRACT_ID, inputPrides)
                output(PrideContract.PRIDE_CONTRACT_ID, outputPrides)
                output(AssetContractNew.ID, validAssetPayment.ownableState)
                command(owner.publicKey, validAssetPayment.command)
                command(smurfies.publicKey, PrideContract.Commands.Transfer())
                this.verifies()
            }
        }
    }

    /**
     * Task 4.
     * We need to check that the total number of input assets is equal to the output assets (i.e. no assets were created)
     * TODO: Add a constraint to check that the total number of assets in the input states is equal to the total number of
     * assets in the output state.
     * Hint:
     * - Not all of the assets may be assigned to us as some of the input assets may be sent back to the payer as change.
     * - We need to use the [AssetStateNew.owner] property to check to see that it is the value of our public key.
     * - We want to compare the public key of the input Pride States to that of the output Asset States (as we are trading
     *     assets for Prides.
     * - Use [filter] to filter over the list of asset states to get the ones which are being assigned to Smurfies.
     * - Once we have this filtered list, we can sum the assets being paid to us so we know how much is being traded.
     */
    @Test
    fun totalInputAssetsMustEqualOutputAssets() {
        val ia10 = AssetStateNew(name, 10, owner.party)
        val ia20 = AssetStateNew(name, 20, owner.party)
        val ia50 = AssetStateNew(name, 50, owner.party)
        val oa10 = ia10.withNewOwner(newOwner = smurfies.party).ownableState
        val oa20 = ia20.withNewOwner(newOwner = smurfies.party).ownableState
        val oa50 = ia50.withNewOwner(newOwner = smurfies.party).ownableState
        val inputPrides = PrideState(amount, twigIssuerAndRef, smurfies.party)
        val outputPrides = inputPrides.withNewOwner(newOwner = owner.party).ownableState
        ledgerServices.ledger {
            transaction {
                input(AssetContractNew.ID, ia10)
                input(AssetContractNew.ID, ia10)
                input(AssetContractNew.ID, ia20)
                output(AssetContractNew.ID, oa50)
                input(PrideContract.PRIDE_CONTRACT_ID, inputPrides)
                output(PrideContract.PRIDE_CONTRACT_ID, outputPrides)
                command(owner.publicKey, AssetContractNew.Commands.Trade())
                command(smurfies.publicKey, PrideContract.Commands.Transfer())
                this `fails with` "Total number of input Assets must equal total number of output Assets."
            }
            transaction {
                input(AssetContractNew.ID, ia10)
                input(AssetContractNew.ID, ia10)
                input(AssetContractNew.ID, ia20)
                output(AssetContractNew.ID, oa10)
                output(AssetContractNew.ID, oa20)
                input(PrideContract.PRIDE_CONTRACT_ID, inputPrides)
                output(PrideContract.PRIDE_CONTRACT_ID, outputPrides)
                command(owner.publicKey, AssetContractNew.Commands.Trade())
                command(smurfies.publicKey, PrideContract.Commands.Transfer())
                this `fails with` "Total number of input Assets must equal total number of output Assets."
            }
            transaction {
                input(AssetContractNew.ID, ia10)
                input(AssetContractNew.ID, ia20)
                input(AssetContractNew.ID, ia20)
                output(AssetContractNew.ID, oa50)
                input(PrideContract.PRIDE_CONTRACT_ID, inputPrides)
                output(PrideContract.PRIDE_CONTRACT_ID, outputPrides)
                command(owner.publicKey, AssetContractNew.Commands.Trade())
                command(smurfies.publicKey, PrideContract.Commands.Transfer())
                this.verifies()
            }
            transaction {
                input(AssetContractNew.ID, ia10)
                input(AssetContractNew.ID, ia20)
                input(AssetContractNew.ID, ia20)
                output(AssetContractNew.ID, oa10)
                output(AssetContractNew.ID, oa10)
                output(AssetContractNew.ID, oa10)
                output(AssetContractNew.ID, oa20)
                input(PrideContract.PRIDE_CONTRACT_ID, inputPrides)
                output(PrideContract.PRIDE_CONTRACT_ID, outputPrides)
                command(owner.publicKey, AssetContractNew.Commands.Trade())
                command(smurfies.publicKey, PrideContract.Commands.Transfer())
                this.verifies()
            }
        }
    }

    /**
     * Task 5.
     * We need to check that the total number of input prides is equal to the output prides (i.e. no prides were created)
     * This is actually handled in the pride cordapp that is included as a dependency. So you don't have to do anything!
     * Make sure this test passes in your flow. :)
     */
    @Test
    fun totalInputPridesMustEqualOutputPrides() {
        val ip10 = PrideState(10, twigIssuerAndRef, smurfies.party)
        val ip20 = PrideState(20, twigIssuerAndRef, smurfies.party)
        val ip50 = PrideState(50, twigIssuerAndRef, smurfies.party)
        val op10 = ip10.withNewOwner(newOwner = owner.party).ownableState
        val op20 = ip20.withNewOwner(newOwner = owner.party).ownableState
        val op50 = ip50.withNewOwner(newOwner = owner.party).ownableState
        val inputAssets = AssetStateNew(name, 10, owner.party)
        val outputAssets = inputAssets.withNewOwner(newOwner = smurfies.party).ownableState
        ledgerServices.ledger {
            transaction {
                input(PrideContract.PRIDE_CONTRACT_ID, ip10)
                input(PrideContract.PRIDE_CONTRACT_ID, ip10)
                input(PrideContract.PRIDE_CONTRACT_ID, ip20)
                output(PrideContract.PRIDE_CONTRACT_ID, op50)
                input(AssetContractNew.ID, inputAssets)
                output(AssetContractNew.ID, outputAssets)
                command(owner.publicKey, AssetContractNew.Commands.Trade())
                command(smurfies.publicKey, PrideContract.Commands.Transfer())
                this `fails with` "Total number of input Prides must equal total number of output Prides."
            }
            transaction {
                input(PrideContract.PRIDE_CONTRACT_ID, ip10)
                input(PrideContract.PRIDE_CONTRACT_ID, ip10)
                input(PrideContract.PRIDE_CONTRACT_ID, ip20)
                output(PrideContract.PRIDE_CONTRACT_ID, op10)
                output(PrideContract.PRIDE_CONTRACT_ID, op20)
                input(AssetContractNew.ID, inputAssets)
                output(AssetContractNew.ID, outputAssets)
                command(owner.publicKey, AssetContractNew.Commands.Trade())
                command(smurfies.publicKey, PrideContract.Commands.Transfer())
                this `fails with` "Total number of input Prides must equal total number of output Prides."
            }
            transaction {
                input(PrideContract.PRIDE_CONTRACT_ID, ip10)
                input(PrideContract.PRIDE_CONTRACT_ID, ip20)
                input(PrideContract.PRIDE_CONTRACT_ID, ip20)
                output(PrideContract.PRIDE_CONTRACT_ID, op50)
                input(AssetContractNew.ID, inputAssets)
                output(AssetContractNew.ID, outputAssets)
                command(owner.publicKey, AssetContractNew.Commands.Trade())
                command(smurfies.publicKey, PrideContract.Commands.Transfer())
                this.verifies()
            }
            transaction {
                input(PrideContract.PRIDE_CONTRACT_ID, ip10)
                input(PrideContract.PRIDE_CONTRACT_ID, ip20)
                input(PrideContract.PRIDE_CONTRACT_ID, ip20)
                output(PrideContract.PRIDE_CONTRACT_ID, op10)
                output(PrideContract.PRIDE_CONTRACT_ID, op10)
                output(PrideContract.PRIDE_CONTRACT_ID, op10)
                output(PrideContract.PRIDE_CONTRACT_ID, op20)
                input(AssetContractNew.ID, inputAssets)
                output(AssetContractNew.ID, outputAssets)
                command(owner.publicKey, AssetContractNew.Commands.Trade())
                command(smurfies.publicKey, PrideContract.Commands.Transfer())
                this.verifies()
            }
        }
    }
}
