package im.hikaru.ruoyi.module.system.service.dept

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.system.controller.admin.dept.vo.post.PostPageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.dept.vo.post.PostSaveReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.dept.PostDO

interface PostService {
    fun createPost(req: PostSaveReqVO): Long
    fun updatePost(req: PostSaveReqVO)
    fun deletePost(id: Long)
    fun deletePostList(ids: List<Long>)
    fun getPostList(ids: Collection<Long>?): List<PostDO>
    fun getPostList(ids: Collection<Long>?, statuses: Collection<Int>?): List<PostDO>
    fun getPostPage(req: PostPageReqVO): PageResult<PostDO>
    fun getPost(id: Long): PostDO?
    fun validatePostList(ids: Collection<Long>)
}
