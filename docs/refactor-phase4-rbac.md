# 阶段 4：数据库驱动 RBAC

## 数据库

- 用户主表由 `t_user` 迁移为 `sys_user`，不再保存单一 `role` 字段。
- 新增 `sys_role`、`sys_permission`、`sys_user_role`、
  `sys_role_permission`，支持用户多角色和角色多权限。
- `V4__database_driven_rbac.sql` 会将旧 `ADMIN` 账号迁移为
  `SUPER_ADMIN`，其他账号迁移为 `USER`，避免升级后失去 RBAC 管理入口。
- 执行迁移前必须备份 `shiqian_user` 数据库。已有数据环境手工执行迁移；
  新 Docker 数据卷会直接按新表结构初始化。

## 鉴权

- 用户服务从数据库合并角色与权限，并缓存到
  `auth:user:authorities:{userId}`。
- 资源服务优先读取同一 Redis 权限快照，未命中时通过带内部服务密钥的
  OpenFeign 接口回源用户服务；依赖不可用时拒绝授权，不从 JWT 旧角色放权。
- 用户角色变化会提升 `token_version`、撤销全部 Refresh Token 并清理权限缓存。
- 角色权限变化会在事务提交后清理所有受影响用户的权限缓存。

## 管理保护

- 只有 `rbac:manage` 可以维护角色、权限和用户多角色关系。
- 内置角色和内置权限不可删除。
- 超级管理员角色不可禁用或手动删减权限。
- 禁止移除或禁用最后一个启用的超级管理员。

## 配置

```dotenv
RBAC_AUTHORITY_CACHE_TTL=24h
INTERNAL_SERVICE_KEY=请设置至少32字节随机值
```
