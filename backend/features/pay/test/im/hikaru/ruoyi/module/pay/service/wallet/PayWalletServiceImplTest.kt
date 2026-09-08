package im.hikaru.ruoyi.module.pay.service.wallet

import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.pay.controller.app.wallet.vo.transaction.AppPayWalletTransactionPageReqVO
import im.hikaru.ruoyi.module.pay.dal.dataobject.wallet.PayWalletTransactionDO
import im.hikaru.ruoyi.module.pay.dal.mysql.wallet.PayWalletDao
import im.hikaru.ruoyi.module.pay.dal.mysql.wallet.PayWalletTable
import im.hikaru.ruoyi.module.pay.dal.mysql.wallet.PayWalletTransactionDao
import im.hikaru.ruoyi.module.pay.dal.mysql.wallet.PayWalletTransactionTable
import im.hikaru.ruoyi.module.pay.dal.redis.wallet.PayWalletLockRedisDAO
import im.hikaru.ruoyi.module.pay.enums.wallet.PayWalletBizTypeEnum
import im.hikaru.ruoyi.module.pay.service.wallet.bo.WalletTransactionCreateReqBO
import im.hikaru.ruoyi.module.pay.test.interfaceProxy
import java.time.LocalDateTime
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.redisson.api.RLock
import org.redisson.api.RedissonClient

class PayWalletServiceImplTest {
    private lateinit var service: PayWalletServiceImpl

    @BeforeEach
    fun setUp() {
        Database.connect(
            "jdbc:h2:mem:pay_wallet_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        transaction { SchemaUtils.create(PayWalletTable, PayWalletTransactionTable) }
        TenantContextHolder.setTenantId(1L)

        val lock = interfaceProxy<RLock> { method, _ ->
            when (method) {
                "isHeldByCurrentThread" -> true
                else -> null
            }
        }
        val redisson = interfaceProxy<RedissonClient> { method, _ ->
            when (method) {
                "getLock" -> lock
                else -> null
            }
        }
        val transactionService = interfaceProxy<PayWalletTransactionService> { method, args ->
            when (method) {
                "getWalletTransaction" -> {
                    val bizType = args?.get(1) as PayWalletBizTypeEnum
                    PayWalletTransactionDao.selectByBizIdAndType(args[0] as String, bizType.type)
                }
                "getWalletTransactionByNo" -> PayWalletTransactionDao.selectByNo(args?.get(0) as String)
                "createWalletTransaction" -> createTransaction(args?.get(0) as WalletTransactionCreateReqBO)
                else -> null
            }
        }
        service = PayWalletServiceImpl(PayWalletLockRedisDAO(redisson), transactionService)
    }

    @AfterEach
    fun tearDown() {
        TenantContextHolder.clear()
    }

    @Test
    fun `wallet mutations keep idempotency continuous balances and signed flow filters`() {
        val wallet = service.getOrCreateWallet(7L, 1)
        assertEquals(wallet.id, service.getOrCreateWallet(7L, 1).id)

        val recharge = service.addWalletBalance(
            requireNotNull(wallet.id),
            "recharge-1",
            PayWalletBizTypeEnum.RECHARGE,
            1000,
        )
        val duplicateRecharge = service.addWalletBalance(
            requireNotNull(wallet.id),
            "recharge-1",
            PayWalletBizTypeEnum.RECHARGE,
            1000,
        )
        val payment = service.reduceWalletBalance(
            requireNotNull(wallet.id),
            101L,
            PayWalletBizTypeEnum.PAYMENT,
            250,
        )
        val refund = service.addWalletBalance(
            requireNotNull(wallet.id),
            "refund-1",
            PayWalletBizTypeEnum.PAYMENT_REFUND,
            100,
        )

        assertEquals(recharge.id, duplicateRecharge.id)
        assertEquals(1000, recharge.balance)
        assertEquals(750, payment.balance)
        assertEquals(850, refund.balance)
        assertEquals(850, PayWalletDao.selectById(requireNotNull(wallet.id))?.balance)
        assertEquals(3L, PayWalletTransactionDao.selectCount())

        val income = PayWalletTransactionDao.selectPage(
            null,
            null,
            wallet.id,
            AppPayWalletTransactionPageReqVO.TYPE_INCOME,
            null,
            1,
            20,
        )
        val expense = PayWalletTransactionDao.selectPage(
            null,
            null,
            wallet.id,
            AppPayWalletTransactionPageReqVO.TYPE_EXPENSE,
            null,
            1,
            20,
        )
        assertEquals(listOf(100, 1000), income.list.map { it.price })
        assertEquals(listOf(-250), expense.list.map { it.price })

        PayWalletTransactionDao.insert(PayWalletTransactionDO().apply {
            no = "W-old"
            walletId = wallet.id
            bizType = PayWalletBizTypeEnum.RECHARGE.type
            bizId = "old"
            title = "Old"
            price = 999
            balance = 999
            createTime = kotlinx.datetime.LocalDateTime(2025, 1, 1, 0, 0)
            updateTime = createTime
        })
        val walletLookup = interfaceProxy<PayWalletService> { method, _ ->
            when (method) {
                "getOrCreateWallet" -> wallet
                else -> null
            }
        }
        val summary = PayWalletTransactionServiceImpl(walletLookup).getWalletTransactionSummary(
            7L,
            1,
            arrayOf(LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusMinutes(1)),
        )
        assertEquals(1100, summary.totalIncome)
        assertEquals(250, summary.totalExpense)
    }

    private fun createTransaction(bo: WalletTransactionCreateReqBO): PayWalletTransactionDO =
        PayWalletTransactionDO().apply {
            no = "W-${bo.bizType}-${bo.bizId}"
            walletId = bo.walletId
            bizType = bo.bizType
            bizId = bo.bizId
            title = bo.title
            price = bo.price
            balance = bo.balance
            PayWalletTransactionDao.insert(this)
        }
}
