package im.hikaru.ruoyi.server

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * yudao-server 启动入口 (迁移自 reference/yudao-server)。
 */
@SpringBootApplication(scanBasePackages = ["im.hikaru.ruoyi"])
class YudaoServerApplication

fun main(args: Array<String>) {
    runApplication<YudaoServerApplication>(*args)
}
