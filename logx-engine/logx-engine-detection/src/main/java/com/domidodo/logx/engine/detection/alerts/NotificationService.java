package com.domidodo.logx.engine.detection.alerts;

import com.domidodo.logx.engine.detection.entity.Alert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 通知服务
 * 负责发送告警通知（邮件、短信、Webhook等）
 */
@Slf4j
@Service
public class NotificationService {

    /**
     * 待发送队列
     */
    private final BlockingQueue<Alert> pendingQueue = new LinkedBlockingQueue<>(10000);

    /**
     * 告警计数器（用于合并通知）
     */
    private final Map<String, Integer> alertCounter = new ConcurrentHashMap<>();

    /**
     * 立即发送通知（严重告警）
     */
    public void sendImmediate(Alert alert) {
        try {
            log.info("正在发送警报的即时通知：id={}，级别={}",
                    alert.getId(), alert.getAlertLevel());

            // TODO: 实现实际的通知发送
            // 1. 查询通知配置
            // 2. 根据配置选择通知渠道
            // 3. 发送通知

            // 模拟发送
            sendEmail(alert);
            sendSms(alert);
            sendWebhook(alert);

        } catch (Exception e) {
            log.error("未能立即发送通知", e);
        }
    }

    /**
     * 添加到队列（非严重告警，批量发送）
     */
    public void addToQueue(Alert alert) {
        try {
            pendingQueue.offer(alert);

            // 增加计数
            String key = alert.getTenantId() + ":" + alert.getAlertLevel();
            alertCounter.merge(key, 1, Integer::sum);

            log.debug("已将警报添加到队列：id={}，queue_size={}",
                    alert.getId(), pendingQueue.size());
        } catch (Exception e) {
            log.error("向队列添加警报失败", e);
        }
    }

    /**
     * 定时批量发送通知（每小时一次）
     */
    @Scheduled(cron = "0 0 * * * ?") // 每小时整点执行
    public void sendBatchNotifications() {
        try {
            if (pendingQueue.isEmpty()) {
                log.debug("没有待发送的警报");
                return;
            }

            List<Alert> alerts = new ArrayList<>();
            pendingQueue.drainTo(alerts, 1000); // 最多取1000条

            if (alerts.isEmpty()) {
                return;
            }

            log.info("发送批处理通知：计数={}", alerts.size());

            // 按租户分组
            Map<String, List<Alert>> groupedAlerts = groupByTenant(alerts);

            // 发送汇总通知
            for (Map.Entry<String, List<Alert>> entry : groupedAlerts.entrySet()) {
                sendSummaryNotification(entry.getKey(), entry.getValue());
            }

            // 清空计数器
            alertCounter.clear();

        } catch (Exception e) {
            log.error("发送批量通知失败", e);
        }
    }

    /**
     * 按租户分组
     */
    private Map<String, List<Alert>> groupByTenant(List<Alert> alerts) {
        Map<String, List<Alert>> grouped = new ConcurrentHashMap<>();
        for (Alert alert : alerts) {
            grouped.computeIfAbsent(alert.getTenantId(), k -> new ArrayList<>()).add(alert);
        }
        return grouped;
    }

    /**
     * 发送汇总通知
     */
    private void sendSummaryNotification(String tenantId, List<Alert> alerts) {
        try {
            log.info("正在向租户发送摘要通知：{}，计数={}",
                    tenantId, alerts.size());

            // TODO: 实现实际的汇总通知
            // 1. 查询租户的通知配置
            // 2. 构建汇总内容
            // 3. 发送通知

            // 构建汇总内容
            StringBuilder summary = new StringBuilder();
            summary.append("【LogX告警汇总】\n\n");
            summary.append("租户: ").append(tenantId).append("\n");
            summary.append("告警数量: ").append(alerts.size()).append("\n\n");

            // 按级别统计
            long criticalCount = alerts.stream()
                    .filter(a -> "CRITICAL".equals(a.getAlertLevel())).count();
            long warningCount = alerts.stream()
                    .filter(a -> "WARNING".equals(a.getAlertLevel())).count();
            long infoCount = alerts.stream()
                    .filter(a -> "INFO".equals(a.getAlertLevel())).count();

            summary.append("严重: ").append(criticalCount).append("\n");
            summary.append("警告: ").append(warningCount).append("\n");
            summary.append("提示: ").append(infoCount).append("\n\n");

            summary.append("详情请登录控制台查看。");

            // 模拟发送邮件
            log.info("摘要通知内容：\n{}", summary);

        } catch (Exception e) {
            log.error("发送摘要通知失败", e);
        }
    }

    /**
     * 发送邮件（TODO: 实现实际的邮件发送）
     */
    private void sendEmail(Alert alert) {
        log.info("发送电子邮件通知：alertId={}", alert.getId());
        // TODO: 集成邮件服务（JavaMail）
    }

    /**
     * 发送短信（TODO: 实现实际的短信发送）
     */
    private void sendSms(Alert alert) {
        log.info("正在发送短信通知：alertId={}", alert.getId());
        // TODO: 集成短信服务（阿里云/腾讯云）
    }

    /**
     * 发送Webhook（TODO: 实现实际的Webhook发送）
     */
    private void sendWebhook(Alert alert) {
        log.info("正在发送webhook通知：alertId={}", alert.getId());
        // TODO: 实现Webhook调用（企业微信/钉钉/飞书）
    }

    /**
     * 获取队列大小
     */
    public int getQueueSize() {
        return pendingQueue.size();
    }

    /**
     * 获取告警计数
     */
    public Map<String, Integer> getAlertCounter() {
        return new ConcurrentHashMap<>(alertCounter);
    }
}