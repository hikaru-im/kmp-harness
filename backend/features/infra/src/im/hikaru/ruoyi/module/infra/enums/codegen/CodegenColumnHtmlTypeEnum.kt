package im.hikaru.ruoyi.module.infra.enums.codegen

enum class CodegenColumnHtmlTypeEnum(val type: String) {
    INPUT("input"),
    TEXTAREA("textarea"),
    SELECT("select"),
    RADIO("radio"),
    CHECKBOX("checkbox"),
    DATETIME("datetime"),
    IMAGE_UPLOAD("imageUpload"),
    FILE_UPLOAD("fileUpload"),
    EDITOR("editor"),
}
