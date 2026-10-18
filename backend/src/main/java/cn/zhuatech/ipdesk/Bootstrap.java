// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.ipdesk;

import static cn.zhuatech.ipdesk.Models.*;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 空库初始化目录和随机口令管理员，不植入业务数据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class Bootstrap implements ApplicationRunner {
  public static final LinkedHashMap<String, String> PERMISSIONS = new LinkedHashMap<>();

  static {
    for (String p :
        List.of(
            "matters:案件查看",
            "proposals:技术提案",
            "proposal.review:提案独立评审",
            "case.write:立案与代理授权",
            "case.review:结案独立复核",
            "deadline.write:期限编制与改期",
            "deadline.review:期限独立复核",
            "delivery:办理与交付",
            "finance:费用申请与凭据登记",
            "finance.review:费用与付款独立复核",
            "reports:统计与导出",
            "notifications:本人提醒",
            "admin:系统管理")) {
      var s = p.split(":");
      PERMISSIONS.put(s[0], s[1]);
    }
  }

  final Store db;
  final BCryptPasswordEncoder encoder;
  final String username, password;

  public Bootstrap(
      Store db,
      BCryptPasswordEncoder encoder,
      @Value("${ADMIN_USERNAME:admin}") String username,
      @Value("${ADMIN_PASSWORD}") String password) {
    this.db = db;
    this.encoder = encoder;
    this.username = username;
    this.password = password;
  }

  /** 目录在同一事务首次建立，重启不改写现有业务与口令。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    db.writeLock();
    if (!db.all(Account.class).isEmpty()) return;
    Rules.password(password);
    var d = new Department();
    d.code = "HQ";
    d.name = "知识产权中心";
    d.kind = "INTERNAL";
    db.save(d);
    role("系统管理员", "ALL", String.join(",", PERMISSIONS.keySet()));
    role("提案人与案件负责人", "ASSIGNED", "matters,proposals,delivery,reports,notifications");
    role(
        "企业知产经办",
        "DEPARTMENT",
        "matters,proposals,case.write,deadline.write,delivery,reports,notifications");
    role(
        "企业知产复核",
        "DEPARTMENT",
        "matters,proposals,proposal.review,case.review,deadline.review,reports,notifications");
    role("财务经办", "DEPARTMENT", "matters,finance,reports,notifications");
    role("财务复核", "DEPARTMENT", "matters,finance.review,reports,notifications");
    role("外部代理", "AGENT", "matters,delivery,notifications");
    var a = new Account();
    a.username = Rules.text(username, 60).toLowerCase(Locale.ROOT);
    Rules.check(a.username.matches("[a-z0-9_.-]{3,60}"), "初始账号格式不正确");
    a.displayName = "系统管理员";
    a.roleId = 1L;
    a.departmentId = d.id;
    a.passwordHash = encoder.encode(password);
    db.save(a);
    int pos = 0;
    for (var x :
        new String[][] {
          {"overview", "工作总览", "matters"},
          {"proposals", "技术提案", "proposals"},
          {"matters", "知识产权案件", "matters"},
          {"reports", "业务统计", "reports"},
          {"notifications", "提醒中心", "notifications"},
          {"admin", "系统管理", "admin"}
        }) {
      var m = new Menu();
      m.code = x[0];
      m.label = x[1];
      m.permission = x[2];
      m.position = ++pos;
      db.save(m);
    }
    for (var x :
        new String[][] {
          {"DEADLINE", "REPLY", "来文答复"},
          {"DEADLINE", "RENEWAL", "年费与续展"},
          {"DEADLINE", "FILING", "申请交付"},
          {"FEE", "OFFICIAL", "官费"},
          {"FEE", "AGENCY", "代理服务费"}
        }) {
      var e = new Entry();
      e.groupCode = x[0];
      e.code = x[1];
      e.label = x[2];
      db.save(e);
    }
    setting("app_name", "工作台名称", "知华 IPDesk");
    setting("warning_days", "到期提醒提前天数", "30");
    setting("reminders_enabled", "启用到期提醒", "true");
    setting("email_enabled", "启用邮件通道", "false");
    setting("max_case_mb", "单案件文件容量MB", "40");
  }

  void role(String n, String s, String p) {
    var r = new Role();
    r.name = n;
    r.scope = s;
    r.permissions = p;
    db.save(r);
  }

  void setting(String c, String l, String v) {
    var s = new Setting();
    s.code = c;
    s.label = l;
    s.parameterValue = v;
    db.save(s);
  }
}
