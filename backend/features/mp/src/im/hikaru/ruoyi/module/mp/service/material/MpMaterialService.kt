package im.hikaru.ruoyi.module.mp.service.material

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.mp.controller.admin.material.vo.MpMaterialPageReqVO
import im.hikaru.ruoyi.module.mp.controller.admin.material.vo.MpMaterialUploadNewsImageReqVO
import im.hikaru.ruoyi.module.mp.controller.admin.material.vo.MpMaterialUploadPermanentReqVO
import im.hikaru.ruoyi.module.mp.controller.admin.material.vo.MpMaterialUploadTemporaryReqVO
import im.hikaru.ruoyi.module.mp.dal.dataobject.material.MpMaterialDO
import jakarta.validation.Valid

interface MpMaterialService {
    fun downloadMaterialUrl(accountId: Long, mediaId: String, type: String): String?
    fun uploadTemporaryMaterial(@Valid reqVO: MpMaterialUploadTemporaryReqVO): MpMaterialDO
    fun uploadPermanentMaterial(@Valid reqVO: MpMaterialUploadPermanentReqVO): MpMaterialDO
    fun uploadNewsImage(reqVO: MpMaterialUploadNewsImageReqVO): String
    fun getMaterialPage(pageReqVO: MpMaterialPageReqVO): PageResult<MpMaterialDO>
    fun getMaterialListByMediaId(mediaIds: Collection<String>): List<MpMaterialDO>
    fun deleteMaterial(id: Long): Unit
}
