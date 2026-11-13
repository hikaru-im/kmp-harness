package im.hikaru.harness.desktop.notifications

import im.hikaru.harness.client.notifications.SystemNotification
import im.hikaru.harness.client.notifications.SystemNotificationResult
import im.hikaru.harness.client.notifications.SystemNotificationService
import java.awt.AWTException
import java.awt.GraphicsEnvironment
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage

/**
 * Desktop notification adapter. A transparent tray icon keeps this independent of shell commands;
 * unsupported/headless environments and OS security refusals remain explicit result states.
 */
public class DesktopSystemNotificationService : SystemNotificationService {
    private var trayIcon: TrayIcon? = null
    private var closed = false

    override fun send(notification: SystemNotification): SystemNotificationResult {
        if (closed) return SystemNotificationResult.Failed("通知服务已关闭")
        if (GraphicsEnvironment.isHeadless()) return SystemNotificationResult.Unsupported("当前环境没有桌面通知")
        val tray = try {
            SystemTray.getSystemTray()
        } catch (denied: SecurityException) {
            return SystemNotificationResult.Denied("系统拒绝访问通知权限")
        } catch (failure: Exception) {
            return SystemNotificationResult.Unsupported("当前平台不支持桌面通知")
        }
        val supported = try {
            SystemTray.isSupported()
        } catch (denied: SecurityException) {
            return SystemNotificationResult.Denied("系统拒绝访问通知权限")
        } catch (failure: Exception) {
            return SystemNotificationResult.Unsupported("当前平台不支持桌面通知")
        }
        if (!supported) return SystemNotificationResult.Unsupported("当前平台不支持桌面通知")
        return try {
            val icon = trayIcon ?: TrayIcon(
                BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).also { image ->
                    image.setRGB(0, 0, 0x00000000)
                },
                "Harness",
            ).also {
                it.isImageAutoSize = true
                tray.add(it)
                trayIcon = it
            }
            icon.displayMessage(notification.title, notification.body, TrayIcon.MessageType.INFO)
            SystemNotificationResult.Delivered
        } catch (denied: SecurityException) {
            SystemNotificationResult.Denied("系统拒绝显示通知")
        } catch (unsupported: UnsupportedOperationException) {
            SystemNotificationResult.Unsupported("当前平台不支持桌面通知")
        } catch (awt: AWTException) {
            SystemNotificationResult.Failed("无法注册桌面通知入口")
        } catch (failure: Exception) {
            SystemNotificationResult.Failed("无法显示桌面通知")
        }
    }

    override fun close() {
        closed = true
        val icon = trayIcon ?: return
        try {
            if (SystemTray.isSupported()) SystemTray.getSystemTray().remove(icon)
        } catch (_: Exception) {
            // Shutdown must not be blocked by a platform tray cleanup failure.
        } finally {
            trayIcon = null
        }
    }
}
