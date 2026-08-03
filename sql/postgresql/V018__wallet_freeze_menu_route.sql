-- module-payment V018: 修 V017 冻结菜单的挂载点和路由。
--
-- V017 把「冻结管理」挂在了 4050（钱包余额，**它自己是个页面**）下面，而且没写
-- path/component——菜单树里出现了一个页面的子页面，前端也没有任何路由可跳。
-- 正确的父节点是 405（钱包管理，目录），路由与同级的「钱包余额」对齐：
--   4(/pay) → 405(wallet) → 冻结管理(freeze) → 组件 pay/wallet/freeze/index
--
-- 不改 V017 而另开一版：V017 已经在环境里跑过，改已应用的迁移会让 history 校验失败。
-- 这里按 permission 定位、写幂等 UPDATE，V017 跑没跑过、跑过几次都得到同一结果。

SET search_path = public;

UPDATE system_menus
   SET parent_id = 405,
       path = 'freeze',
       component = 'pay/wallet/freeze/index',
       sort = 6,
       updated_at = (extract(epoch from now()) * 1000)::bigint
 WHERE permission = 'pay:wallet-freeze:menu';

-- 按钮跟着父菜单走；V017 已经把它们挂在冻结管理下了，这里只兜底 parent_id
-- （万一 V017 的父菜单查询在某个环境落空）。
UPDATE system_menus b
   SET parent_id = m.id,
       updated_at = (extract(epoch from now()) * 1000)::bigint
  FROM (SELECT id FROM system_menus WHERE permission = 'pay:wallet-freeze:menu' LIMIT 1) m
 WHERE b.permission IN (
         'pay:wallet-freeze:list',
         'pay:wallet-freeze:place',
         'pay:wallet-freeze:judicial',
         'pay:wallet-freeze:release'
       )
   AND b.parent_id <> m.id;
