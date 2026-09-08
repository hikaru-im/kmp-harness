package im.hikaru.ruoyi.module.system.api.dept

import im.hikaru.ruoyi.module.system.api.dept.dto.PostRespDTO

interface PostApi {
    fun validPostList(ids: Collection<Long>)
    fun getPostList(ids: Collection<Long>): List<PostRespDTO>
    fun getPostMap(ids: Collection<Long>): Map<Long, PostRespDTO> =
        if (ids.isEmpty()) emptyMap() else getPostList(ids).mapNotNull { dto -> dto.id?.let { it to dto } }.toMap()
}
