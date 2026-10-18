// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.ipdesk;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;

/** 单实例60秒调度；失败保留队列状态，关闭调度不影响手动触发。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
    name = "ipdesk.scheduler-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ReminderJob {
  final Notifications service;

  public ReminderJob(Notifications service) {
    this.service = service;
  }

  /** 不打印收件人、来文或SMTP异常秘密。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Scheduled(fixedDelay = 60000, initialDelay = 60000)
  public void tick() {
    try {
      service.enqueue();
      service.dispatch();
    } catch (RuntimeException e) {
      org.slf4j.LoggerFactory.getLogger(ReminderJob.class)
          .warn("IPDesk reminder cycle failed; check database and delivery status.");
    }
  }
}
