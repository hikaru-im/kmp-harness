package im.hikaru.ruoyi.module.pay.framework.pay.core.enums

enum class PayOrderDisplayModeEnum(val mode: String) {
    URL("url"),
    IFRAME("iframe"),
    FORM("form"),
    QR_CODE("qr_code"),
    QR_CODE_URL("qr_code_url"),
    BAR_CODE("bar_code"),
    APP("app");
}
