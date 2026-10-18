// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.ipdesk;

import static cn.zhuatech.ipdesk.Models.*;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 持久化站内/邮件提醒，发送前重核授权，发送状态不明时不自动重发。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
public class Notifications {
  final Store db;
  final Access access;
  final Clock clock;
  final TransactionTemplate tx;
  final boolean enabled, tls;
  final String host, username, password, from;
  final int port;

  public Notifications(
      Store db,
      Access access,
      Clock clock,
      PlatformTransactionManager manager,
      @Value("${ipdesk.mail-enabled:false}") boolean enabled,
      @Value("${ipdesk.mail-host:}") String host,
      @Value("${ipdesk.mail-port:587}") int port,
      @Value("${ipdesk.mail-username:}") String username,
      @Value("${ipdesk.mail-password:}") String password,
      @Value("${ipdesk.mail-from:}") String from,
      @Value("${ipdesk.mail-tls:true}") boolean tls) {
    this.db = db;
    this.access = access;
    this.clock = clock;
    this.tx = new TransactionTemplate(manager);
    this.enabled = enabled;
    this.host = host;
    this.port = port;
    this.username = username;
    this.password = password;
    this.from = from;
    this.tls = tls;
  }

  String setting(String code) {
    return db.query(Setting.class, "from Setting where code=?1", code).getFirst().parameterValue;
  }

  boolean live(Deadline d) {
    return Set.of("OPEN", "DONE_PENDING", "CANCEL_PENDING").contains(d.state);
  }

  boolean eligible(Account a, Deadline d, Matter m) {
    return List.of(db.get(Role.class, a.roleId).permissions.split(",")).contains("notifications")
        && live(d)
        && m.state.equals("ACTIVE")
        && access.visible(a, m)
        && (a.id.equals(d.responsibleId) || (!access.external(a) && a.id.equals(m.ownerId)));
  }

