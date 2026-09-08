package im.hikaru.ruoyi.framework.common.pojo

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "可排序的分页参数")
class SortablePageParam : PageParam() {
    @Schema(description = "排序字段")
    var sortingFields: List<SortingField>? = null
}
