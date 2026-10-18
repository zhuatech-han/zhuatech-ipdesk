[中文](README.md) | [English](README.en.md)

# 知华 IPDesk · 企业知识产权与代理协同系统

**知华科技（上海如静知华信息科技有限公司）** · [官网](https://www.zhuatech.cn/)

基于 Java 21、Spring Boot、Vue 3 与 MySQL 的知识产权管理工作台，覆盖专利、商标、著作权提案，以及企业与外部代理之间的案件交付、人工期限复核、费用凭据和通知。

**公开源码学习版／非商业源码版**：仅限个人学习交流；未经上海如静知华信息科技有限公司授权不得商用。详见 [LICENSE](LICENSE)，本项目不是 MIT 或 Apache 授权的自有源码。

## 适用场景

- 企业研发人员提交知识产权提案，由知识产权岗位独立评审后建立案件。
- 企业知识产权部门协调代理、收集来文和交付证明，人工确认期限并复核变更。
- 财务人员登记代理费、官费及线下支付凭据，由另一人复核和核对余额。
- 机构管理员管理内部部门、外部代理账号、权限、字典与提醒配置。

这是单机构部署、部门与案件范围控制的实现；不是完整的多租户 SaaS，也不代替专业代理或法律意见。

## 已实现功能

| 工作台 | 当前能力 |
| --- | --- |
| 提案人端 | 专利／商标／著作权草稿、提交、撤回、退回修改和评审结果 |
| 企业案件端 | 获批提案建案、负责人及代理分派、列表检索分页、独立结案和重开复核 |
| 期限协同 | 必须关联来源文件；人工登记、独立复核、版本替换、完成证明、退回与验收、取消复核 |
| 外部代理端 | 仅看当前分派案件、已共享文件和本人任务；上传交付；撤换代理即收回访问 |
| 费用端 | 费用依据与独立审批；分次支付凭据、未审金额占用、整笔冲销、余额核对；金额按整数分存储 |
| 通知端 | 站内提醒、已读；可配置 SMTP 邮件、账号邮箱和订阅、去重、发送状态、人工核对后重试 |
| 管理端 | 账号、角色、部门、期限／费用字典、菜单、系统参数、审计与通知投递记录 |
| 报表与文件 | 当前权限范围的概览与 CSV；真实 PDF/PNG/JPEG 校验、版本、SHA-256 校验、权限下载 |

审批人不能复核自己提交的动作，管理员也不例外。数据库初始化不放入业务案例或通用演示密码。完整流程见 [操作手册](docs/操作手册.md)。

## 当前运行截图

以下均来自当前版本在隔离数据库中的实际运行；案件与账号为虚构测试资料。

### 登录与机构入口

![登录页：机构账号入口与非商业授权](docs/screenshots/login.jpg)

### 企业工作概览

![工作概览：当前范围案件与临近期限](docs/screenshots/overview.jpg)

### 提案人工作台

![提案列表：三类知识产权提案和评审状态](docs/screenshots/proposals.jpg)

### 案件与期限

![案件工作台：来源文件、复核版本与交付状态](docs/screenshots/deadlines.jpg)

### 费用核对

![费用页面：审批、分次凭据和整数分余额](docs/screenshots/fees.jpg)

### 代理交付端

![代理页面：当前分派案件与共享交付](docs/screenshots/agent.jpg)

### 管理与通知

![账号管理：部门、角色与启停控制](docs/screenshots/accounts.jpg)

![通知记录：站内及本机 SMTP 捕获验收状态](docs/screenshots/notifications.jpg)

### 手机布局

![手机视图：窄屏案件工作台](docs/screenshots/mobile.jpg)

## 技术架构

浏览器 Vue 3 → Nginx 同源代理 → Spring Boot 4 / Spring Security 会话与 CSRF → JPA / Flyway → MySQL 8.4。文件原文与元数据同库保存，便于一致备份；SMTP 通过提交前检查与持久化投递状态处理。业务写操作使用数据库互斥行串行化，适合小规模单机构实例。

[架构与安全边界](docs/架构与安全.md) · [API](docs/API.md) · [部署与恢复](docs/部署与恢复.md) · [验证记录](docs/验证记录.md)

```text
backend/                 Java 21 服务、权限和业务流程
  src/main/resources/db/migration/  Flyway 版本化迁移
  src/test/              规则、真实迁移和 HTTP 安全测试
frontend/                Vue 页面、组件和金额精度测试
  public/brand/          正式 LOGO
scripts/                 初始化、验收、发布检查、备份和空库恢复
compose.yaml             MySQL、后端与前端
compose.storage.yaml     显式本地数据库目录选项
.github/workflows/       云端质量检查
docs/                   图片、操作、架构、API 与部署说明
```

## 环境要求

推荐 Docker Desktop / Docker Engine 与 Compose v2，至少 4 GB 可用内存、10 GB 空间。首次构建需访问 Maven Central、npm 和镜像源。Compose 使用 MySQL 8.4。

本地开发：JDK 21、Maven 3.9.16 或兼容 3.9.x、Node.js 24.19.0、npm 11、Python 3.10+。前端运行时无 Node 服务；Python 仅用于辅助脚本。后端测试采用 H2 MySQL 模式及真实 Flyway 迁移，发布验收另用实际 MySQL。

## 安装启动与数据库初始化

```bash
git clone https://github.com/zhuatech-han/zhuatech-ipdesk.git
cd zhuatech-ipdesk
python3 scripts/init-env.py
docker compose -p ipdesk config --quiet
docker compose -p ipdesk up --build -d --wait --wait-timeout 900
```

打开 <http://127.0.0.1:8176/>。管理员默认登录名 `admin`；密码读取本机新生成的 `.env` 中 `ADMIN_PASSWORD`，不要公开或提交该文件。脚本生成独立随机密码并将文件权限设为 600，不覆盖已有配置。首次启动 Flyway 建表并初始化管理员、七种角色、基础菜单、字典和参数；不创建业务数据。已有数据库重启不重置密码。修改 `.env` 的初始管理员密码不能修改已有账号密码。

数据库仅在容器内部网络开放。不要对已有数据库删除卷来“修复”启动问题。恢复和迁移前先备份；详见部署说明。

本地分开开发：先启动 `docker compose -p ipdesk up -d --wait mysql`，在终端安全设置 `DATABASE_URL`（指向可达测试库）、`DATABASE_USER`、`DATABASE_PASSWORD`、`ADMIN_PASSWORD` 后运行 `mvn -f backend/pom.xml spring-boot:run`；前端 `cd frontend && npm ci && npm run dev`（Vite 代理后端 8080）。默认 Compose 不映射数据库端口，本机开发可添加仅限回环的临时端口映射，不应暴露生产数据库。

## 配置说明

以 [.env.example](.env.example) 为准。容器环境文件不会自动被本机 Maven 读取。

| 配置 | 说明 |
| --- | --- |
| `DATABASE_PASSWORD` / `MYSQL_ROOT_PASSWORD` | 初始化脚本生成；数据库卷已建立后不能仅改环境变量轮换 |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | 仅首次空库管理员创建使用 |
| `WEB_PORT` / `BIND_ADDRESS` | 默认 8176 / 127.0.0.1 |
| `DATABASE_URL` / `DATABASE_USER` | 可替换为受控外部 MySQL；默认 TLS REQUIRED，生产配置 CA 与 VERIFY_IDENTITY |
| `COOKIE_SECURE` | HTTPS 部署设置 true |
| `SCHEDULER_ENABLED` | 默认 true，每 60 秒扫描；测试可关闭并手动触发 |
| `MAIL_ENABLED` | 默认 false，是否允许服务实际投递 SMTP |
| `MAIL_HOST` / `MAIL_PORT` | SMTP 主机与端口，端口默认 587 |
| `MAIL_USERNAME` / `MAIL_PASSWORD` / `MAIL_FROM` | 环境配置；管理接口不返回 SMTP 密钥 |
| `MAIL_TLS` | 默认 true，要求 STARTTLS；不支持隐式 SMTPS 465 配置 |

邮件需同时满足：服务器 `MAIL_ENABLED=true`、有效 SMTP、管理端开启 `email_enabled`、账号有有效邮箱且主动勾选邮件订阅；提醒总开关 `reminders_enabled` 也需开启。没有配置时不会宣称发送成功；SMTP 接受不代表收件箱到达。发送结果不确定时需要人工核对后确认重试，避免盲目重复发送。`warning_days` 默认30，范围1–180；`max_case_mb` 默认40，范围10–100。

## 测试与构建

```bash
mvn -B -f backend/pom.xml spotless:check clean verify
cd frontend
npm ci --no-audit --no-fund
npm run format:check
npm run lint
npm test
npm run build
cd ..
python3 -m pip install -r scripts/requirements-quality.txt
python3 -m black --check scripts
python3 -m py_compile scripts/*.py
python3 -m unittest discover -s scripts -p 'test_*.py'
python3 scripts/release-check.py
git diff --check
```

实际 MySQL 全流程验收必须使用**新建独立测试实例**（包含测试写入，不可对业务库运行）：

```bash
SCHEDULER_ENABLED=false docker compose -p ipdesk-test up --build -d --wait --wait-timeout 900
python3 scripts/smoke.py --env-file .env --private-state /tmp/ipdesk-test-state.json
docker compose -p ipdesk-test down -v --remove-orphans
```

`--private-state` 必须为仓库外新文件，保存虚构测试账号凭证，权限600。该测试要求初始无业务数据，默认验证未配置 SMTP 的跳过状态；加 `--mailpit-url` 可验证独立 Mailpit 捕获服务中的实际 SMTP 投递，详见验证记录。Docker 后端构建执行测试，不跳过。

## 部署与已知限制

生产接入前必须自行完成授权、HTTPS、密码轮换流程、备份恢复演练、访问策略、容量与安全评估。可配置 HTTPS 反向代理连接回环 8176，设置 `COOKIE_SECURE=true`，不向公网开放 MySQL 和后端容器。详情见 [部署与恢复](docs/部署与恢复.md)。

- 无专利局／商标局／版权登记 API、官方状态同步、自动法律期限推算、专利检索或 AI 法律判断。
- 无真实付款、电子发票、银行对账接口；只登记线下凭据。仅人民币，两位小数；冲销为原整笔反向记录。
- 提醒为站内与可配置 SMTP；无短信、微信、推送、送达／已读回执服务。站内已读为人工操作。
- 单机构实例、写操作串行、列表上限10,000条；概览期限最多100条；需按实际规模扩展分页与性能方案。
- 文件仅 PDF/PNG/JPEG、每份3 MiB、每案100版本及配置容量；不支持 Office、压缩包、恶意软件查杀或全文索引。共享版本不能撤销公开标记，误共享应联系管理员并按组织流程处理；撤换代理可立即收回原代理所有访问。
- 以人工输入为准，期限来源与复核不能代替专业人员核对；不能承诺覆盖全部法域、全部知识产权程序或生产可用性。

## 授权与第三方组件

自有源码仅限个人学习交流；未经上海如静知华信息科技有限公司授权不得商用。商业部署、再分发及其他使用以 [LICENSE](LICENSE) 正文和有效授权为准。第三方组件保留各自许可，见 [NOTICE](NOTICE)。

## 联系知华科技

[官网 https://www.zhuatech.cn/](https://www.zhuatech.cn/)

**商业授权或深度定制开发请联系知华科技**。服务范围：商业授权、定制开发、部署与系统集成。微信：`zhuatech`、`zhuatech2`。

| 微信 zhuatech | 微信 zhuatech2 |
| --- | --- |
| ![微信 zhuatech](docs/images/wechat-zhuatech.png) | ![微信 zhuatech2](docs/images/wechat-zhuatech2.png) |