  /** 按日期、期限版本、通道、接收人生成去重提醒，不对待复核新期限发消息。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public int enqueue() {
    return tx.execute(
        status -> {
          db.writeLock();
          if (!setting("reminders_enabled").equals("true")) return 0;
          int count = 0;
          var today = LocalDate.now(clock);
          int days = Integer.parseInt(setting("warning_days"));
          for (var d : db.all(Deadline.class)) {
            if (!live(d) || d.dueOn.isAfter(today.plusDays(days))) continue;
            var m = db.get(Matter.class, d.matterId);
            var recipients = new LinkedHashSet<Long>(List.of(m.ownerId, d.responsibleId));
            for (Long id : recipients) {
              var a = db.get(Account.class, id);
              if (!eligible(a, d, m)) continue;
              for (String channel : List.of("INAPP", "EMAIL")) {
                if (channel.equals("EMAIL")
                    && (!setting("email_enabled").equals("true")
                        || !a.mailOptIn
                        || a.email.isEmpty())) continue;
                String key =
                    d.id
                        + ":"
                        + d.version
                        + ":"
                        + today
                        + ":"
                        + id
                        + ":"
                        + channel
                        + ":"
                        + Rules.digest(a.email.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                            .substring(0, 12);
                if (!db.query(Alert.class, "from Alert where dedupeKey=?1", key).isEmpty())
                  continue;
                var n = new Alert();
                n.deadlineId = d.id;
                n.recipientId = id;
                n.dedupeKey = key;
                n.channel = channel;
                n.state = channel.equals("INAPP") ? "NEW" : "PENDING";
                n.email = channel.equals("EMAIL") ? a.email : "";
                n.createdAt = clock.instant();
                db.save(n);
                count++;
              }
            }
          }
          return count;
        });
  }

  /** 每次先提交认领，再实际投递，最后独立写回；重启不盲重发已认领邮件。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public int dispatch() {
    int sent = 0;
    for (int i = 0; i < 20; i++) {
      var message =
          tx.execute(
              status -> {
                db.writeLock();
                for (var stale :
                    db.query(Alert.class, "from Alert where channel='EMAIL' and state='SENDING'")) {
                  if (stale.claimedAt.isBefore(clock.instant().minusSeconds(120))) {
                    stale.state = "UNKNOWN";
                    stale.reason = "DELIVERY_STATUS_UNKNOWN";
                  }
                }
                var pending =
                    db.query(
                        Alert.class,
                        "from Alert where channel='EMAIL' and state='PENDING' order by id");
                if (pending.isEmpty()) return null;
                var n = pending.getFirst();
                var d = db.get(Deadline.class, n.deadlineId);
                var m = db.get(Matter.class, d.matterId);
                var a = db.get(Account.class, n.recipientId);
                if (!setting("reminders_enabled").equals("true")
                    || !setting("email_enabled").equals("true")
                    || !eligible(a, d, m)
                    || !a.mailOptIn
                    || !a.email.equals(n.email)) {
                  n.state = "SKIPPED";
                  n.reason = "RECIPIENT_OR_TASK_CHANGED";
                  return Map.<String, Object>of("skip", true);
                }
                if (!configured()) {
                  n.state = "SKIPPED";
                  n.reason = "SMTP_NOT_CONFIGURED";
                  return Map.<String, Object>of("skip", true);
                }
                n.state = "SENDING";
                n.claimedAt = clock.instant();
                n.attempts++;
                return Map.<String, Object>of(
                    "id",
                    n.id,
                    "to",
                    a.email,
                    "subject",
                    "IPDesk期限提醒 · " + m.code,
                    "body",
                    "案件："
                        + m.code
                        + "\n事项："
                        + d.title
                        + "\n登记截止日期："
                        + d.dueOn
                        + "\n请登录机构IPDesk核对并办理。办理提交后仍需内部复核。\n");
              });
      if (message == null) break;
      if (message.containsKey("skip")) continue;
      boolean ok = false;
      try {
        var sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(username);
        sender.setPassword(password);
        var props = sender.getJavaMailProperties();
        props.setProperty("mail.smtp.auth", Boolean.toString(!username.isBlank()));
        props.setProperty("mail.smtp.starttls.enable", Boolean.toString(tls));
        props.setProperty("mail.smtp.starttls.required", Boolean.toString(tls));
        props.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        props.setProperty("mail.smtp.connectiontimeout", "5000");
        props.setProperty("mail.smtp.timeout", "5000");
        props.setProperty("mail.smtp.writetimeout", "5000");
        var mail = new SimpleMailMessage();
        mail.setFrom(from);
        mail.setTo(message.get("to").toString());
        mail.setSubject(message.get("subject").toString());
        mail.setText(message.get("body").toString());
        sender.send(mail);
        ok = true;
      } catch (Exception e) {
        /* 不记录包含账号、地址或服务器信息的异常细节；所有非确定投递需人工核对。 */
      }
      boolean delivered = ok;
      tx.executeWithoutResult(
          status -> {
            db.writeLock();
            var n = db.get(Alert.class, (Long) message.get("id"));
            n.state = delivered ? "SENT" : "UNKNOWN";
            n.reason = delivered ? "" : "SMTP_DELIVERY_UNCONFIRMED";
            if (delivered) n.sentAt = clock.instant();
          });
      if (ok) sent++;
    }
    return sent;
  }

  /** 只报告SMTP可用配置状态，不输出主机、账号或秘密值。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public boolean configured() {
    return enabled
        && !host.isBlank()
        && from.matches(
            "[A-Za-z0-9.!#$%&' *+/=?^_`{|}~-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+".replace(" ", ""))
        && port > 0
        && port <= 65535;
  }

  /** 本人通知按当前案件授权重新过滤；管理端仅内部全部范围访问投递记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object list(boolean admin) {
    return tx.execute(
        status -> {
          var a = access.require("notifications");
          if (admin) access.admin();
          var rows =
              db.all(Alert.class).stream()
                  .filter(
                      n ->
                          admin
                              || (n.recipientId.equals(a.id)
                                  && eligible(
                                      a,
                                      db.get(Deadline.class, n.deadlineId),
                                      db.get(
                                          Matter.class,
                                          db.get(Deadline.class, n.deadlineId).matterId))))
                  .map(
                      n -> {
                        var d = db.get(Deadline.class, n.deadlineId);
                        var m = db.get(Matter.class, d.matterId);
                        Map<String, Object> v = new LinkedHashMap<>();
                        v.put("id", n.id);
                        v.put("version", n.version);
                        v.put("deadlineId", d.id);
                        v.put("matterId", m.id);
                        v.put("caseCode", m.code);
                        v.put("title", d.title);
                        v.put("dueOn", d.dueOn);
                        v.put("channel", n.channel);
                        v.put("state", n.state);
                        v.put("reason", n.reason);
                        v.put("attempts", n.attempts);
                        v.put("createdAt", n.createdAt);
                        v.put("sentAt", n.sentAt);
                        if (admin) {
                          v.put("recipientId", n.recipientId);
                          v.put("email", n.email);
                        }
                        return v;
                      })
                  .toList()
                  .reversed();
          return Map.of("items", rows, "smtpConfigured", configured());
        });
  }

  /** 站内已读只允许本人，SMTP状态不明重试须管理员确认已核对邮箱；最多3次尝试。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void action(Long id, String action, Map<String, Object> b) {
    tx.executeWithoutResult(
        status -> {
          db.writeLock();
          var n = db.get(Alert.class, id);
          Rules.version(n, b.get("version"));
          if (action.equals("read")) {
            var a = access.current();
            Rules.check(n.recipientId.equals(a.id) && n.channel.equals("INAPP"), "仅本人站内提醒可标记");
            access.matter(db.get(Deadline.class, n.deadlineId).matterId);
            n.state = "READ";
          } else if (action.equals("retry")) {
            access.admin();
            Rules.check(
                n.channel.equals("EMAIL") && Set.of("UNKNOWN", "SKIPPED").contains(n.state),
                "此记录不能重试");
            Rules.check(n.attempts < 3, "已达3次尝试上限");
            Rules.check(Rules.flag(b.get("confirmedNotDelivered")), "需先检查收件箱及SMTP记录，确认未送达");
            n.state = "PENDING";
            n.reason = "";
            access.audit("MAIL_RETRY", n.id, null, "管理员确认未送达");
          } else throw new Problem(400, "操作不存在");
        });
  }

  /** 管理员手动触发与后台调度共用持久化队列。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Integer> runNow() {
    tx.executeWithoutResult(s -> access.admin());
    int created = enqueue(), sent = dispatch();
    return Map.of("created", created, "sent", sent);
  }
}
