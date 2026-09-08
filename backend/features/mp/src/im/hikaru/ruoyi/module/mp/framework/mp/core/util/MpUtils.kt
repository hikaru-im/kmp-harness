package im.hikaru.ruoyi.module.mp.framework.mp.core.util

import im.hikaru.ruoyi.framework.common.util.validation.ValidationUtils
import jakarta.validation.Validator
import me.chanjar.weixin.common.api.WxConsts
import org.slf4j.LoggerFactory

/** Utilities shared by menu and auto-reply validation. */
object MpUtils {
    private val log = LoggerFactory.getLogger(MpUtils::class.java)

    fun validateMessage(validator: Validator, type: String?, message: Any) {
        val group = when (type) {
            WxConsts.XmlMsgType.TEXT -> TextMessageGroup::class.java
            WxConsts.XmlMsgType.IMAGE -> ImageMessageGroup::class.java
            WxConsts.XmlMsgType.VOICE -> VoiceMessageGroup::class.java
            WxConsts.XmlMsgType.VIDEO -> VideoMessageGroup::class.java
            WxConsts.XmlMsgType.NEWS -> NewsMessageGroup::class.java
            WxConsts.XmlMsgType.MUSIC -> MusicMessageGroup::class.java
            else -> throw IllegalArgumentException("不支持的消息类型：$type")
        }
        ValidationUtils.validate(validator, message, group)
    }

    fun validateButton(validator: Validator, type: String?, messageType: String?, button: Any) {
        if (type.isNullOrBlank()) return
        val group = when (type) {
            WxConsts.MenuButtonType.CLICK -> {
                validateMessage(validator, messageType, button)
                ClickButtonGroup::class.java
            }
            WxConsts.MenuButtonType.VIEW -> ViewButtonGroup::class.java
            WxConsts.MenuButtonType.MINIPROGRAM -> MiniProgramButtonGroup::class.java
            WxConsts.MenuButtonType.SCANCODE_WAITMSG -> {
                validateMessage(validator, messageType, button)
                ScanCodeWaitMsgButtonGroup::class.java
            }
            "article_${WxConsts.MenuButtonType.VIEW_LIMITED}" -> ViewLimitedButtonGroup::class.java
            WxConsts.MenuButtonType.SCANCODE_PUSH,
            WxConsts.MenuButtonType.PIC_SYSPHOTO,
            WxConsts.MenuButtonType.PIC_PHOTO_OR_ALBUM,
            WxConsts.MenuButtonType.PIC_WEIXIN,
            WxConsts.MenuButtonType.LOCATION_SELECT -> return
            else -> throw IllegalArgumentException("不支持的按钮类型：$type")
        }
        ValidationUtils.validate(validator, button, group)
    }

    fun getMediaFileType(messageType: String?): String = when (messageType) {
        WxConsts.XmlMsgType.IMAGE -> WxConsts.MediaFileType.IMAGE
        WxConsts.XmlMsgType.VOICE -> WxConsts.MediaFileType.VOICE
        WxConsts.XmlMsgType.VIDEO -> WxConsts.MediaFileType.VIDEO
        else -> WxConsts.MediaFileType.FILE
    }

}

interface TextMessageGroup
interface ImageMessageGroup
interface VoiceMessageGroup
interface VideoMessageGroup
interface NewsMessageGroup
interface MusicMessageGroup
interface ClickButtonGroup
interface ViewButtonGroup
interface MiniProgramButtonGroup
interface ScanCodeWaitMsgButtonGroup
interface ViewLimitedButtonGroup
