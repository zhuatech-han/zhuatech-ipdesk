# IPDesk API

所有业务接口在`/api`下，除CSRF获取与登录外需要会话。使用Cookie `IPDESKSESSION`；写请求带从`GET /api/auth/csrf`获得的令牌，头名为返回`header`（默认`X-CSRF-TOKEN`）。登录后重新取令牌。JSON错误包含可读消息，不回传SQL与密码。400无效状态／参数，401未登录，403权限／CSRF，404无可见对象，409并发版本，413容量上限。

| 方法与路径 | 用途 |
| --- | --- |
| GET auth/csrf；POST auth/login | CSRF、登录（username/password） |
| GET auth/me；POST auth/logout；POST auth/password | 当前会话、退出、修改本人密码 |
| GET references | 当前可分派账号、字典等受控引用 |
| GET/POST proposals；PUT/DELETE proposals/{id} | 提案列表、草稿与修改／删除 |
| POST proposals/{id}/{action} | submit、withdraw、approve、reject |
| GET/POST matters；GET/PUT matters/{id} | 案件检索、获批提案建案、明细与基本资料 |
| POST matters/{id}/assign | 负责人和当前代理 |
| POST matters/{id}/state/{action} | close、reopen、approve、reject |
| POST matters/{id}/deadlines | 期限初版或previousId新版本 |
| POST deadlines/{id}/{action} | approve、reject、complete、accept、return、cancel、cancel-approve、cancel-reject |
| POST matters/{id}/files | multipart字段file，shared布尔字段 |
| GET files/{id} | 原始附件下载，按当前权限验证 |
| POST matters/{id}/fees；PUT matters/{id}/fees/{feeId} | 费用草稿 |
| DELETE fees/{id}；POST fees/{id}/{action} | 删除草稿；submit、approve、reject |
| POST fees/{id}/payments | 线下付款或reverseOfId整笔冲销凭据 |
| POST payments/{id}/{action} | approve／reject |
| GET reports；GET export | 受控概览和CSV |
| GET/POST admin/{resource}；PUT/DELETE admin/{resource}/{id} | accounts、roles、departments、entries、menus、settings；审计仅只读 |
| GET notifications；GET notifications?admin=true | 本人提醒／管理员投递记录 |
| POST notifications/{id}/{action}；POST notifications/run | read、retry；管理员立即检查 |

需要版本的更新与复核在JSON中带`version`。复核动作带`note`；期限完成带`proofFileId`，新期限带`sourceFileId`。费用`amount`按元字符串输入，例如`"1000.29"`；响应`amountCents`等整数分字段为字符串。付款用`amount`、`reference`、`proofFileId`和`paidOn`；冲销用`reverseOfId`关联原批准付款。

准确字段与可执行请求见[验收脚本](../scripts/smoke.py)；每个权限仍由当前账号角色和数据范围判定，不能依赖前端按钮隐藏。未实现第三方知识产权服务、银行转账或法规期限计算接口。

---
知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
