package im.hikaru.ruoyi.module.mp.service.material

import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.infra.api.file.FileApi
import im.hikaru.ruoyi.module.mp.controller.admin.material.vo.MpMaterialPageReqVO
import im.hikaru.ruoyi.module.mp.controller.admin.material.vo.MpMaterialUploadNewsImageReqVO
import im.hikaru.ruoyi.module.mp.controller.admin.material.vo.MpMaterialUploadPermanentReqVO
import im.hikaru.ruoyi.module.mp.controller.admin.material.vo.MpMaterialUploadTemporaryReqVO
import im.hikaru.ruoyi.module.mp.convert.material.MpMaterialConvert
import im.hikaru.ruoyi.module.mp.dal.dataobject.material.MpMaterialDO
import im.hikaru.ruoyi.module.mp.dal.mysql.material.MpMaterialDao
import im.hikaru.ruoyi.module.mp.enums.ErrorCodeConstants.MATERIAL_DELETE_FAIL
import im.hikaru.ruoyi.module.mp.enums.ErrorCodeConstants.MATERIAL_IMAGE_UPLOAD_FAIL
import im.hikaru.ruoyi.module.mp.enums.ErrorCodeConstants.MATERIAL_NOT_EXISTS
import im.hikaru.ruoyi.module.mp.enums.ErrorCodeConstants.MATERIAL_UPLOAD_FAIL
import im.hikaru.ruoyi.module.mp.framework.mp.core.MpServiceFactory
import im.hikaru.ruoyi.module.mp.service.account.MpAccountService
import me.chanjar.weixin.common.error.WxErrorException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.nio.file.Files

@Service
@Validated
class MpMaterialServiceImpl(
    private val fileApi: FileApi,
    private val mpAccountServiceProvider: ObjectProvider<MpAccountService>,
    private val mpServiceFactoryProvider: ObjectProvider<MpServiceFactory>,
) : MpMaterialService {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val mpAccountService: MpAccountService
        get() = mpAccountServiceProvider.getObject()
    private val mpServiceFactory: MpServiceFactory
        get() = mpServiceFactoryProvider.getObject()

    override fun downloadMaterialUrl(accountId: Long, mediaId: String, type: String): String? {
        MpMaterialDao.selectByAccountIdAndMediaId(accountId, mediaId)?.url?.let { return it }
        val url = downloadMedia(accountId, mediaId) ?: return null
        val account = mpAccountService.getRequiredAccount(accountId)
        MpMaterialDao.insert(MpMaterialConvert.convert(mediaId, type, url, account, null).apply { permanent = false })
        return url
    }

    override fun uploadTemporaryMaterial(reqVO: MpMaterialUploadTemporaryReqVO): MpMaterialDO {
        val accountId = requireNotNull(reqVO.accountId)
        val type = requireNotNull(reqVO.type)
        val multipart = requireNotNull(reqVO.file)
        val file = multipart.toTemporaryFile()
        try {
            val result = mpServiceFactory.getRequiredMpService(accountId).materialService.mediaUpload(type, file)
            val mediaId = result.mediaId ?: result.thumbMediaId
            val url = uploadFile(requireNotNull(mediaId), file)
            val material = MpMaterialConvert.convert(mediaId, type, url, mpAccountService.getRequiredAccount(accountId), multipart.originalFilename)
                .apply { permanent = false }
            MpMaterialDao.insert(material)
            return material
        } catch (ex: Exception) {
            throw exception(MATERIAL_UPLOAD_FAIL, wxErrorMessage(ex))
        } finally {
            Files.deleteIfExists(file.toPath())
        }
    }

    override fun uploadPermanentMaterial(reqVO: MpMaterialUploadPermanentReqVO): MpMaterialDO {
        val accountId = requireNotNull(reqVO.accountId)
        val type = requireNotNull(reqVO.type)
        val multipart = requireNotNull(reqVO.file)
        val name = reqVO.name?.takeIf(String::isNotBlank) ?: multipart.originalFilename ?: multipart.name
        val file = multipart.toTemporaryFile()
        try {
            val result = mpServiceFactory.getRequiredMpService(accountId).materialService.materialFileUpload(
                type,
                MpMaterialConvert.convert(name, file, reqVO.title, reqVO.introduction),
            )
            val mediaId = requireNotNull(result.mediaId)
            val url = uploadFile(mediaId, file)
            val material = MpMaterialConvert.convert(
                mediaId,
                type,
                url,
                mpAccountService.getRequiredAccount(accountId),
                name,
                reqVO.title,
                reqVO.introduction,
                result.url,
            ).apply { permanent = true }
            MpMaterialDao.insert(material)
            return material
        } catch (ex: Exception) {
            throw exception(MATERIAL_UPLOAD_FAIL, wxErrorMessage(ex))
        } finally {
            Files.deleteIfExists(file.toPath())
        }
    }

    override fun uploadNewsImage(reqVO: MpMaterialUploadNewsImageReqVO): String {
        val file = requireNotNull(reqVO.file).toTemporaryFile()
        try {
            return mpServiceFactory.getRequiredMpService(requireNotNull(reqVO.accountId)).materialService
                .mediaImgUpload(file).url
        } catch (ex: Exception) {
            throw exception(MATERIAL_IMAGE_UPLOAD_FAIL, wxErrorMessage(ex))
        } finally {
            Files.deleteIfExists(file.toPath())
        }
    }

    override fun getMaterialPage(pageReqVO: MpMaterialPageReqVO): PageResult<MpMaterialDO> =
        MpMaterialDao.selectPage(pageReqVO)

    override fun getMaterialListByMediaId(mediaIds: Collection<String>): List<MpMaterialDO> =
        MpMaterialDao.selectListByMediaId(mediaIds)

    override fun deleteMaterial(id: Long) {
        val material = MpMaterialDao.selectById(id) ?: throw exception(MATERIAL_NOT_EXISTS)
        if (material.permanent == true) {
            try {
                mpServiceFactory.getRequiredMpService(requireNotNull(material.appId)).materialService
                    .materialDelete(requireNotNull(material.mediaId))
            } catch (ex: Exception) {
                throw exception(MATERIAL_DELETE_FAIL, wxErrorMessage(ex))
            }
        }
        MpMaterialDao.deleteById(id)
    }

    internal fun downloadMedia(accountId: Long, mediaId: String): String? {
        val service = mpServiceFactory.getMpService(accountId) ?: return null
        repeat(3) { attempt ->
            try {
                return uploadFile(mediaId, service.materialService.mediaDownload(mediaId))
            } catch (ex: WxErrorException) {
                logger.warn("Failed to download WeChat media {} on attempt {}", mediaId, attempt + 1)
            }
        }
        return null
    }

    private fun uploadFile(mediaId: String, file: File): String {
        val extension = file.extension.ifBlank { "bin" }
        return fileApi.createFile(file.readBytes(), "$mediaId.$extension")
    }

    private fun MultipartFile.toTemporaryFile(): File {
        val suffix = originalFilename?.substringAfterLast('.', "")?.takeIf(String::isNotBlank)?.let { ".$it" } ?: ".tmp"
        return Files.createTempFile("mp-material-", suffix).toFile().also(::transferTo)
    }

    private fun wxErrorMessage(ex: Exception): String =
        (ex as? WxErrorException)?.error?.errorMsg ?: ex.message.orEmpty()
}
