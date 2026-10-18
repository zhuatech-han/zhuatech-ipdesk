// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.ipdesk;

import static cn.zhuatech.ipdesk.Models.*;

import java.time.*;
import java.util.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** 实时身份与部门/本人/显式代理案件范围，文件与报表使用相同门禁。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
public class Access {
  final Store db;
  final Clock clock;

  public Access(Store db, Clock clock) {
    this.db = db;
    this.clock = clock;
  }

  /** 每次请求检查最新账号、口令及机构状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Account current() {
    var a = SecurityContextHolder.getContext().getAuthentication();
    if (a == null || !a.isAuthenticated() || a.getName().equals("anonymousUser"))
      throw new Problem(401, "请先登录");
    var rows = db.query(Account.class, "from Account where username=?1", a.getName());
    if (rows.isEmpty()) throw new Problem(401, "登录已失效");
    var u = rows.getFirst();
    if (!u.enabled || !Objects.equals(a.getDetails(), u.passwordHash))
      throw new Problem(401, "登录已失效");
    if (!db.get(Department.class, u.departmentId).enabled) throw new Problem(403, "机构已停用");
    return u;
  }

  /** 当前最新角色。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Role role() {
    return db.get(Role.class, current().roleId);
  }

  /** 外部机构或代理角色一律以外部隔离规则处理。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public boolean external(Account a) {
    return db.get(Role.class, a.roleId).scope.equals("AGENT")
        || db.get(Department.class, a.departmentId).kind.equals("AGENCY");
  }

  /** 最新启用身份与权限用于后台提醒发送前再次检查。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public boolean valid(Account a) {
    return a.enabled
        && db.get(Department.class, a.departmentId).enabled
        && List.of(db.get(Role.class, a.roleId).permissions.split(",")).contains("matters");
  }

  /** 授予功能不绕过数据范围。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public boolean has(String p) {
    return List.of(role().permissions.split(",")).contains(p);
  }

  /** 要求明确功能。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Account require(String p) {
    var a = current();
    if (!has(p)) throw new Problem(403, "没有此操作权限");
    return a;
  }

  /** 企业内部功能禁止任何外部代理访问。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Account internal(String p) {
    var a = require(p);
    if (external(a)) throw new Problem(403, "仅限企业内部岗位");
    return a;
  }

  /** 系统管理要求内部全部范围。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void admin() {
    internal("admin");
    if (!role().scope.equals("ALL")) throw new Problem(403, "须全部范围系统管理员");
  }

  /** 账号仅能看到归属部门/本人负责或被显式授权的案件。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public boolean visible(Account a, Matter m) {
    if (!valid(a)) return false;
    if (external(a)) return Objects.equals(m.agentId, a.id);
    return switch (db.get(Role.class, a.roleId).scope) {
      case "ALL" -> true;
      case "DEPARTMENT" -> a.departmentId.equals(m.departmentId);
      case "ASSIGNED" -> a.id.equals(m.ownerId) || a.id.equals(m.creatorId);
      default -> false;
    };
  }

  /** 直接记录访问与列表同样校验，猜测编号也不泄露案件。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Matter matter(Long id) {
    var a = require("matters");
    var m = db.get(Matter.class, id);
    if (!visible(a, m)) throw new Problem(404, "案件不存在或不可访问");
    return m;
  }

  /** 提案仅内部人员按部门或本人范围访问。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public boolean proposalVisible(Proposal p) {
    var a = internal("proposals");
    return role().scope.equals("ALL")
        || (role().scope.equals("DEPARTMENT") && p.departmentId.equals(a.departmentId))
        || p.creatorId.equals(a.id);
  }

  /** 同一套范围过滤用于所有案件列表。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public List<Matter> matters() {
    var a = require("matters");
    return db.all(Matter.class).stream().filter(m -> visible(a, m)).toList();
  }

  /** 追加操作痕迹，不包含密码、SMTP凭据或文件内容。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void audit(String action, Object object, Long matterId, String note) {
    var u = current();
    var e = new Audit();
    e.actorId = u.id;
    e.departmentId = u.departmentId;
    e.matterId = matterId;
    e.action = action;
    e.objectRef = Objects.toString(object, "");
    e.note = Rules.optional(note, 1600);
    e.createdAt = clock.instant();
    db.save(e);
  }

  /** 返回当前账号、可见菜单与非秘密参数。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> profile() {
    var r = role();
    return Map.of(
        "account",
        current(),
        "role",
        r,
        "permissions",
        r.permissions.isBlank() ? List.of() : List.of(r.permissions.split(",")),
        "menus",
        db.all(Menu.class).stream()
            .filter(m -> m.enabled && has(m.permission))
            .sorted(Comparator.comparingInt(m -> m.position))
            .toList(),
        "settings",
        db.all(Setting.class));
  }
}
