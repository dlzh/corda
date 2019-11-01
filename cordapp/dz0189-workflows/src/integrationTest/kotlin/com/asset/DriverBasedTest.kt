package com.dz0189

import com.dz0189.flow.AssetIssueFlow
import com.dz0189.flow.AssetTradeFlow
import com.dz0189.issue.AssetState
import com.dz0189.trade.AssetContractNew
import com.dz0189.trade.AssetStateNew
import com.twig.flow.PrideIssueFlow
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.concurrent.CordaFuture
import net.corda.core.flows.ContractUpgradeFlow
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.startFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.Permissions.Companion.invokeRpc
import net.corda.node.services.Permissions.Companion.startFlow
import net.corda.testing.core.*
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import org.junit.Test

/*
 * With the trade flow, there is an additional test that you must complete. The integration test
 * ensures that your flow works and does not result in double spends when it is run concurrently
 * with other users. Let's assume that we will run 5 trade flows concurrently and that Smurfie's
 * has been issued 1000 Prides. As Corda uses a UTXO model, Smurfies has 1 Pride State with amount
 * 1000. While the first flow is running, the node will retrieve the state and attempt to complete the trade.
 * Issues arise when other nodes try and retrieve the state. If the other nodes are allowed to retrieve
 * the same state then a double spend will occur (as you are trying to spend the same state in two
 * different transactions). The solution to this is to lock the state. This can be achieved with the
 * [tryLockFungibleStatesForSpending] function (consult your cheatsheet!). You will have to query
 * the vault for the state and retry if you are unsuccessful. Here is some sample code to help you.
 * You only need to do this for Prides, not Assets. 
 *
 * Hint:
 *  - Amount is a wrapper for a token. To create an Amount for 1000 Prides, use Amount(1000, "Prides")
 *  - If, after 30 retries, you still cannot retrieve any states, throw an [InsufficientBalanceException]
 *  - Here is some code to help you get started.
  for (retryCount in 1..maxRetries) {
      // retrieve states here
      if (states.IsEmpty()) {
          log.warn("Coin selection failed on attempt $retryCount")
          if (retryCount != maxRetries) {
              FlowLogic.sleep(Duration.ofSeconds((Math.random() * 5).toLong()))
          } else {
              log.warn("Insufficient spendable states identified for $amount")
          }
      } else {
          break
      }
  }
 */
class DriverBasedTest {
    val devon = TestIdentity(CordaX500Name("Devon", "", "GB"))
    val devon2 = TestIdentity(CordaX500Name("Devon2", "", "GB"))
    val devon3 = TestIdentity(CordaX500Name("Devon3", "", "GB"))
    val devon4 = TestIdentity(CordaX500Name("Devon4", "", "GB"))
    val devon5 = TestIdentity(CordaX500Name("Devon5", "", "GB"))
    val devon6 = TestIdentity(CordaX500Name("Devon6", "", "GB"))
    val devon7 = TestIdentity(CordaX500Name("Devon7", "", "GB"))
    val devon8 = TestIdentity(CordaX500Name("Devon8", "", "GB"))

