package im.hikaru.ruoyi.module.infra.controller.admin.demo.demo03.normal.vo

import im.hikaru.ruoyi.module.infra.dal.dataobject.demo.demo03.Demo03CourseDO
import im.hikaru.ruoyi.module.infra.dal.dataobject.demo.demo03.Demo03GradeDO
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

@Schema(description = "Admin student create/update request (normal mode)")
class Demo03StudentNormalSaveReqVO {
    var id: Long? = null

    @field:NotEmpty(message = "Name must not be empty")
    var name: String? = null

    @field:NotNull(message = "Sex must not be null")
    var sex: Int? = null

    @field:NotNull(message = "Birthday must not be null")
    var birthday: LocalDateTime? = null

    @field:NotEmpty(message = "Description must not be empty")
    var description: String? = null

    var demo03Courses: List<Demo03CourseDO>? = null
    var demo03Grade: Demo03GradeDO? = null
}
