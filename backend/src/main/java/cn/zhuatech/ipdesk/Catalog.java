// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.ipdesk;

import static cn.zhuatech.ipdesk.Models.*;

import java.util.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 企业与代理目录、权限、字典和非秘密参数的显式字段维护。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class Catalog {
  final Store db;
  final Access access;
  final BCryptPasswordEncoder encoder;

  public Catalog(Store db, Access access, BCryptPasswordEncoder encoder) {
    this.db = db;
    this.access = access;
    this.encoder = encoder;
  }

  /** 非秘密管理员目录与完整审计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object list(String type) {
    access.admin();
    return switch (type) {
      case "accounts" -> db.all(Account.class);
      case "roles" -> db.all(Role.class);
      case "departments" -> db.all(Department.class);
      case "dictionary" -> db.all(Entry.class);
      case "menus" -> db.all(Menu.class);
      case "settings" -> db.all(Setting.class);
      case "audit" -> db.all(Audit.class).reversed();
      case "permissions" ->
          Bootstrap.PERMISSIONS.entrySet().stream()
              .map(e -> Map.of("code", e.getKey(), "label", e.getValue()))
              .toList();
      default -> throw new Problem(404, "目录不存在");
    };
  }

  /** 接收邮箱严格单地址，默认关闭邮件；不修改SMTP秘密。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String email(Object value) {
    String s = Rules.optional(value, 254);
    Rules.check(
        s.isEmpty()
            || s.matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+"),
        "邮箱须为单个有效地址");
    return s;
  }

  /** 更新允许字段；稳定归属与外部权限边界不可绕过。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object save(String type, Long id, Map<String, Object> b) {
    db.writeLock();
    access.admin();
    Row row;
    switch (type) {
      case "accounts" -> {
        var a = id == null ? new Account() : db.get(Account.class, id);
        if (id != null) {
          Rules.version(a, b.get("version"));
          Rules.check(id != 1, "初始管理员使用本人改密入口");
        }
        var d = db.get(Department.class, Rules.id(b.get("departmentId")));
        var r = db.get(Role.class, Rules.id(b.get("roleId")));
        Rules.check(d.enabled, "机构已停用");
        Rules.check(
            d.kind.equals("AGENCY") == r.scope.equals("AGENT"), "外部机构只能使用代理角色，内部部门不能使用代理角色");
        if (id != null) Rules.check(d.id.equals(a.departmentId), "账号归属固定，请停用旧账号后新建");
        String name = Rules.text(b.get("username"), 60).toLowerCase(Locale.ROOT);
        Rules.check(name.matches("[a-z0-9_.-]{3,60}"), "登录名格式错误");
        if (id != null) Rules.check(name.equals(a.username), "登录名不可修改");
        String p = Rules.optional(b.get("password"), 128);
        if (id == null || !p.isEmpty()) {
          Rules.password(p);
          a.passwordHash = encoder.encode(p);
        }
        a.username = name;
        a.displayName = Rules.text(b.get("displayName"), 120);
        a.departmentId = d.id;
        a.roleId = r.id;
        a.enabled = Rules.flag(b.get("enabled"));
        a.email = email(b.get("email"));
        a.mailOptIn = Rules.flag(b.get("mailOptIn"));
        Rules.check(!a.mailOptIn || !a.email.isEmpty(), "启用邮件需填写收件地址");
        if (id == null) db.save(a);
        row = a;
      }
      case "roles" -> {
        var r = id == null ? new Role() : db.get(Role.class, id);
        if (id != null) {
          Rules.version(r, b.get("version"));
          Rules.check(id != 1, "初始管理员角色受保护");
        }
        String scope = Rules.text(b.get("scope"), 20);
        Rules.check(Set.of("ALL", "DEPARTMENT", "ASSIGNED", "AGENT").contains(scope), "范围错误");
        if (id != null) Rules.check(scope.equals(r.scope), "角色范围不可改变，请新建角色");
        Rules.check(b.get("permissions") instanceof List<?>, "请选择权限");
        var perms = new TreeSet<String>();
        for (var v : (List<?>) b.get("permissions")) {
          String p = Rules.text(v, 60);
          Rules.check(Bootstrap.PERMISSIONS.containsKey(p), "未知权限");
          perms.add(p);
        }
        if (scope.equals("AGENT"))
          Rules.check(
              Set.of("matters", "delivery", "notifications").containsAll(perms), "代理角色不能包含企业内部权限");
        Rules.check(!perms.contains("admin") || scope.equals("ALL"), "管理员须全部范围");
        Rules.check(
            perms.isEmpty() || perms.equals(Set.of("admin")) || perms.contains("matters"),
            "业务岗位须包含案件查看");
        r.name = Rules.text(b.get("name"), 120);
        r.scope = scope;
        r.permissions = String.join(",", perms);
        if (id == null) db.save(r);
        row = r;
      }
      case "departments" -> {
        var d = id == null ? new Department() : db.get(Department.class, id);
        if (id != null) {
          Rules.version(d, b.get("version"));
          Rules.check(id != 1, "初始部门受保护");
          Rules.check(
              d.code.equals(Rules.code(b.get("code"))) && d.kind.equals(b.get("kind")),
              "部门代码和类型不可改变");
        }
        d.code = Rules.code(b.get("code"));
        d.name = Rules.text(b.get("name"), 120);
        d.kind = Rules.text(b.get("kind"), 20);
        Rules.check(Set.of("INTERNAL", "AGENCY").contains(d.kind), "机构类型错误");
        d.enabled = Rules.flag(b.get("enabled"));
        if (id == null) db.save(d);
        row = d;
      }
      case "dictionary" -> {
        var e = id == null ? new Entry() : db.get(Entry.class, id);
        if (id != null) {
          Rules.version(e, b.get("version"));
          Rules.check(
              e.code.equals(Rules.code(b.get("code"))) && e.groupCode.equals(b.get("groupCode")),
              "代码和分组不可改变");
        }
        e.code = Rules.code(b.get("code"));
        e.groupCode = Rules.text(b.get("groupCode"), 40);
        Rules.check(Set.of("DEADLINE", "FEE").contains(e.groupCode), "分组错误");
        e.label = Rules.text(b.get("label"), 120);
        e.enabled = Rules.flag(b.get("enabled"));
        if (id == null) db.save(e);
        row = e;
      }
      case "menus" -> {
        Rules.check(id != null, "只维护现有菜单");
        var m = db.get(Menu.class, id);
        Rules.version(m, b.get("version"));
        m.label = Rules.text(b.get("label"), 120);
        m.position = Integer.parseInt(b.get("position").toString());
        Rules.check(m.position >= 0 && m.position <= 100, "排序须0至100");
        m.enabled = Rules.flag(b.get("enabled"));
        row = m;
      }
      case "settings" -> {
        Rules.check(id != null, "只维护现有参数");
        var s = db.get(Setting.class, id);
        Rules.version(s, b.get("version"));
        String v = Rules.text(b.get("parameterValue"), 1000);
        switch (s.code) {
          case "app_name" -> Rules.text(v, 80);
          case "warning_days" ->
              Rules.check(Integer.parseInt(v) >= 1 && Integer.parseInt(v) <= 180, "提前天数须1至180");
          case "max_case_mb" ->
              Rules.check(Integer.parseInt(v) >= 10 && Integer.parseInt(v) <= 100, "容量须10至100MB");
          case "reminders_enabled", "email_enabled" ->
              Rules.check(Set.of("true", "false").contains(v), "开关须true或false");
          default -> throw new Problem(400, "未知参数");
        }
        s.parameterValue = v;
        row = s;
      }
      default -> throw new Problem(404, "目录不存在");
    }
    db.flush();
    access.audit("CATALOG_SAVE", type + ":" + row.id, null, "");
    return row;
  }

  /** 外键保护历史引用，账号通过停用保留。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void delete(String type, Long id, Object version) {
    db.writeLock();
    access.admin();
    Row r =
        switch (type) {
          case "roles" -> db.get(Role.class, id);
          case "departments" -> db.get(Department.class, id);
          case "dictionary" -> db.get(Entry.class, id);
          default -> throw new Problem(400, "此目录不允许删除");
        };
    Rules.check(!((type.equals("roles") || type.equals("departments")) && id == 1), "初始目录受保护");
    Rules.version(r, version);
    access.audit("CATALOG_DELETE", type + ":" + id, null, "");
    db.delete(r);
  }

  /** 表单只得到必要的人名和启用字典，代理不获得企业人员目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> references() {
    var a = access.current();
    boolean ext = access.external(a), all = access.role().scope.equals("ALL");
    return Map.of(
        "people",
        db.all(Account.class).stream()
            .filter(
                u ->
                    access.valid(u)
                        && (ext
                            ? u.id.equals(a.id)
                            : (all || u.departmentId.equals(a.departmentId) || access.external(u))))
            .map(
                u ->
                    Map.of(
                        "id",
                        u.id,
                        "name",
                        u.displayName,
                        "departmentId",
                        u.departmentId,
                        "external",
                        access.external(u)))
            .toList(),
        "dictionary",
        db.all(Entry.class).stream().filter(e -> e.enabled).toList());
  }
}
