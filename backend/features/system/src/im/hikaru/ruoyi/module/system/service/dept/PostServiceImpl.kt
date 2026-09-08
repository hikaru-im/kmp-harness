package im.hikaru.ruoyi.module.system.service.dept

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.system.controller.admin.dept.vo.post.PostPageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.dept.vo.post.PostSaveReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.dept.PostDO
import im.hikaru.ruoyi.module.system.dal.mysql.dept.PostDao
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.POST_CODE_DUPLICATE
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.POST_NAME_DUPLICATE
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.POST_NOT_ENABLE
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.POST_NOT_FOUND
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

@Service
@Validated
class PostServiceImpl : PostService {
    override fun createPost(req: PostSaveReqVO): Long { validate(null, requireNotNull(req.name), requireNotNull(req.code)); return PostDao.insert(req.toEntity()) }
    override fun updatePost(req: PostSaveReqVO) { val id = requireNotNull(req.id); validate(id, requireNotNull(req.name), requireNotNull(req.code)); PostDao.updateById(req.toEntity()) }
    override fun deletePost(id: Long) { validateExists(id); PostDao.deleteById(id) }
    override fun deletePostList(ids: List<Long>) = PostDao.deleteByIds(ids).let { }
    override fun getPostList(ids: Collection<Long>?) = PostDao.selectList(ids, null)
    override fun getPostList(ids: Collection<Long>?, statuses: Collection<Int>?) = PostDao.selectList(ids, statuses)
    override fun getPostPage(req: PostPageReqVO): PageResult<PostDO> = PostDao.selectPage(req)
    override fun getPost(id: Long) = PostDao.selectById(id)
    override fun validatePostList(ids: Collection<Long>) { val map = getPostList(ids).associateBy { it.id }; ids.forEach { val post = map[it] ?: throw exception(POST_NOT_FOUND); if (post.status != CommonStatusEnum.ENABLE.status) throw exception(POST_NOT_ENABLE, post.name ?: "") } }
    private fun validate(id: Long?, name: String, code: String) { validateExists(id); PostDao.selectByName(name)?.let { if (it.id != id) throw exception(POST_NAME_DUPLICATE) }; PostDao.selectByCode(code)?.let { if (it.id != id) throw exception(POST_CODE_DUPLICATE) } }
    private fun validateExists(id: Long?) { if (id != null && PostDao.selectById(id) == null) throw exception(POST_NOT_FOUND) }
    private fun PostSaveReqVO.toEntity() = PostDO().apply { id = this@toEntity.id; name = this@toEntity.name; code = this@toEntity.code; sort = this@toEntity.sort; status = this@toEntity.status; remark = this@toEntity.remark }
}
