package im.hikaru.ruoyi.module.infra.enums

import im.hikaru.ruoyi.framework.common.exception.ErrorCode

/** Error codes owned by the infrastructure module. */
object ErrorCodeConstants {
    val CONFIG_NOT_EXISTS = ErrorCode(1_002_000_000, "Config does not exist")
    val CONFIG_NAME_DUPLICATE = ErrorCode(1_002_000_001, "Config key already exists")
    val CONFIG_KEY_DUPLICATE = CONFIG_NAME_DUPLICATE
    val CONFIG_CAN_NOT_DELETE_SYSTEM = ErrorCode(1_002_000_002, "A system config cannot be deleted")
    val CONFIG_CAN_NOT_DELETE_SYSTEM_TYPE = CONFIG_CAN_NOT_DELETE_SYSTEM
    val CONFIG_GET_VALUE_ERROR_IF_VISIBLE = ErrorCode(1_002_000_003, "A hidden config value cannot be read")

    val FILE_PATH_EXISTS = ErrorCode(1_002_001_000, "File path already exists")
    val FILE_NOT_EXISTS = ErrorCode(1_002_001_001, "File does not exist")
    val FILE_CONFIG_NOT_EXISTS = ErrorCode(1_002_001_002, "File configuration does not exist")
    val FILE_CONFIG_EXISTS = ErrorCode(1_002_001_003, "File configuration is still in use")
    val FILE_PATH_DUPLICATE = ErrorCode(1_002_001_004, "File path already exists")
    val FILE_CONFIG_DELETE_FAIL_MASTER = ErrorCode(1_002_001_005, "The master file configuration cannot be deleted")
    val FILE_IS_EMPTY = ErrorCode(1_002_001_006, "File is empty")
    val FILE_PATH_INVALID = ErrorCode(1_002_001_007, "File path is invalid")

    val API_ERROR_LOG_NOT_FOUND = ErrorCode(1_002_002_000, "API error log does not exist")
    val API_ERROR_LOG_PROCESSED = ErrorCode(1_002_002_001, "API error log has already been processed")

    val CODEGEN_IMPORT_TABLE_NULL = ErrorCode(1_002_003_001, "The imported table does not exist")
    val CODEGEN_TABLE_EXISTS = ErrorCode(1_002_003_002, "The code generation table already exists")
    val CODEGEN_IMPORT_COLUMNS_NULL = ErrorCode(1_002_003_003, "The imported table has no columns")
    val CODEGEN_TABLE_NOT_EXISTS = ErrorCode(1_002_003_004, "The code generation table does not exist")
    val CODEGEN_COLUMN_NOT_EXISTS = ErrorCode(1_002_003_005, "The code generation table has no columns")
    val CODEGEN_SYNC_NONE_CHANGE = ErrorCode(1_002_003_007, "The database schema has not changed")
    val CODEGEN_TABLE_INFO_TABLE_COMMENT_IS_NULL = ErrorCode(1_002_003_008, "The database table comment is empty")
    val CODEGEN_TABLE_INFO_COLUMN_COMMENT_IS_NULL = ErrorCode(1_002_003_009, "Database column ({}) has no comment")
    val CODEGEN_MASTER_TABLE_NOT_EXISTS = ErrorCode(1_002_003_010, "Master table ({}) does not exist")
    val CODEGEN_SUB_COLUMN_NOT_EXISTS = ErrorCode(1_002_003_011, "Sub-table column ({}) does not exist")
    val CODEGEN_MASTER_GENERATION_FAIL_NO_SUB_TABLE = ErrorCode(1_002_003_012, "The master table has no sub-table")

    val JOB_NOT_EXISTS = ErrorCode(1_002_004_000, "Job does not exist")
    val JOB_HANDLER_EXISTS = ErrorCode(1_002_004_001, "Job handler already exists")
    val JOB_CHANGE_STATUS_INVALID = ErrorCode(1_002_004_002, "Only normal or stopped status is allowed")
    val JOB_CHANGE_STATUS_EQUALS = ErrorCode(1_002_004_003, "The job is already in this status")
    val JOB_UPDATE_ONLY_NORMAL_STATUS = ErrorCode(1_002_004_004, "Only a running job can be updated")
    val JOB_CRON_EXPRESSION_VALID = ErrorCode(1_002_004_005, "The CRON expression is invalid")
    val JOB_HANDLER_BEAN_NOT_EXISTS = ErrorCode(1_002_004_006, "The job handler bean does not exist")
    val JOB_HANDLER_BEAN_TYPE_ERROR = ErrorCode(1_002_004_007, "The job handler bean has an invalid type")

    val DATA_SOURCE_CONFIG_NOT_EXISTS = ErrorCode(1_002_005_000, "Data source configuration does not exist")

    val DEMO02_CATEGORY_NOT_EXISTS = ErrorCode(1_002_006_000, "Demo category does not exist")
    val DEMO02_CATEGORY_NAME_DUPLICATE = ErrorCode(1_002_006_001, "A category with the same parent and name already exists")
    val DEMO02_CATEGORY_EXITS_CHILDREN = ErrorCode(1_002_006_002, "The category still has children")
    val DEMO02_CATEGORY_PARENT_ERROR = ErrorCode(1_002_006_003, "A category cannot be its own parent")
    val DEMO02_CATEGORY_PARENT_NOT_EXITS = ErrorCode(1_002_006_004, "The category parent does not exist")
    val DEMO02_CATEGORY_PARENT_IS_CHILD = ErrorCode(1_002_006_005, "A category cannot use a descendant as its parent")

    val DEMO01_CONTACT_NOT_EXISTS = ErrorCode(1_002_007_000, "Demo contact does not exist")

    val DEMO03_STUDENT_NOT_EXISTS = ErrorCode(1_001_201_007, "Student does not exist")
    val DEMO03_COURSE_NOT_EXISTS = ErrorCode(1_001_201_008, "Student course does not exist")
    val DEMO03_GRADE_NOT_EXISTS = ErrorCode(1_001_201_009, "Student grade does not exist")
    val DEMO03_GRADE_EXISTS = ErrorCode(1_001_201_010, "Student grade already exists")
}
