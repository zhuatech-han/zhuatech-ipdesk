<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted, shallowRef } from "vue";
import DataGrid from "./DataGrid.vue";
import FormDialog from "./FormDialog.vue";
import {
  request,
  download,
  money,
  yuan,
  cents,
  states,
  reasons,
} from "./domain.js";
const profile = ref(null),
  boot = ref(true),
  busy = ref(false),
  error = ref(""),
  notice = ref(""),
  route = ref("overview"),
  caseTab = ref("deadlines"),
  adminTab = ref("accounts");
const username = ref(""),
  password = ref(""),
  refs = ref({ people: [], dictionary: [] }),
  proposals = ref([]),
  list = ref({ items: [], total: 0 }),
  detail = ref(null),
  report = ref({ due: [] }),
  alerts = ref({ items: [], smtpConfigured: false }),
  catalog = ref([]),
  roles = ref([]),
  departments = ref([]),
  permissions = ref([]),
  dialog = shallowRef(null),
  formError = ref("");
const query = ref(""),
  filter = ref(""),
  sort = ref("id"),
  page = ref(1),
  proposalQuery = ref("");
const has = (p) => profile.value?.permissions.includes(p),
  me = computed(() => profile.value?.account.id),
  external = computed(() => profile.value?.role.scope === "AGENT"),
  matter = computed(() => detail.value?.matter),
  active = computed(() => matter.value?.state === "ACTIVE");
const appName = computed(
  () =>
    profile.value?.settings.find((s) => s.code === "app_name")
      ?.parameterValue || "知华 IPDesk",
);
const title = computed(() =>
  route.value === "case"
    ? matter.value?.title
    : profile.value?.menus.find((m) => m.code === route.value)?.label ||
      "知识产权协同",
);
const approved = computed(() =>
  proposals.value.filter((p) => p.state === "APPROVED"),
);
const proposalRows = computed(() =>
  proposals.value.filter((p) =>
    (p.title + " " + p.summary)
      .toLowerCase()
      .includes(proposalQuery.value.toLowerCase()),
  ),
);
const person = (id) =>
  refs.value.people.find((p) => p.id === id)?.name ||
  detail.value?.contacts?.find((p) => p.id === id)?.name ||
  `账号 #${id}`;
const category = (id) =>
  refs.value.dictionary.find((p) => p.id === id)?.label || `分类 #${id}`;
const opts = (rows, key = "name") =>
  rows.map((r) => ({ value: r.id, label: r[key] }));
const categoryOptions = (group) =>
  opts(
    refs.value.dictionary.filter((e) => e.groupCode === group),
    "label",
  );
const fileOpts = computed(
  () =>
    detail.value?.files.map((f) => ({
      value: f.id,
      label: `${f.fileName} · v${f.versionNumber} · ${f.shared ? "共享" : "内部"}`,
    })) || [],
);
const fields = (rows) =>
  rows.map(([key, label, type = "text", options, required = true]) => ({
    key,
    label,
    type,
    options,
    required,
  }));
const rightOptions = ["PATENT", "TRADEMARK", "COPYRIGHT"].map((value) => ({
  value,
  label: states[value],
}));
const canReview = (r, p, field = "creatorId") =>
  has(p) && r[field] !== me.value && r.submitterId !== me.value;
const fileName = (id) =>
  detail.value?.files.find((f) => f.id === id)?.fileName || "案件附件";
const feePaid = (id) =>
  detail.value?.paid?.find((p) => p.feeId === id) || {
    approved: "0",
    reserved: "0",
  };
