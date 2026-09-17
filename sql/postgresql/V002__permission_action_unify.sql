-- 权限动作词统一（RBAC spec §3.4 冻结词汇表）：读一律 query，写 create / update / delete。
--
-- ── 背景 ──
-- 脚手架的主流模块（system / infra / member）一直是标准增删改查：`system:user:query` 同时覆盖
-- /page、/simple-list、/get/{id}，读不再按形状细分。payment 是偏离最远的一个，用了
-- page(7) / list(3) / detail(1) / overview(1) 四种说法表达同一件事「读」。
-- 运营在角色授权页面上看到 pay:order:page 与 pay:wallet:overview 并列时，无从判断它们是不是同一类。
--
-- ── 合并语义 ──
-- 原本 `:page`（列表）与 `:query`（单条）是两个权限，合并后同一个 query 覆盖两者。
-- 这是**放宽**：此前只授了列表权限的角色，现在也能看详情。可以接受 —— 菜单授的就是页面，
-- 而页面本来就要同时调列表与详情接口；把它们拆开只会制造「列表能看、点进去 403」。
--
-- 菜单行由 infra baseline 种下，所以本模块的 @Module 声明了 dependsOn = ["system", "infra"]，
-- 保证这条 UPDATE 跑在那些行插入之后（否则匹配 0 行且不报错）。
UPDATE system_menus
   SET permission = regexp_replace(permission, '^(pay:[a-z0-9-]+):(page|list|detail|overview)$', '\1:query'),
       updated_at = (extract(epoch from now()) * 1000)::bigint
 WHERE permission ~ '^pay:[a-z0-9-]+:(page|list|detail|overview)$';

-- `pay:wallet-freeze:menu` 更离谱：menu 不是动作，是「这一行是个菜单」的意思被塞进了 action 段。
-- 后端那个控制器要的是 pay:wallet-freeze:query，所以这条菜单授出去也调不通接口 —— 死授权。
UPDATE system_menus
   SET permission = 'pay:wallet-freeze:query',
       updated_at = (extract(epoch from now()) * 1000)::bigint
 WHERE permission = 'pay:wallet-freeze:menu';
