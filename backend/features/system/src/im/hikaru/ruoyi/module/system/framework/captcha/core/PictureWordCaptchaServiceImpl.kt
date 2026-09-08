package im.hikaru.ruoyi.module.system.framework.captcha.core

import com.anji.captcha.model.common.RepCodeEnum
import com.anji.captcha.model.common.ResponseModel
import com.anji.captcha.model.vo.CaptchaVO
import com.anji.captcha.service.impl.AbstractCaptchaService
import com.anji.captcha.service.impl.CaptchaServiceFactory
import com.anji.captcha.util.AESUtil
import com.anji.captcha.util.ImageUtils
import com.anji.captcha.util.RandomUtils
import java.awt.Color
import java.awt.Font
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.util.Properties
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class PictureWordCaptchaServiceImpl : AbstractCaptchaService() {
    override fun init(config: Properties) = super.init(config)

    override fun destroy(config: Properties) {
        logger.info("start-clear-history-data-{}", captchaType())
    }

    override fun captchaType() = "pictureWord"

    override fun get(captchaVO: CaptchaVO): ResponseModel =
        ResponseModel.successData(createImageData(generateRandomText(LENGTH)))

    override fun check(captchaVO: CaptchaVO): ResponseModel {
        val requestValidation = super.check(captchaVO)
        if (!validatedReq(requestValidation)) return requestValidation

        val codeKey = String.format(REDIS_CAPTCHA_KEY, captchaVO.token)
        val cache = CaptchaServiceFactory.getCache(cacheType)
        if (!cache.exists(codeKey)) return ResponseModel.errorMsg(RepCodeEnum.API_CAPTCHA_INVALID)

        val codeValue = cache.get(codeKey)
        val code = codeValue.substringBefore(',')
        val secretKey = codeValue.substringAfter(',')
        cache.delete(codeKey)

        val userCode = captchaVO.pointJson
        if (!code.equals(userCode, ignoreCase = true)) {
            afterValidateFail(captchaVO)
            return ResponseModel.errorMsg(RepCodeEnum.API_CAPTCHA_COORDINATE_ERROR)
        }

        val value = try {
            AESUtil.aesEncrypt("${captchaVO.token}---$userCode", secretKey)
        } catch (exception: Exception) {
            logger.error("Failed to encrypt captcha verification", exception)
            afterValidateFail(captchaVO)
            return ResponseModel.errorMsg(exception.message)
        }
        val secondKey = String.format(REDIS_SECOND_CAPTCHA_KEY, value)
        cache.set(secondKey, captchaVO.token, EXPIRESIN_THREE)
        captchaVO.result = true
        captchaVO.resetClientFlag()
        return ResponseModel.successData(captchaVO)
    }

    override fun verification(captchaVO: CaptchaVO): ResponseModel {
        val requestValidation = super.verification(captchaVO)
        if (!validatedReq(requestValidation)) return requestValidation
        return try {
            val codeKey = String.format(REDIS_SECOND_CAPTCHA_KEY, captchaVO.captchaVerification)
            val cache = CaptchaServiceFactory.getCache(cacheType)
            if (!cache.exists(codeKey)) {
                ResponseModel.errorMsg(RepCodeEnum.API_CAPTCHA_INVALID)
            } else {
                cache.delete(codeKey)
                ResponseModel.success()
            }
        } catch (exception: Exception) {
            logger.error("Failed to verify captcha", exception)
            ResponseModel.errorMsg(exception.message)
        }
    }

    private fun createImageData(text: String): CaptchaVO {
        val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        graphics.color = randomColor(200, 250)
        graphics.fillRect(0, 0, WIDTH, HEIGHT)

        repeat(LINES) {
            graphics.color = randomColor(100, 200)
            graphics.drawLine(
                Random.nextInt(WIDTH),
                Random.nextInt(HEIGHT),
                Random.nextInt(WIDTH),
                Random.nextInt(HEIGHT),
            )
        }

        graphics.font = Font("Arial", Font.BOLD, 24)
        text.forEachIndexed { index, character ->
            graphics.color = randomColor(20, 130)
            val x = 20 + index * 20
            val y = 24 + Random.nextInt(8)
            graphics.transform = AffineTransform.getRotateInstance(
                Math.toRadians(Random.nextInt(-45, 45).toDouble()),
                x.toDouble(),
                y.toDouble(),
            )
            graphics.drawString(character.toString(), x, y)
        }
        graphics.dispose()

        repeat(100) {
            image.setRGB(Random.nextInt(WIDTH), Random.nextInt(HEIGHT), randomColor(0, 255).rgb)
        }

        val secretKey = if (captchaAesStatus == true) AESUtil.getKey() else null
        return CaptchaVO().apply {
            this.secretKey = secretKey
            originalImageBase64 = ImageUtils.getImageToBase64Str(image).replace("\r", "").replace("\n", "")
            token = RandomUtils.getUUID()
            val codeKey = String.format(REDIS_CAPTCHA_KEY, token)
            CaptchaServiceFactory.getCache(cacheType).set(
                codeKey,
                "$text,$secretKey",
                EXPIRESIN_SECONDS,
            )
        }
    }

    private fun randomColor(first: Int, second: Int): Color {
        val lower = min(first, second)
        val upper = max(first, second)
        return Color(
            Random.nextInt(lower, upper),
            Random.nextInt(lower, upper),
            Random.nextInt(lower, upper),
        )
    }

    companion object {
        private const val CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private const val LENGTH = 4
        private const val WIDTH = 120
        private const val HEIGHT = 40
        private const val LINES = 10

        fun generateRandomText(length: Int): String =
            buildString(length) { repeat(length) { append(CHARACTERS.random()) } }
    }
}