const today = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
};
function clear() {
  refs.value = { people: [], dictionary: [] };
  proposals.value = [];
  list.value = { items: [], total: 0 };
  detail.value = null;
  report.value = { due: [] };
  alerts.value = { items: [], smtpConfigured: false };
  catalog.value = [];
  roles.value = [];
  departments.value = [];
  permissions.value = [];
  dialog.value = null;
  query.value = "";
  filter.value = "";
  page.value = 1;
}
function fail(e) {
  error.value = e.message;
  if (e.status === 401) {
    profile.value = null;
    clear();
    password.value = "";
  } else if ([403, 404].includes(e.status)) {
    clear();
    route.value = "overview";
  }
}
async function run(fn, message = "操作已保存") {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    await fn();
    notice.value = message;
  } catch (e) {
    fail(e);
  } finally {
    busy.value = false;
  }
}
/** 以会话和CSRF令牌登录机构账号。知华科技 https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。 */
async function login() {
  await run(async () => {
    clear();
    profile.value = await request("/auth/login", {
      method: "POST",
      body: { username: username.value, password: password.value },
    });
    password.value = "";
    route.value = profile.value.menus[0]?.code || "overview";
    await refresh();
  }, "");
}
/** 退出并清除页面中当前账号的数据。知华科技 https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。 */
async function logout() {
  await run(async () => {
    await request("/auth/logout", { method: "POST" });
    profile.value = null;
    clear();
    password.value = "";
  }, "");
}
/** 按当前权限刷新可见业务数据。知华科技 https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。 */
async function refresh() {
  profile.value = await request("/auth/me");
  refs.value = await request("/references");
  if (route.value === "case" && matter.value) {
    detail.value = await request(`/matters/${matter.value.id}`);
  } else if (route.value === "proposals") {
    proposals.value = await request("/proposals");
  } else if (route.value === "matters") {
    list.value = await request(
      `/matters?q=${encodeURIComponent(query.value)}&state=${encodeURIComponent(filter.value)}&page=${page.value}&sort=${sort.value}`,
    );
    if (has("case.write")) proposals.value = await request("/proposals");
  } else if (["overview", "reports"].includes(route.value)) {
    report.value = await request("/reports");
  } else if (route.value === "notifications") {
    alerts.value = await request("/notifications");
  } else if (route.value === "admin") {
    await loadAdmin();
  }
}
async function navigate(code) {
  await run(async () => {
    detail.value = null;
    route.value = code;
    page.value = 1;
    await refresh();
  }, "");
}
/** 读取已授权案件及其工作台。知华科技 https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。 */
async function openCase(id) {
  await run(async () => {
    detail.value = await request(`/matters/${id}`);
    route.value = "case";
    caseTab.value = "deadlines";
  }, "");
}
async function changeAdmin(key) {
  adminTab.value = key;
  await run(loadAdmin, "");
}
/** 读取选定的管理资源。知华科技 https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。 */
async function loadAdmin() {
  if (adminTab.value === "outbox") {
    alerts.value = await request("/notifications?admin=true");
    catalog.value = [];
  } else {
    catalog.value = await request(`/admin/${adminTab.value}`);
  }
  [roles.value, departments.value, permissions.value] = await Promise.all([
    request("/admin/roles"),
    request("/admin/departments"),
    request("/admin/permissions"),
  ]);
}
function form(title, fs, values, save, message = "") {
  formError.value = "";
  dialog.value = { title, fields: fs, values, save, message };
}
/** 提交当前表单并保留失败时的输入。知华科技 https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。 */
async function save(values) {
  if (busy.value) return;
  busy.value = true;
  formError.value = "";
  error.value = "";
  try {
    const spec = dialog.value;
    await spec.save(values);
    if (dialog.value === spec) dialog.value = null;
    notice.value = "操作已保存";
    if (profile.value) await refresh();
  } catch (e) {
    formError.value = e.message;
    if (e.status === 401) fail(e);
  } finally {
    busy.value = false;
  }
}
/** 确认业务动作并提交复核意见。知华科技 https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。 */
function confirmAction(label, path, row, extra = {}) {
  form(
    label,
    fields([["note", "处理说明", "textarea"]]),
    { note: "", ...extra },
    (v) =>
      request(path, { method: "POST", body: { version: row.version, ...v } }),
  );
}
/** 填写提案草稿与业务范围。知华科技 https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。 */
function proposalForm(row) {
  form(
    row ? "修改技术提案" : "新建技术提案",
    [
      ...fields([
        ["title", "提案名称"],
        ["rightType", "权利类型", "select", rightOptions],
      ]),
      {
        key: "summary",
        label: "技术方案与申请说明",
        type: "textarea",
        max: 6000,
        rows: 6,
      },
    ],
    row || { rightType: "PATENT" },
    (v) =>
      request("/proposals" + (row ? `/${row.id}` : ""), {
        method: row ? "PUT" : "POST",
        body: v,
      }),
  );
}
function sendProposal(row, action) {
  return run(async () => {
    await request(`/proposals/${row.id}/${action}`, {
      method: "POST",
      body: { version: row.version },
    });
    await refresh();
  });
}
function deleteDraft(path, row, label) {
  form(
    label,
    [],
    {},
    () => request(path + `?version=${row.version}`, { method: "DELETE" }),
    "确认删除此未提交草稿？操作审计会保留。",
  );
}
/** 将获批提案转为案件。知华科技 https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。 */
function createCase() {
  form(
    "已审提案立案",
    fields([
      [
        "proposalId",
        "已批准提案",
        "select",
        approved.value.map((p) => ({ value: p.id, label: p.title })),
      ],
      ["code", "案件编号"],
      ["jurisdiction", "国家或地区"],
      ["applicant", "申请人"],
      ["applicationNo", "申请号", "text", null, false],
      ["filedOn", "申请日期", "date", null, false],
    ]),
    { jurisdiction: "中国" },
    (v) => request("/matters", { method: "POST", body: v }),
  );
}
function editCase() {
  form(
    "案件基本信息",
    fields([
      ["title", "案件名称"],
      ["jurisdiction", "国家或地区"],
      ["applicant", "申请人"],
      ["applicationNo", "申请号", "text", null, false],
      ["filedOn", "申请日期", "date", null, false],
    ]),
    matter.value,
    (v) => request(`/matters/${matter.value.id}`, { method: "PUT", body: v }),
  );
}
/** 设置案件负责人和当前代理。知华科技 https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。 */
function assign() {
  const m = matter.value;
  form(
    "负责人及代理授权",
    fields([
      [
        "ownerId",
        "内部负责人",
        "select",
        opts(
          refs.value.people.filter(
            (p) => !p.external && p.departmentId === m.departmentId,
          ),
        ),
      ],
      [
        "agentId",
        "外部代理账号",
        "select",
        opts(refs.value.people.filter((p) => p.external)),
        false,
      ],
      ["note", "授权或撤换说明", "textarea"],
    ]),
    { ownerId: m.ownerId, agentId: m.agentId || "", note: "" },
    (v) =>
      request(`/matters/${m.id}/assign`, {
        method: "POST",
        body: { version: m.version, ...v },
      }),
    "撤换代理后，原账号将失去此案件访问权。原代理未办任务需通过期限新版本改派。",
  );
}
/** 登记人工期限或发起新版本复核。知华科技 https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。 */
function deadlineForm(old) {
  const m = matter.value;
  form(
    old ? "期限改期或改派" : "登记人工期限",
    fields([
      ["title", "事项名称"],
      ["kindId", "事项类型", "select", categoryOptions("DEADLINE")],
      ["dueOn", "登记截止日期", "date"],
      [
        "responsibleId",
        "办理人",
        "select",
        opts(
          refs.value.people.filter(
            (p) =>
              (!p.external && p.departmentId === m.departmentId) ||
              p.id === m.agentId,
          ),
        ),
      ],
      ["sourceFileId", "依据来文", "select", fileOpts.value],
      ["reason", "日期依据及变更说明", "textarea"],
    ]),
    old
      ? {
          title: old.title,
          kindId: old.kindId,
          dueOn: old.dueOn,
          responsibleId: old.responsibleId,
          sourceFileId: old.sourceFileId,
          reason: "",
        }
      : { dueOn: today() },
    (v) =>
      request(`/matters/${m.id}/deadlines`, {
        method: "POST",
        body: {
          ...v,
          previousId: old?.id || null,
          previousVersion: old?.version,
        },
      }),
    "按正式来文录入日期，由另一名内部人员确认。指派外部代理时，依据来文须为代理共享资料。",
  );
}
/** 提交完成证明并交独立人员验收。知华科技 https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。 */
function complete(row) {
  form(
    "提交办理回执",
    fields([
      ["completionFileId", "办理回执", "select", fileOpts.value],
      ["note", "办理说明", "textarea"],
    ]),
    {},
    (v) =>
      request(`/deadlines/${row.id}/complete`, {
        method: "POST",
        body: { version: row.version, ...v },
      }),
  );
}
/** 上传真实文件并明确代理共享范围。知华科技 https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。 */
function upload() {
  form(
    "上传案件资料",
    [
      {
        key: "file",
        label: "文件",
        type: "file",
        accept: ".pdf,.png,.jpg,.jpeg",
      },
      ...(!external.value && has("case.write")
        ? [
            {
              key: "shared",
              label: "向当前及后续获授权代理共享",
              type: "checkbox",
              required: false,
            },
          ]
        : []),
      ...fields([["note", "资料说明", "textarea", null, false]]),
    ],
    { shared: false },
    (v) => {
      const data = new FormData();
      data.append("file", v.file);
      data.append("shared", String(v.shared || false));
      data.append("note", v.note || "");
      return request(`/matters/${matter.value.id}/files`, {
        method: "POST",
        body: data,
      });
    },
    "支持静态 PDF、PNG、JPEG，每个不超过3MB。同名文件递增版本；已有版本和共享标记不覆盖。",
  );
}
/** 登记费用及其依据。知华科技 https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。 */
function feeForm(row) {
  form(
    row ? "修改费用草稿" : "登记费用申请",
    fields([
      ["categoryId", "费用类型", "select", categoryOptions("FEE")],
      ["reference", "账单编号"],
      ["payee", "收款方名称"],
      ["amount", "申请金额（元）"],
      ["sourceFileId", "账单或依据文件", "select", fileOpts.value],
      ["description", "费用说明", "textarea"],
    ]),
    row ? { ...row, amount: yuan(row.amountCents) } : {},
    (v) =>
      request(`/matters/${matter.value.id}/fees` + (row ? `/${row.id}` : ""), {
        method: row ? "PUT" : "POST",
        body: { ...v, amountCents: cents(v.amount) },
      }),
  );
}
/** 登记线下支付凭据或整笔冲销申请。知华科技 https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。 */
function paymentForm(fee, old) {
  form(
    old ? "登记完整冲销凭据" : "登记外部付款凭据",
    fields([
      ["reference", "凭据编号"],
      ...(!old ? [["amount", "核销金额（元）"]] : []),
      ["paidOn", "凭据日期", "date"],
      ["proofFileId", "凭据文件", "select", fileOpts.value],
      ["note", "登记说明", "textarea"],
    ]),
    { paidOn: today() },
    (v) =>
      request(`/fees/${fee.id}/payments`, {
        method: "POST",
        body: {
          ...v,
          amountCents: old ? undefined : cents(v.amount),
          reverseOfId: old?.id || null,
        },
      }),
    "仅登记已在线下完成的业务凭据，不在本系统发起付款。待复核的正向金额会预留核销余额。",
  );
}
const adminTitles = {
  accounts: "账号",
  roles: "角色与权限",
  departments: "部门与代理",
  dictionary: "业务分类",
  menus: "菜单",
  settings: "系统参数",
  audit: "操作审计",
  outbox: "通知投递",
};
const adminColumns = computed(
  () =>
    ({
      accounts: [
        { key: "username", label: "登录名" },
        { key: "displayName", label: "姓名" },
        {
          key: "roleId",
          label: "角色",
          format: (v) => roles.value.find((r) => r.id === v)?.name || v,
        },
        {
          key: "departmentId",
          label: "机构",
          format: (v) => departments.value.find((r) => r.id === v)?.name || v,
        },
        { key: "email", label: "提醒邮箱" },
        { key: "mailOptIn", label: "邮件接收" },
        { key: "enabled", label: "启用" },
      ],
      roles: [
        { key: "name", label: "角色" },
        { key: "scope", label: "范围", state: true },
        {
          key: "permissions",
          label: "权限",
          format: (v) =>
            v
              .split(",")
              .map(
                (c) => permissions.value.find((p) => p.code === c)?.label || c,
              )
              .join("、"),
        },
      ],
      departments: [
        { key: "code", label: "编号" },
        { key: "name", label: "机构名称" },
        { key: "kind", label: "机构类型", state: true },
        { key: "enabled", label: "启用" },
      ],
      dictionary: [
        {
          key: "groupCode",
          label: "分组",
          format: (v) => (v === "FEE" ? "费用" : "期限"),
        },
        { key: "code", label: "代码" },
        { key: "label", label: "名称" },
        { key: "enabled", label: "启用" },
      ],
      menus: [
        { key: "code", label: "标识" },
        { key: "label", label: "名称" },
        { key: "position", label: "顺序" },
        { key: "enabled", label: "启用" },
      ],
      settings: [
        { key: "label", label: "参数" },
        { key: "parameterValue", label: "当前值" },
      ],
      audit: [
        { key: "createdAt", label: "时间" },
        { key: "actorId", label: "操作人", format: person },
        { key: "action", label: "操作" },
        { key: "objectRef", label: "记录" },
        { key: "note", label: "说明" },
      ],
    })[adminTab.value] || [],
);
/** 维护机构账号、权限与基础配置。知华科技 https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。 */
function adminForm(row) {
  const type = adminTab.value;
  let fs = [],
    values = row ? { ...row } : { enabled: true };
  switch (type) {
    case "accounts":
      fs = fields([
        ["username", "登录名"],
        ["displayName", "姓名"],
        ["departmentId", "所属机构", "select", opts(departments.value)],
        ["roleId", "角色", "select", opts(roles.value)],
        [
          "password",
          row ? "新密码（留空不变）" : "初始密码",
          "password",
          null,
          !row,
        ],
        ["email", "提醒邮箱", "email", null, false],
        ["mailOptIn", "同意接收邮件提醒", "checkbox", null, false],
        ["enabled", "启用", "checkbox", null, false],
      ]);
      break;
    case "roles":
      fs = fields([
        ["name", "角色名称"],
        [
          "scope",
          "数据范围",
          "select",
          ["ALL", "DEPARTMENT", "ASSIGNED", "AGENT"].map((value) => ({
            value,
            label: states[value],
          })),
        ],
        [
          "permissions",
          "功能权限",
          "checks",
          permissions.value.map((p) => ({ value: p.code, label: p.label })),
        ],
      ]);
      values.permissions = row
        ? row.permissions.split(",").filter(Boolean)
        : [];
      break;
    case "departments":
      fs = fields([
        ["code", "机构代码"],
        ["name", "机构名称"],
        [
          "kind",
          "机构类型",
          "select",
          [
            { value: "INTERNAL", label: "企业内部部门" },
            { value: "AGENCY", label: "外部代理机构" },
          ],
        ],
        ["enabled", "启用", "checkbox", null, false],
      ]);
      break;
    case "dictionary":
      fs = fields([
        ["code", "代码"],
        [
          "groupCode",
          "分类分组",
          "select",
          [
            { value: "DEADLINE", label: "期限事项" },
            { value: "FEE", label: "费用事项" },
          ],
        ],
        ["label", "显示名称"],
        ["enabled", "启用", "checkbox", null, false],
      ]);
      break;
    case "menus":
      fs = fields([
        ["label", "名称"],
        ["position", "排序", "number"],
        ["enabled", "启用", "checkbox", null, false],
      ]);
      break;
    case "settings":
      fs = row.code.endsWith("enabled")
        ? fields([
            [
              "parameterValue",
              row.label,
              "select",
              [
                { value: "true", label: "启用" },
                { value: "false", label: "停用" },
              ],
            ],
          ])
        : fields([["parameterValue", row.label]]);
      break;
  }
  if (row)
    fs = fs.map((f) =>
      [
        "username",
        "departmentId",
        "scope",
        "code",
        "kind",
        "groupCode",
      ].includes(f.key)
        ? { ...f, disabled: true }
        : f,
    );
  form(
    (row ? "修改" : "新增") + adminTitles[type],
    fs,
    values,
    (v) =>
      request(`/admin/${type}` + (row ? `/${row.id}` : ""), {
        method: row ? "PUT" : "POST",
        body: v,
      }),
    type === "accounts"
      ? "内部账号与代理账号分属不同机构类型；启用邮件前确认接收地址和接收意愿。"
      : "",
  );
}
function passwordForm() {
  form(
    "修改本人密码",
    fields([
      ["oldPassword", "当前密码", "password"],
      ["newPassword", "新密码", "password"],
    ]),
    {},
    async (v) => {
      await request("/auth/password", { method: "POST", body: v });
      profile.value = null;
      clear();
    },
    "新密码须12至72字节，包含大小写字母和数字。修改后重新登录。",
  );
}
function about() {
  form(
    "关于 IPDesk",
    [],
    {},
    () => {},
    "知华科技（上海如静知华信息科技有限公司）\nIPDesk 1.0 · 源码公开、非商业使用。仅限个人学习交流；未经书面授权不得商用。\n官网 https://www.zhuatech.cn/\n商业授权或深度定制开发请联系知华科技，微信 zhuatech、zhuatech2。\n登记日期及费用由机构核对，不提供法律判断或自动付款。",
  );
}
const deadlinesCols = [
  { key: "title", label: "办理事项" },
  { key: "kindId", label: "分类", format: category },
  { key: "dueOn", label: "截止日期" },
  { key: "responsibleId", label: "办理人", format: person },
  { key: "previousId", label: "前版编号" },
  { key: "state", label: "状态", state: true },
];
const alertCols = [
  { key: "caseCode", label: "案件" },
  { key: "title", label: "事项" },
  { key: "dueOn", label: "截止日期" },
  { key: "channel", label: "通道", state: true },
  { key: "state", label: "状态", state: true },
  { key: "reason", label: "投递说明", format: (v) => reasons[v] || v },
  { key: "createdAt", label: "创建时间" },
];
onMounted(async () => {
  try {
    profile.value = await request("/auth/me");
    await refresh();
  } catch (e) {
    if (e.status !== 401) fail(e);
  } finally {
    boot.value = false;
  }
});
</script>
<template>
  <div v-if="boot" class="boot">正在连接 IPDesk…</div>
  <main v-else-if="!profile" class="login-page">
    <section class="login-story">
      <a
        href="https://www.zhuatech.cn/"
        class="login-brand"
        target="_blank"
        rel="noreferrer"
        ><img src="/brand/logo.jpg" alt="知华科技" /><b>知华科技</b></a
      >
      <div>
        <span class="eyebrow">IPDESK / INTELLECTUAL PROPERTY</span>
        <h1>企业知识产权<br />与代理协同</h1>
        <p>提案评审、来文期限、代理交付与费用核对。</p>
        <div class="story-line">
          <span>01 企业提案</span><span>02 案件协同</span
          ><span>03 期限跟进</span>
        </div>
      </div>
      <small>公开源码学习版 · 非商业使用</small>
    </section>
    <section class="login-panel">
      <div class="login-card">
        <span class="eyebrow">机构工作台</span>
        <h2>登录 IPDesk</h2>
        <p>使用机构分配的账号进入工作台</p>
        <form @submit.prevent="login">
          <label
            >登录名<input
              v-model="username"
              autocomplete="username"
              required
              maxlength="60" /></label
          ><label
            >密码<input
              v-model="password"
              type="password"
              autocomplete="current-password"
              required
              maxlength="128"
          /></label>
          <p v-if="error" class="error" role="alert">{{ error }}</p>
          <button :disabled="busy">
            {{ busy ? "正在登录…" : "登录工作台 →" }}
          </button>
        </form>
        <a href="https://www.zhuatech.cn/" target="_blank" rel="noreferrer"
          >知华科技 · 官网与商业咨询 ↗</a
        >
      </div>
    </section>
  </main>
  <div v-else class="app-shell">
    <aside class="sidebar">
      <a
        class="brand"
        href="https://www.zhuatech.cn/"
        target="_blank"
        rel="noreferrer"
        ><img src="/brand/logo.jpg" alt="知华科技" />
        <div><b>IPDesk</b><small>知华知识产权协同</small></div></a
      >
      <p class="sidebar-caption">
        {{ external ? "代理工作台" : "企业工作台" }}
      </p>
      <nav aria-label="主导航">
        <button
          v-for="m in profile.menus"
          :key="m.id"
          :disabled="busy"
          :class="{
            selected:
              route === m.code || (route === 'case' && m.code === 'matters'),
          }"
          @click="navigate(m.code)"
        >
          <span class="nav-dot"></span>{{ m.label }}
        </button>
      </nav>
      <div class="sidebar-bottom">
        <p class="edition">源码公开 · 非商业使用</p>
        <button class="text-button" @click="about">关于与授权</button
        ><a href="https://www.zhuatech.cn/" target="_blank" rel="noreferrer"
          >知华科技 ↗</a
        >
      </div>
    </aside>
    <div class="workspace">
      <header class="topbar">
        <div class="top-label">
          {{ appName }} <span class="separator">/</span>
          {{ route === "case" ? "案件工作台" : title }}
        </div>
        <div class="account">
          <span
            >{{ profile.account.displayName
            }}<small
              >{{ profile.role.name }} · {{ states[profile.role.scope] }}</small
            ></span
          ><button class="text-button" @click="passwordForm">改密码</button
          ><button class="text-button" :disabled="busy" @click="logout">
            退出
          </button>
        </div>
      </header>
      <main class="content">
        <p v-if="error" class="banner error" role="alert">{{ error }}</p>
        <p v-if="notice" class="banner success" role="status">{{ notice }}</p>
        <header class="page-heading">
          <div>
            <span class="eyebrow">{{
              route === "case" ? matter?.code : "IP OPERATIONS"
            }}</span>
            <h1>{{ title }}</h1>
            <p v-if="route === 'case'">
              {{ states[matter.rightType] }} · {{ matter.jurisdiction }} ·
              <span class="badge" :data-state="matter.state">{{
                states[matter.state]
              }}</span>
            </p>
            <p v-else-if="route === 'overview'">
              当前权限范围内的案件、期限与待办。
            </p>
          </div>
          <div class="heading-actions">
            <button
              v-if="route === 'case'"
              class="secondary"
              :disabled="busy"
              @click="navigate('matters')"
            >
              返回案件</button
            ><button
              v-if="route === 'proposals'"
              :disabled="busy"
              @click="proposalForm()"
            >
              ＋ 新建提案</button
            ><button
              v-if="route === 'matters' && has('case.write')"
              :disabled="busy || !approved.length"
              @click="createCase"
            >
              已审提案立案</button
            ><button
              v-if="route === 'reports' && has('reports') && !external"
              class="secondary"
              :disabled="busy"
              @click="
                run(() => download('/export', 'ipdesk-cases.csv'), '导出已生成')
              "
            >
              导出案件表</button
            ><button
              class="secondary"
              :disabled="busy"
              @click="run(refresh, '')"
            >
              刷新
            </button>
          </div>
        </header>
        <template v-if="['overview', 'reports'].includes(route)"
          ><div class="metrics">
            <article>
              <span>可见案件</span><strong>{{ report.matters || 0 }}</strong
              ><small>当前账号数据范围</small>
            </article>
            <article>
              <span>正在办理</span><strong>{{ report.active || 0 }}</strong
              ><small>企业与代理协同中</small>
            </article>
            <article>
              <span>已逾期事项</span
              ><strong class="amber">{{ report.overdue || 0 }}</strong
              ><small>按登记截止日期统计</small>
            </article>
            <article v-if="report.unpaid !== undefined">
              <span>待核销费用</span
              ><strong>¥ {{ money(report.unpaid) }}</strong
              ><small>已批准费用减已核实凭据</small>
            </article>
            <article v-else>
              <span>日期基准</span
              ><strong style="font-size: 22px">{{ report.today || "—" }}</strong
              ><small>机构业务日期</small>
            </article>
          </div>
          <section class="panel">
            <div class="section-heading">
              <h2>近期与逾期办理事项</h2>
              <span class="count">按日期排序 · 最早100项</span>
            </div>
            <DataGrid
              :rows="report.due"
              :columns="deadlinesCols"
              empty="当前提醒窗口内没有待办理事项"
              ><template #actions="{ row }"
                ><button
                  class="link-button"
                  :disabled="busy"
                  @click="openCase(row.matterId)"
                >
                  查看案件
                </button></template
              ></DataGrid
            >
          </section></template
        >
        <template v-else-if="route === 'proposals'"
          ><div class="toolbar disclosure-filter">
            <input
              v-model="proposalQuery"
              aria-label="搜索提案"
              placeholder="搜索提案名称或内容"
            /><span class="count">{{ proposalRows.length }} 项</span>
          </div>
          <div class="disclosure-list">
            <article
              v-for="p in proposalRows"
              :key="p.id"
              class="disclosure-card"
            >
              <div class="disclosure-meta">
                <span>#{{ p.id }}</span
                ><span>{{ states[p.rightType] }}</span
                ><span class="badge" :data-state="p.state">{{
                  states[p.state]
                }}</span
                ><span>{{ person(p.creatorId) }}</span>
              </div>
              <h2>{{ p.title }}</h2>
              <details>
                <summary>查看技术方案与评审说明</summary>
                <p>{{ p.summary }}</p>
                <p v-if="p.reviewNote">评审：{{ p.reviewNote }}</p>
              </details>
              <div class="action-buttons">
                <template
                  v-if="
                    p.creatorId === me &&
                    ['DRAFT', 'REJECTED'].includes(p.state)
                  "
                  ><button class="link-button" @click="proposalForm(p)">
                    修改</button
                  ><button
                    class="link-button"
                    :disabled="busy"
                    @click="sendProposal(p, 'submit')"
                  >
                    提交评审</button
                  ><button
                    v-if="p.state === 'DRAFT'"
                    class="link-button danger-text"
                    @click="
                      deleteDraft('/proposals/' + p.id, p, '删除提案草稿')
                    "
                  >
                    删除草稿
                  </button></template
                ><button
                  v-if="p.state === 'PENDING' && p.submitterId === me"
                  class="link-button"
                  :disabled="busy"
                  @click="sendProposal(p, 'withdraw')"
                >
                  撤回</button
                ><template
                  v-if="
                    p.state === 'PENDING' && canReview(p, 'proposal.review')
                  "
                  ><button
                    class="link-button"
                    @click="
                      confirmAction(
                        '批准技术提案',
                        '/proposals/' + p.id + '/approve',
                        p,
                      )
                    "
                  >
                    批准</button
                  ><button
                    class="link-button danger-text"
                    @click="
                      confirmAction(
                        '驳回技术提案',
                        '/proposals/' + p.id + '/reject',
                        p,
                      )
                    "
                  >
                    驳回
                  </button></template
                >
              </div>
            </article>
            <p v-if="!proposalRows.length" class="empty">
              暂无提案，可登记技术方案并提交内部评审。
            </p>
          </div></template
        >
        <template v-else-if="route === 'matters'"
          ><form
            class="toolbar"
            @submit.prevent="
              page = 1;
              run(refresh, '');
            "
          >
            <input
              v-model="query"
              placeholder="名称、案件编号或申请号"
              aria-label="搜索案件"
            /><select v-model="filter" aria-label="案件状态">
              <option value="">全部状态</option>
              <option
                v-for="s in ['ACTIVE', 'CLOSING', 'CLOSED', 'REOPENING']"
                :key="s"
                :value="s"
              >
                {{ states[s] }}
              </option></select
            ><select v-model="sort" aria-label="排序">
              <option value="id">最近立案</option>
              <option value="code">案件编号</option></select
            ><button :disabled="busy">查询</button>
          </form>
          <section class="panel">
            <DataGrid
              :rows="list.items"
              :columns="[
                { key: 'code', label: '案件编号' },
                { key: 'title', label: '案件名称' },
                { key: 'rightType', label: '类型', state: true },
                { key: 'applicationNo', label: '申请号' },
                { key: 'applicant', label: '申请人' },
                { key: 'state', label: '状态', state: true },
              ]"
              ><template #actions="{ row }"
                ><button
                  class="link-button"
                  :disabled="busy"
                  @click="openCase(row.id)"
                >
                  案件工作台 →
                </button></template
              ></DataGrid
            >
          </section>
          <div class="pagination">
            <span>共 {{ list.total }} 件</span
            ><button
              class="secondary"
              :disabled="busy || page <= 1"
              @click="
                page--;
                run(refresh, '');
              "
            >
              上一页</button
            ><span>{{ page }}</span
            ><button
              class="secondary"
              :disabled="busy || page * 20 >= list.total"
              @click="
                page++;
                run(refresh, '');
              "
            >
              下一页
            </button>
          </div></template
        >
        <template v-else-if="route === 'case' && matter"
          ><section class="panel case-summary">
            <dl class="detail-grid">
              <div>
                <dt>申请人</dt>
                <dd>{{ matter.applicant }}</dd>
              </div>
              <div>
                <dt>申请号</dt>
                <dd>{{ matter.applicationNo || "尚未登记" }}</dd>
              </div>
              <div>
                <dt>申请日期</dt>
                <dd>{{ matter.filedOn || "尚未登记" }}</dd>
              </div>
              <div>
                <dt>内部负责人</dt>
                <dd>{{ person(matter.ownerId) }}</dd>
              </div>
              <div>
                <dt>代理账号</dt>
                <dd>
                  {{ matter.agentId ? person(matter.agentId) : "未授权" }}
                </dd>
              </div>
              <div>
                <dt>案件编号</dt>
                <dd>{{ matter.code }}</dd>
              </div>
            </dl>
            <div v-if="has('case.write') && active" class="action-buttons">
              <button class="secondary" @click="editCase">修改基本信息</button
              ><button class="secondary" @click="assign">
                负责人及代理授权
              </button>
            </div>
          </section>
          <div class="tabs">
            <button
              v-for="t in [
                ['deadlines', '期限与办理'],
                ['files', '来文与资料'],
                ...(!external && detail.fees ? [['fees', '费用与凭据']] : []),
                ...(!external ? [['history', '结案与历史']] : []),
              ]"
              :key="t[0]"
              :class="{ selected: caseTab === t[0] }"
              @click="caseTab = t[0]"
            >
              {{ t[1] }}
            </button>
          </div>
          <template v-if="caseTab === 'deadlines'"
            ><div class="section-heading">
              <h2>期限与办理</h2>
              <button
                v-if="has('deadline.write') && active"
                @click="deadlineForm()"
              >
                登记人工期限
              </button>
            </div>
            <section v-for="d in detail.deadlines" :key="d.id" class="panel">
              <div class="section-heading">
                <div>
                  <span class="eyebrow"
                    >#{{ d.id }} · {{ category(d.kindId)
                    }}<template v-if="d.previousId">
                      · 前版 #{{ d.previousId }}</template
                    ></span
                  >
                  <h2>
                    {{ d.title }}
                    <span class="badge" :data-state="d.state">{{
                      states[d.state]
                    }}</span>
                  </h2>
                  <p>{{ d.dueOn }} 截止 · {{ person(d.responsibleId) }}</p>
                </div>
                <div v-if="active" class="action-buttons">
                  <template
                    v-if="
                      d.state === 'PENDING' && canReview(d, 'deadline.review')
                    "
                    ><button
                      @click="
                        confirmAction(
                          '确认期限版本',
                          '/deadlines/' + d.id + '/approve',
                          d,
                        )
                      "
                    >
                      确认</button
                    ><button
                      class="secondary"
                      @click="
                        confirmAction(
                          '退回期限版本',
                          '/deadlines/' + d.id + '/reject',
                          d,
                        )
                      "
                    >
                      退回
                    </button></template
                  ><template v-if="d.state === 'OPEN'"
                    ><button v-if="has('delivery')" @click="complete(d)">
                      提交办理回执</button
                    ><button
                      v-if="has('deadline.write')"
                      class="secondary"
                      @click="deadlineForm(d)"
                    >
                      改期／改派</button
                    ><button
                      v-if="has('deadline.write')"
                      class="secondary"
                      @click="
                        confirmAction(
                          '申请取消期限',
                          '/deadlines/' + d.id + '/cancel',
                          d,
                        )
                      "
                    >
                      申请取消
                    </button></template
                  ><template
                    v-if="
                      d.state === 'DONE_PENDING' &&
                      canReview(d, 'deadline.review', 'completionById')
                    "
                    ><button
                      @click="
                        confirmAction(
                          '确认办结',
                          '/deadlines/' + d.id + '/accept',
                          d,
                        )
                      "
                    >
                      确认办结</button
                    ><button
                      class="secondary"
                      @click="
                        confirmAction(
                          '退回继续办理',
                          '/deadlines/' + d.id + '/return',
                          d,
                        )
                      "
                    >
                      退回
                    </button></template
                  ><template
                    v-if="
                      d.state === 'CANCEL_PENDING' &&
                      canReview(d, 'deadline.review', 'completionById')
                    "
                    ><button
                      @click="
                        confirmAction(
                          '批准取消',
                          '/deadlines/' + d.id + '/cancel-approve',
                          d,
                        )
                      "
                    >
                      批准取消</button
                    ><button
                      class="secondary"
                      @click="
                        confirmAction(
                          '保留办理事项',
                          '/deadlines/' + d.id + '/cancel-reject',
                          d,
                        )
                      "
                    >
                      保留事项
                    </button></template
                  >
                </div>
              </div>
              <div class="case-summary">
                <p class="prose">{{ d.reason }}</p>
                <p v-if="d.completionNote">
                  办理／取消说明：{{ d.completionNote }}
                </p>
                <p v-if="d.reviewNote">复核：{{ d.reviewNote }}</p>
                <div class="action-buttons">
                  <button
                    class="link-button"
                    @click="
                      run(
                        () =>
                          download(
                            '/files/' + d.sourceFileId,
                            fileName(d.sourceFileId),
                          ),
                        '',
                      )
                    "
                  >
                    下载依据来文</button
                  ><button
                    v-if="d.completionFileId"
                    class="link-button"
                    @click="
                      run(
                        () =>
                          download(
                            '/files/' + d.completionFileId,
                            fileName(d.completionFileId),
                          ),
                        '',
                      )
                    "
                  >
                    下载办理回执
                  </button>
                </div>
              </div>
            </section>
            <p v-if="!detail.deadlines.length" class="empty">
              暂无可见期限。内部人员可先上传来文，再登记办理事项。
            </p></template
          >
          <template v-else-if="caseTab === 'files'"
            ><div class="section-heading">
              <h2>来文与资料版本</h2>
              <button
                v-if="
                  active &&
                  (has('delivery') || has('case.write') || has('finance'))
                "
                @click="upload"
              >
                上传资料
              </button>
            </div>
            <section class="panel">
              <DataGrid
                :rows="detail.files"
                :columns="[
                  { key: 'fileName', label: '文件' },
                  { key: 'versionNumber', label: '版本' },
                  { key: 'shared', label: '代理共享' },
                  {
                    key: 'sizeBytes',
                    label: '大小',
                    format: (v) => (v / 1024).toFixed(1) + ' KB',
                  },
                  { key: 'note', label: '说明' },
                  { key: 'createdAt', label: '上传时间' },
                ]"
                ><template #actions="{ row }"
                  ><button
                    class="link-button"
                    @click="
                      run(() => download('/files/' + row.id, row.fileName), '')
                    "
                  >
                    下载
                  </button></template
                ></DataGrid
              >
            </section></template
          >
          <template v-else-if="caseTab === 'fees'"
            ><div class="section-heading">
              <h2>费用申请与核销</h2>
              <button v-if="active && has('finance')" @click="feeForm()">
                登记费用申请
              </button>
            </div>
            <section v-for="f in detail.fees" :key="f.id" class="panel">
              <div class="section-heading">
                <div>
                  <span class="eyebrow"
                    >{{ f.reference }} · {{ category(f.categoryId) }}</span
                  >
                  <h2>
                    {{ f.payee }}
                    <span class="badge" :data-state="f.state">{{
                      states[f.state]
                    }}</span>
                  </h2>
                  <p>
                    申请 ¥ {{ money(f.amountCents) }} · 已核实 ¥
                    {{ money(feePaid(f.id).approved) }} · 待审占用 ¥
                    {{ money(feePaid(f.id).reserved) }}
                  </p>
                </div>
                <div v-if="active" class="action-buttons">
                  <template
                    v-if="
                      f.creatorId === me &&
                      has('finance') &&
                      ['DRAFT', 'REJECTED'].includes(f.state)
                    "
                    ><button class="secondary" @click="feeForm(f)">修改</button
                    ><button
                      @click="
                        confirmAction(
                          '提交费用申请',
                          '/fees/' + f.id + '/submit',
                          f,
                        )
                      "
                    >
                      提交</button
                    ><button
                      v-if="f.state === 'DRAFT'"
                      class="secondary"
                      @click="deleteDraft('/fees/' + f.id, f, '删除费用草稿')"
                    >
                      删除草稿
                    </button></template
                  ><template
                    v-if="
                      f.state === 'PENDING' && canReview(f, 'finance.review')
                    "
                    ><button
                      @click="
                        confirmAction(
                          '批准费用申请',
                          '/fees/' + f.id + '/approve',
                          f,
                        )
                      "
                    >
                      批准</button
                    ><button
                      class="secondary"
                      @click="
                        confirmAction(
                          '驳回费用申请',
                          '/fees/' + f.id + '/reject',
                          f,
                        )
                      "
                    >
                      驳回
                    </button></template
                  ><button
                    v-if="f.state === 'APPROVED' && has('finance')"
                    @click="paymentForm(f)"
                  >
                    登记付款凭据
                  </button>
                </div>
              </div>
              <div class="case-summary">
                <p>{{ f.description }}</p>
                <p v-if="f.reviewNote">复核：{{ f.reviewNote }}</p>
                <button
                  class="link-button"
                  @click="
                    run(
                      () =>
                        download(
                          '/files/' + f.sourceFileId,
                          fileName(f.sourceFileId),
                        ),
                      '',
                    )
                  "
                >
                  下载费用依据
                </button>
              </div>
              <DataGrid
                :rows="detail.payments.filter((p) => p.feeId === f.id)"
                :columns="[
                  { key: 'reference', label: '凭据编号' },
                  { key: 'amountCents', label: '核销金额', money: true },
                  { key: 'paidOn', label: '凭据日期' },
                  { key: 'reverseOfId', label: '冲销原编号' },
                  { key: 'state', label: '状态', state: true },
                  { key: 'reviewNote', label: '复核说明' },
                ]"
                ><template #actions="{ row }"
                  ><button
                    class="link-button"
                    @click="
                      run(
                        () =>
                          download(
                            '/files/' + row.proofFileId,
                            fileName(row.proofFileId),
                          ),
                        '',
                      )
                    "
                  >
                    凭据</button
                  ><template
                    v-if="
                      active &&
                      row.state === 'PENDING' &&
                      canReview(row, 'finance.review')
                    "
                    ><button
                      class="link-button"
                      @click="
                        confirmAction(
                          '核实付款凭据',
                          '/payments/' + row.id + '/approve',
                          row,
                        )
                      "
                    >
                      核实</button
                    ><button
                      class="link-button danger-text"
                      @click="
                        confirmAction(
                          '驳回付款凭据',
                          '/payments/' + row.id + '/reject',
                          row,
                        )
                      "
                    >
                      驳回
                    </button></template
                  ><button
                    v-if="
                      active &&
                      has('finance') &&
                      row.state === 'APPROVED' &&
                      !row.reverseOfId &&
                      !detail.payments.some(
                        (p) =>
                          p.reverseOfId === row.id &&
                          ['PENDING', 'APPROVED'].includes(p.state),
                      )
                    "
                    class="link-button"
                    @click="paymentForm(f, row)"
                  >
                    登记完整冲销
                  </button></template
                ></DataGrid
              >
            </section>
            <p v-if="!detail.fees.length" class="empty">
              暂无费用申请。
            </p></template
          >
          <template v-else-if="caseTab === 'history'"
            ><section class="panel case-summary">
              <h2>结案检查</h2>
              <ul v-if="detail.blockers.length" class="readiness">
                <li v-for="(b, i) in detail.blockers" :key="i">{{ b }}</li>
              </ul>
              <p v-else class="success-text">暂无未决期限、费用和付款事项。</p>
              <p v-if="matter.reviewNote">审签说明：{{ matter.reviewNote }}</p>
              <div class="action-buttons">
                <button
                  v-if="active && has('case.write')"
                  :disabled="detail.blockers.length > 0"
                  @click="
                    confirmAction(
                      '申请结案',
                      '/matters/' + matter.id + '/state/close',
                      matter,
                    )
                  "
                >
                  申请结案</button
                ><button
                  v-if="matter.state === 'CLOSED' && has('case.write')"
                  @click="
                    confirmAction(
                      '申请重新打开案件',
                      '/matters/' + matter.id + '/state/reopen',
                      matter,
                    )
                  "
                >
                  申请重开</button
                ><template
                  v-if="
                    ['CLOSING', 'REOPENING'].includes(matter.state) &&
                    canReview(matter, 'case.review', 'closeById')
                  "
                  ><button
                    @click="
                      confirmAction(
                        '批准案件状态变更',
                        '/matters/' + matter.id + '/state/approve',
                        matter,
                      )
                    "
                  >
                    批准</button
                  ><button
                    class="secondary"
                    @click="
                      confirmAction(
                        '驳回案件状态变更',
                        '/matters/' + matter.id + '/state/reject',
                        matter,
                      )
                    "
                  >
                    驳回
                  </button></template
                >
              </div>
            </section>
            <section class="panel">
              <div class="section-heading"><h2>案件操作历史</h2></div>
              <DataGrid
                :rows="detail.audit"
                :columns="[
                  { key: 'createdAt', label: '时间' },
                  { key: 'actorId', label: '操作人', format: person },
                  { key: 'action', label: '动作' },
                  { key: 'objectRef', label: '记录' },
                  { key: 'note', label: '说明' },
                ]"
              /></section></template
        ></template>
        <template v-else-if="route === 'notifications'"
          ><section class="panel">
            <div class="section-heading">
              <h2>我的办理提醒</h2>
              <span class="count">仅显示仍有权限的未办结事项</span>
            </div>
            <DataGrid :rows="alerts.items" :columns="alertCols"
              ><template #actions="{ row }"
                ><button class="link-button" @click="openCase(row.matterId)">
                  查看案件</button
                ><button
                  v-if="row.channel === 'INAPP' && row.state === 'NEW'"
                  class="link-button"
                  @click="
                    run(async () => {
                      await request('/notifications/' + row.id + '/read', {
                        method: 'POST',
                        body: { version: row.version },
                      });
                      await refresh();
                    })
                  "
                >
                  标记已读
                </button></template
              ></DataGrid
            >
          </section></template
        >
        <template v-else-if="route === 'admin'"
          ><div class="tabs">
            <button
              v-for="(label, key) in adminTitles"
              :key="key"
              :disabled="busy"
              :class="{ selected: adminTab === key }"
              @click="changeAdmin(key)"
            >
              {{ label }}
            </button>
          </div>
          <section class="panel">
            <div class="section-heading">
              <h2>{{ adminTitles[adminTab] }}</h2>
              <button
                v-if="
                  ['accounts', 'roles', 'departments', 'dictionary'].includes(
                    adminTab,
                  )
                "
                @click="adminForm()"
              >
                新增
              </button>
              <div v-if="adminTab === 'outbox'" class="action-buttons">
                <span class="count">{{
                  alerts.smtpConfigured
                    ? "SMTP环境已配置"
                    : "SMTP环境未启用或未完整配置"
                }}</span
                ><button
                  :disabled="busy"
                  @click="
                    run(async () => {
                      await request('/notifications/run', { method: 'POST' });
                      await loadAdmin();
                    })
                  "
                >
                  立即检查与投递
                </button>
              </div>
            </div>
            <DataGrid
              v-if="adminTab !== 'outbox'"
              :rows="catalog"
              :columns="adminColumns"
              ><template v-if="adminTab !== 'audit'" #actions="{ row }"
                ><template
                  v-if="
                    !(
                      ['accounts', 'roles', 'departments'].includes(adminTab) &&
                      row.id === 1
                    )
                  "
                  ><button class="link-button" @click="adminForm(row)">
                    修改</button
                  ><button
                    v-if="
                      ['roles', 'departments', 'dictionary'].includes(adminTab)
                    "
                    class="link-button danger-text"
                    @click="
                      deleteDraft(
                        '/admin/' + adminTab + '/' + row.id,
                        row,
                        '删除未引用目录',
                      )
                    "
                  >
                    删除
                  </button></template
                ><span v-else class="muted">初始项受保护</span></template
              ></DataGrid
            ><DataGrid
              v-else
              :rows="alerts.items"
              :columns="[
                ...alertCols,
                { key: 'email', label: '邮件收件人' },
                { key: 'attempts', label: '尝试次数' },
              ]"
              ><template #actions="{ row }"
                ><button
                  v-if="
                    row.channel === 'EMAIL' &&
                    ['UNKNOWN', 'SKIPPED'].includes(row.state) &&
                    row.attempts < 3
                  "
                  class="link-button"
                  @click="
                    form(
                      '确认未送达后重试',
                      [
                        {
                          key: 'confirmedNotDelivered',
                          label: '已核对收件箱和SMTP记录，确认未送达',
                          type: 'checkbox',
                          required: false,
                        },
                      ],
                      { confirmedNotDelivered: false },
                      (v) =>
                        request('/notifications/' + row.id + '/retry', {
                          method: 'POST',
                          body: { version: row.version, ...v },
                        }),
                      '投递结果不明可能已经送达，核对后再重试以避免重复邮件。',
                    )
                  "
                >
                  核对后重试
                </button></template
              ></DataGrid
            >
          </section></template
        >
        <footer class="page-footer">
          <span>IPDesk · 知华科技 · 非商业源码学习版</span
          ><a href="https://www.zhuatech.cn/" target="_blank" rel="noreferrer"
            >官网与商业咨询 ↗</a
          >
        </footer>
      </main>
    </div>
  </div>
  <FormDialog
    v-if="dialog"
    :key="dialog.title"
    :spec="dialog"
    :busy="busy"
    :error="formError"
    @save="save"
    @cancel="dialog = null"
  />
</template>
