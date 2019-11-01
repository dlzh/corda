package com.dz0189.trade

import com.twig.state.Asset
import net.corda.core.contracts.Amount
import net.corda.core.contracts.FungibleAsset
import net.corda.core.contracts.Issued
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Practical exercise instructions for States Part 2.
 * Copy your AssetState class from com.dz0189.issue.AssetContract to com.dz0189.trade.AssetContractNew.
 * Rename this class AssetStateNew.
 * This should work with very few changes in [AssetStateNew].
 */
class AssetStateNewTests {
    private val owner  = TestIdentity(CordaX500Name("MiniCorp", "New York", "US"))
    private val other = TestIdentity(CordaX500Name("OtherParty", "Sydney", "AU"))
    private val ownerParty = owner.party
    private val otherParty = other.party

    private val name = "I am an asset!"
    private val amount: Long = 100
    private val newAmount: Amount<Issued<Asset>> = Amount(amount, Issued(ownerParty.ref(0), Asset(name)))

    /**
     * Task 1.
     * TODO: Add an 'amount' property of type [Amount] to the [AssetStateNew] class to get this test to pass.
     * Hint: [Amount] is a template class that takes a class parameter of the token you would like an [Amount] of.
     * Use [Issued<Asset>] as the token for the [Amount] class.
     */
    @Test
    fun hasAssetAmountFieldOfCorrectType() {
        // Does the amount field exist?
        AssetStateNew::class.java.getDeclaredField("amount")
        // Is the amount field of the correct type?
        assertEquals(AssetStateNew::class.java.getDeclaredField("amount").type, Amount::class.java)
    }

    /**
     * Task 2.
     * TODO: Add an 'owner' property of type [Party] to the [AssetStateNew] class to get this test to pass.
     */
    @Test
    fun hasOwnerFieldOfCorrectType() {
        // Does the owner field exist?
        AssetStateNew::class.java.getDeclaredField("owner")
        // Is the owner field of the correct type?
        assertEquals(AssetStateNew::class.java.getDeclaredField("owner").type, AbstractParty::class.java)
    }

    /**
     * Task 3.
     * TODO: Add an entry to the [AssetStateNew.participants] list for the owner.
     * Uncomment out the constructor. This constructor has been provided to help you easily construct classes of your token.
     */
    @Test
    fun ownerIsParticipant() {
        val assetState = AssetStateNew(name, amount, ownerParty)
        assertEquals(assetState.participants.size, 1)
        assertEquals(assetState.participants.single(), ownerParty)
    }

    /**
     * Task 4.
     * TODO: Modify the implementation from [ContractState] to [OwnableState].
     * This involves adding a helper method called withNewOwner that accepts an owner and
     * returns a CommandAndState containing the relevant command and a new state.
     * Hint: This helper method is called when ownership changes. You may find it useful to take advantage of the Kotlin
     * data class "copy" functionality.
     */
    @Test
    fun isOwnableAsset() {
        val assetState = AssetStateNew(name, amount, ownerParty)
        val (cmd, state) = assetState.withNewOwner(otherParty)
        assertEquals(cmd::class.java, AssetContractNew.Commands.Trade::class.java)
        assertEquals(state.owner, otherParty)
        assert(FungibleAsset::class.java.isAssignableFrom(AssetStateNew::class.java))
    }

    /**
     * Task 5.
     * TODO: Implement [FungibleAsset] of type Asset along with the required properties and methods.
     *   Task 5.1. Override inherited abstract functions
     *   Task 5.2. Add a helper method called withNewOwnerAndAmount that accepts an owner and returns a [FungibleAsset].
     *     Hint: This helper method is called when ownership and amount changes. For example, when you are distributing
     *     change. You may find it helpful to use the Kotlin data class "copy" functionality.
     *   Task 5.3. Add a helper method called exitKeys. It accepts no values and returns a set of all keys required to destroy
     *     a state using an Exit command. We won't be implementing this feature, but it is a required interface.
     */
    @Test
    fun isFungibleAsset() {
        // check withNewOwnerAndAmount method
        val assetState = AssetStateNew(name, amount, ownerParty)
        val state = assetState.withNewOwnerAndAmount(newAmount, otherParty)
        assertEquals(state.owner, otherParty)
        assertEquals(state.amount, newAmount)

        // check exitKeys method
        assertEquals(assetState.exitKeys.size, 1)
        assertEquals(assetState.exitKeys.contains(ownerParty.owningKey), true)

        // check isFungibleAsset
        assert(FungibleAsset::class.java.isAssignableFrom(AssetStateNew::class.java))
    }
}
