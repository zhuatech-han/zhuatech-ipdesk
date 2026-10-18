// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
/** 元输入精确转整数分；不会使用浮点数累计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function cents(value) {
  const s = String(value).trim();
  if (!/^[0-9]+(?:\.[0-9]{1,2})?$/.test(s))
    throw new Error("金额须为正数，最多两位小数");
  const [a, b = ""] = s.split(".");
  const n = BigInt(a) * 100n + BigInt(b.padEnd(2, "0"));
  if (n <= 0n || n > 10000000000000n) throw new Error("金额超出允许范围");
  return n.toString();
}
/** 格式化服务端整数分，保留大数精度。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function money(value = "0") {
  const n = BigInt(value || 0);
  const a = n < 0n ? -n : n;
  return `${n < 0n ? "-" : ""}${(a / 100n).toLocaleString("zh-CN")}.${String(a % 100n).padStart(2, "0")}`;
}
/** 编辑表单用的无分组精确元值。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function yuan(value = "0") {
  const n = BigInt(value || 0);
  return `${n < 0n ? "-" : ""}${(n < 0n ? -n : n) / 100n}.${String((n < 0n ? -n : n) % 100n).padStart(2, "0")}`;
}
export const states = {
  DRAFT: "草稿",
  PENDING: "待独立复核",
  APPROVED: "已批准",
  REJECTED: "已驳回",
  ACTIVE: "办理中",
  CLOSING: "结案复核中",
  CLOSED: "已结案",
  REOPENING: "重开复核中",
  OPEN: "待办理",
  DONE_PENDING: "办结待复核",
  DONE: "已办结",
  CANCEL_PENDING: "取消待复核",
  CANCELLED: "已取消",
  SUPERSEDED: "已被新版本替代",
  NEW: "未读",
  READ: "已读",
  SENDING: "投递中",
  SENT: "已送达SMTP",
  UNKNOWN: "投递未确认",
  SKIPPED: "已停止投递",
  INAPP: "站内",
  EMAIL: "邮件",
  ALL: "全部",
  DEPARTMENT: "本部门",
  ASSIGNED: "本人负责",
  AGENT: "授权代理",
  PATENT: "专利",
  TRADEMARK: "商标",
  COPYRIGHT: "软件著作权",
  INTERNAL: "内部部门",
  AGENCY: "代理机构",
};
export const reasons = {
  SMTP_NOT_CONFIGURED: "邮件服务未配置或未启用",
  RECIPIENT_OR_TASK_CHANGED: "接收人、授权或任务已变化",
  DELIVERY_STATUS_UNKNOWN: "上次投递中断，请先核对收件箱",
  SMTP_DELIVERY_UNCONFIRMED: "SMTP投递结果未确认，请先核对收件箱",
};
/** 带会话和CSRF的统一请求，不把密码保存至浏览器存储。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export async function request(
  path,
  { method = "GET", body, blob = false } = {},
) {
  const headers = {};
  if (method !== "GET") {
    const r = await fetch("/api/auth/csrf", { credentials: "same-origin" });
    if (!r.ok) throw new Error("无法获取安全令牌，请刷新页面");
    const t = await r.json();
    headers[t.header] = t.token;
  }
  let payload = body;
  if (body && !(body instanceof FormData)) {
    headers["Content-Type"] = "application/json";
    payload = JSON.stringify(body);
  }
  const r = await fetch("/api" + path, {
    method,
    headers,
    body: payload,
    credentials: "same-origin",
  });
  if (!r.ok) {
    let info;
    try {
      info = await r.json();
    } catch {
      info = {};
    }
    const e = new Error(
      info.message ||
        {
          401: "登录已失效，请重新登录",
          403: "没有操作权限或安全令牌失效",
          413: "文件过大",
        }[r.status] ||
        "操作失败，请稍后重试",
    );
    e.status = r.status;
    throw e;
  }
  if (blob) return r.blob();
  const text = await r.text();
  return text ? JSON.parse(text) : null;
}
/** 生成附件下载后及时释放内存地址。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export async function download(path, name) {
  const b = await request(path, { blob: true });
  const url = URL.createObjectURL(b);
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
