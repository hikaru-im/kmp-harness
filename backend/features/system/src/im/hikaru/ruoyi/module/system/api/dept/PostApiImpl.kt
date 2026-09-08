package im.hikaru.ruoyi.module.system.api.dept

import im.hikaru.ruoyi.module.system.api.dept.dto.PostRespDTO
import im.hikaru.ruoyi.module.system.service.dept.PostService
import org.springframework.stereotype.Service

@Service
class PostApiImpl(private val service: PostService) : PostApi {
    override fun validPostList(ids: Collection<Long>) = service.validatePostList(ids)
    override fun getPostList(ids: Collection<Long>): List<PostRespDTO> = service.getPostList(ids).map { PostRespDTO().apply { id = it.id; name = it.name; code = it.code; sort = it.sort; status = it.status } }
}
