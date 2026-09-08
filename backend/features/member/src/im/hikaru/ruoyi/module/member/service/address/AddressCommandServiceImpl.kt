package im.hikaru.ruoyi.module.member.service.address

import im.hikaru.contracts.app.member.MemberAddressSyncContract
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.member.controller.app.address.vo.AppAddressCreateReqVO
import im.hikaru.ruoyi.module.member.controller.app.address.vo.AppAddressUpdateReqVO
import im.hikaru.ruoyi.module.member.convert.address.AddressConvert
import im.hikaru.ruoyi.module.member.dal.dataobject.address.MemberAddressDO
import im.hikaru.ruoyi.module.member.dal.mysql.address.MemberAddressDao
import im.hikaru.ruoyi.module.sync.service.SyncChangeOperation
import im.hikaru.ruoyi.module.sync.service.SyncChangeWriter
import im.hikaru.ruoyi.module.sync.service.SyncCommandContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AddressCommandServiceImpl(
    private val changeWriter: SyncChangeWriter,
) : AddressCommandService {
    @Transactional(rollbackFor = [Exception::class])
    override fun create(userId: Long, request: AppAddressCreateReqVO): MemberAddressDO {
        if (request.defaultStatus == true) clearOtherDefaults(userId, null)
        val address = AddressConvert.convert(request).apply {
            this.userId = userId
            version = INITIAL_VERSION
        }
        MemberAddressDao.insert(address)
        appendUpsert(userId, address)
        return address
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun update(
        userId: Long,
        request: AppAddressUpdateReqVO,
        expectedVersion: Long?,
    ): AddressMutationResult {
        val id = requireNotNull(request.id)
        repeat(MAX_OPTIMISTIC_ATTEMPTS) {
            val current = MemberAddressDao.selectByIdAndUserId(id, userId)
                ?: return AddressMutationResult.NotFound
            if (expectedVersion != null && current.version != expectedVersion) {
                return AddressMutationResult.Conflict(current.version, current)
            }

            val update = AddressConvert.convert(request).apply { this.id = id }
            if (MemberAddressDao.updateByIdAndVersion(userId, update, current.version) == 1) {
                if (request.defaultStatus == true) clearOtherDefaults(userId, id)
                val applied = checkNotNull(MemberAddressDao.selectByIdAndUserId(id, userId))
                appendUpsert(userId, applied)
                return AddressMutationResult.Applied(applied)
            }

            if (expectedVersion != null) {
                val serverAddress = MemberAddressDao.selectByIdAndUserId(id, userId)
                    ?: return AddressMutationResult.NotFound
                return AddressMutationResult.Conflict(serverAddress.version, serverAddress)
            }
        }
        val serverAddress = MemberAddressDao.selectByIdAndUserId(id, userId)
            ?: return AddressMutationResult.NotFound
        return AddressMutationResult.Conflict(serverAddress.version, serverAddress)
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun delete(
        userId: Long,
        id: Long,
        expectedVersion: Long?,
    ): AddressMutationResult {
        repeat(MAX_OPTIMISTIC_ATTEMPTS) {
            val current = MemberAddressDao.selectByIdAndUserId(id, userId)
                ?: return AddressMutationResult.NotFound
            if (expectedVersion != null && current.version != expectedVersion) {
                return AddressMutationResult.Conflict(current.version, current)
            }
            if (MemberAddressDao.deleteByIdAndVersion(userId, id, current.version) == 1) {
                val deleted = current.apply { version += 1 }
                changeWriter.append(
                    context = context(userId),
                    resource = MemberAddressSyncContract.RESOURCE,
                    aggregateId = id.toString(),
                    operation = SyncChangeOperation.DELETE,
                    aggregateVersion = deleted.version,
                    payload = AddressDeleteChangePayload(id, deleted.version),
                )
                return AddressMutationResult.Applied(deleted)
            }
            if (expectedVersion != null) {
                val serverAddress = MemberAddressDao.selectByIdAndUserId(id, userId)
                    ?: return AddressMutationResult.NotFound
                return AddressMutationResult.Conflict(serverAddress.version, serverAddress)
            }
        }
        val serverAddress = MemberAddressDao.selectByIdAndUserId(id, userId)
            ?: return AddressMutationResult.NotFound
        return AddressMutationResult.Conflict(serverAddress.version, serverAddress)
    }

    private fun clearOtherDefaults(userId: Long, excludedId: Long?) {
        MemberAddressDao.selectListByUserIdAndDefaultStatus(userId, true)
            .filter { it.id != excludedId }
            .forEach { current ->
                val id = requireNotNull(current.id)
                val update = MemberAddressDO().apply {
                    this.id = id
                    defaultStatus = false
                }
                check(MemberAddressDao.updateByIdAndVersion(userId, update, current.version) == 1) {
                    "An address default changed concurrently"
                }
                appendUpsert(
                    userId,
                    checkNotNull(MemberAddressDao.selectByIdAndUserId(id, userId)),
                )
            }
    }

    private fun appendUpsert(userId: Long, address: MemberAddressDO) {
        changeWriter.append(
            context = context(userId),
            resource = MemberAddressSyncContract.RESOURCE,
            aggregateId = requireNotNull(address.id).toString(),
            operation = SyncChangeOperation.UPSERT,
            aggregateVersion = address.version,
            payload = AddressChangePayload(
                id = requireNotNull(address.id),
                name = address.name,
                mobile = address.mobile,
                areaId = address.areaId,
                detailAddress = address.detailAddress,
                defaultStatus = address.defaultStatus ?: false,
                version = address.version,
            ),
        )
    }

    private fun context(userId: Long): SyncCommandContext = SyncCommandContext(
        tenantId = TenantContextHolder.getRequiredTenantId(),
        userId = userId,
    )

    private companion object {
        const val INITIAL_VERSION = 1L
        const val MAX_OPTIMISTIC_ATTEMPTS = 3
    }
}

private data class AddressChangePayload(
    val id: Long,
    val name: String?,
    val mobile: String?,
    val areaId: Long?,
    val detailAddress: String?,
    val defaultStatus: Boolean,
    val version: Long,
)

private data class AddressDeleteChangePayload(
    val id: Long,
    val version: Long,
)