    val smurfies = TestIdentity(CordaX500Name("Smurfies", "", "AU"))
    val twig = TestIdentity(CordaX500Name("Twig", "", "US"))
    val regulator = TestIdentity(CordaX500Name("Regulator", "", "PH"))

//    @Test()
//    fun testSoftLockedStates() {
//        driver(DriverParameters(isDebug = true, startNodesInProcess = true,
//                extraCordappPackagesToScan = listOf("com.dz0189", "com.twig"))) {
//            val aliceUser = User("aliceUser", "testPassword1", permissions = setOf("ALL"))
//            val bobUser = User("bobUser", "testPassword2", permissions = setOf("ALL"))
//            val charlieUser = User("charlieUser", "testPassword3", permissions = setOf("ALL"))
//            val (smurfiesNode, twigNode, regulatorNode) = listOf(
//                    startNode(providedName = smurfies.name, rpcUsers = listOf(bobUser)),
//                    startNode(providedName = twig.name, rpcUsers = listOf(charlieUser)),
//                    startNode(providedName = regulator.name)
//            ).map { it.getOrThrow() }
//
//            val (devonNode, devon2Node, devon3Node, devon4Node, devon5Node) = listOf(
//                    startNode(providedName = devon.name, rpcUsers = listOf(aliceUser)),
//                    startNode(providedName = devon2.name, rpcUsers = listOf(aliceUser)),
//                    startNode(providedName = devon3.name, rpcUsers = listOf(aliceUser)),
//                    startNode(providedName = devon4.name, rpcUsers = listOf(aliceUser)),
//                    startNode(providedName = devon5.name, rpcUsers = listOf(aliceUser))
//            ).map { it.getOrThrow() }
//
//            val (devon6Node, devon7Node, devon8Node) = listOf(
//                    startNode(providedName = devon6.name, rpcUsers = listOf(aliceUser)),
//                    startNode(providedName = devon7.name, rpcUsers = listOf(aliceUser)),
//                    startNode(providedName = devon8.name, rpcUsers = listOf(aliceUser))
//            ).map { it.getOrThrow() }
//
//            val smurfiesClient = CordaRPCClient(smurfiesNode.rpcAddress)
//            val smurfiesProxy = smurfiesClient.start("bobUser", "testPassword2").proxy
//            val twigClient = CordaRPCClient(twigNode.rpcAddress)
//            val twigProxy = twigClient.start("charlieUser", "testPassword3").proxy
//
//            val devonClient = CordaRPCClient(devonNode.rpcAddress)
//            val devonProxy = devonClient.start("aliceUser", "testPassword1").proxy
//            val devon2Client = CordaRPCClient(devon2Node.rpcAddress)
//            val devon2Proxy = devon2Client.start("aliceUser", "testPassword1").proxy
//            val devon3Client = CordaRPCClient(devon3Node.rpcAddress)
//            val devon3Proxy = devon3Client.start("aliceUser", "testPassword1").proxy
//            val devon4Client = CordaRPCClient(devon4Node.rpcAddress)
//            val devon4Proxy = devon4Client.start("aliceUser", "testPassword1").proxy
//            val devon5Client = CordaRPCClient(devon5Node.rpcAddress)
//            val devon5Proxy = devon5Client.start("aliceUser", "testPassword1").proxy
//            val devon6Client = CordaRPCClient(devon6Node.rpcAddress)
//            val devon6Proxy = devon6Client.start("aliceUser", "testPassword1").proxy
//            val devon7Client = CordaRPCClient(devon7Node.rpcAddress)
//            val devon7Proxy = devon7Client.start("aliceUser", "testPassword1").proxy
//            val devon8Client = CordaRPCClient(devon8Node.rpcAddress)
//            val devon8Proxy = devon8Client.start("aliceUser", "testPassword1").proxy
//
//            val prideIssuanceValue: Long = 1000
//            val assetIssuanceValue: Long = 100
//            val assetAmount: Long = 10
//            val amountToPay: Long = 50
//            val commission = 0.05
//
//            // Issue Prides To Smurfies
//            twigProxy.startFlow(PrideIssueFlow::Initiator,
//                    prideIssuanceValue,
//                    smurfiesProxy.nodeInfo().legalIdentities.first()
//            ).returnValue.getOrThrow()
//
//            val proxies = listOf(devonProxy, devon2Proxy, devon3Proxy, devon4Proxy, devon5Proxy,
//                    devon6Proxy, devon7Proxy, devon8Proxy)
//
//            // Issue 100 assets to each Devon
//            proxies.map { node ->
//                node.startFlow(AssetIssueFlow::Initiator, assetIssuanceValue).returnValue.getOrThrow()
//                val stateAndRef = node.vaultQuery(AssetState::class.java).states.single()
//                node.startFlow(ContractUpgradeFlow::Authorise, stateAndRef, AssetContractNew::class.java).returnValue.getOrThrow()
//                node.startFlowDynamic(ContractUpgradeFlow.Initiate::class.java, stateAndRef, AssetContractNew::class.java).returnValue.getOrThrow()
//                println("finished")
//            }
//
//            // Commence trade from Party A to Party B 10 times for 10 assets.
//            val futures: MutableList<CordaFuture<SignedTransaction>> = mutableListOf()
//            println("started")
//            listOf(1).map {
//                proxies.map { node ->
//                    futures.add(smurfiesProxy.startFlow(AssetTradeFlow::Buyer,
//                            assetAmount,
//                            amountToPay,
//                            node.nodeInfo().legalIdentities.first(),
//                            commission
//                    ).returnValue)
//                }
//            }
//            futures.map { it.getOrThrow() }
//        }
//    }
}
