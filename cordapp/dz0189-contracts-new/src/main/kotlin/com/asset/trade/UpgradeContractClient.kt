package com.dz0189.trade

import com.dz0189.issue.AssetState
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.flows.ContractUpgradeFlow
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.NetworkHostAndPort.Companion.parse
import net.corda.core.utilities.loggerFor
import org.slf4j.Logger

/**
 * This file queries for states belonging to the user before performing a Cordapp
 * upgrade using the hash constraints method.
 */
fun main(args: Array<String>) = UpgradeAuthClient().main(args)

private class UpgradeAuthClient {
    companion object {
        val logger: Logger = loggerFor<UpgradeAuthClient>()
    }

    fun main(args: Array<String>) {
        require (args.size == 1) { "Usage: UpgradeAuthClient <node address>" }
        val nodeAddress = parse(args[0])
        val client = CordaRPCClient(nodeAddress)

        val proxy = client.start("user1", "test").proxy

        val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
        val stateAndRefs = proxy.vaultQueryByCriteria(generalCriteria, AssetState::class.java).states

        stateAndRefs.forEach { logger.info("{}", it.state) }

        stateAndRefs
                .filter { stateAndRef ->
                    stateAndRef.state.contract == ("com.dz0189.issue.AssetContract")
                }.forEach { stateAndRef ->
                    proxy.startFlowDynamic(
                            ContractUpgradeFlow.Authorise::class.java,
                            stateAndRef,
                            AssetContractNew::class.java
                    )
                }

        stateAndRefs
                .filter { stateAndRef ->
                    stateAndRef.state.contract == ("com.dz0189.issue.AssetContract")
                }.forEach { stateAndRef ->
                    proxy.startFlowDynamic(
                            ContractUpgradeFlow.Initiate::class.java,
                            stateAndRef,
                            AssetContractNew::class.java
                    )
                }

        Thread.sleep(10000)

        proxy.vaultQuery(AssetState::class.java).states.forEach { logger.info("{}", it.state) }
    }
}
