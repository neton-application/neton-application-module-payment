-- 挂起原因改为运营手填的自由文本，替换掉原因码 + 参数那套结构。
--
-- 原设计是「码 + 参数」由各端按 locale 渲染，好处是多语言，代价是运营只能从五个
-- 预置码里挑，说不出这一单真正卡在哪。实际业务里挂起后用户是持续找客服的，客服
-- 手里的信息远比五个码丰富，硬塞进码表反而丢信息。
--
-- 多语言因此确实丢了：运营写什么，四种语言的用户看到的都是同一段文字。这是明确
-- 的取舍——福寿万家面向中国大陆用户，能把话说准比能翻译更重要。
--
-- 直接换列而不是并存：V013 上线至今 hold_reason_code 一条非空记录都没有
-- （status=7 的单子也是 0），没有需要兼容的历史数据。

BEGIN;

ALTER TABLE wallet_withdraw_orders
  DROP COLUMN IF EXISTS hold_reason_code,
  DROP COLUMN IF EXISTS hold_reason_params,
  ADD COLUMN IF NOT EXISTS hold_reason_text text;

COMMENT ON COLUMN wallet_withdraw_orders.hold_reason_text IS
  '挂起原因，运营手填，原样展示给用户。不做 i18n。';

COMMIT;
