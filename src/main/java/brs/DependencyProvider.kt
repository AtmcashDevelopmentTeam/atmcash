package brs

import brs.assetexchange.AssetExchange
import brs.db.cache.DBCacheManagerImpl
import brs.db.store.*
import brs.deeplink.DeeplinkQRCodeGenerator
import brs.feesuggestions.FeeSuggestionCalculator
import brs.fluxcapacitor.FluxCapacitor
import brs.http.API
import brs.http.APITransactionManager
import brs.props.PropertyService
import brs.services.*
import brs.statistics.StatisticsManagerImpl
import brs.unconfirmedtransactions.UnconfirmedTransactionStore
import brs.util.DownloadCacheImpl
import brs.util.ThreadPool
import io.grpc.Server
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty

class DependencyProvider {
    lateinit var accountStore: AccountStore
    lateinit var aliasStore: AliasStore
    lateinit var assetTransferStore: AssetTransferStore
    lateinit var assetStore: AssetStore
    lateinit var atStore: ATStore
    lateinit var blockchainStore: BlockchainStore
    lateinit var digitalGoodsStoreStore: DigitalGoodsStoreStore
    lateinit var escrowStore: EscrowStore
    lateinit var orderStore: OrderStore
    lateinit var tradeStore: TradeStore
    lateinit var subscriptionStore: SubscriptionStore
    lateinit var unconfirmedTransactionStore: UnconfirmedTransactionStore
    lateinit var indirectIncomingStore: IndirectIncomingStore
    lateinit var dbs: Dbs
    lateinit var blockchain: Blockchain
    lateinit var blockchainProcessor: BlockchainProcessor
    lateinit var transactionProcessor: TransactionProcessor
    lateinit var propertyService: PropertyService
    lateinit var fluxCapacitor: FluxCapacitor
    lateinit var dbCacheManager: DBCacheManagerImpl
    lateinit var api: API
    lateinit var apiV2Server: Server
    lateinit var timeService: TimeService
    lateinit var derivedTableManager: DerivedTableManager
    lateinit var statisticsManager: StatisticsManagerImpl
    lateinit var threadPool: ThreadPool
    lateinit var aliasService: AliasService
    lateinit var economicClustering: EconomicClustering
    lateinit var generator: Generator
    lateinit var accountService: AccountService
    lateinit var transactionService: TransactionService
    lateinit var atService: ATService
    lateinit var subscriptionService: SubscriptionService
    lateinit var digitalGoodsStoreService: DGSGoodsStoreService
    lateinit var escrowService: EscrowService
    lateinit var assetExchange: AssetExchange
    lateinit var downloadCache: DownloadCacheImpl
    lateinit var indirectIncomingService: IndirectIncomingService
    lateinit var blockService: BlockService
    lateinit var feeSuggestionCalculator: FeeSuggestionCalculator
    lateinit var deeplinkQRCodeGenerator: DeeplinkQRCodeGenerator
    lateinit var parameterService: ParameterService
    lateinit var apiTransactionManager: APITransactionManager
}
