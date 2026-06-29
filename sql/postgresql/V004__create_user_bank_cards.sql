-- module-payment V004: 用户银行卡（P4-B1）
-- 卡号信封加密存储：card_no_ciphertext(AES-256-GCM box) + encrypted_data_key(被主密钥/KMS 包裹)。
-- 明文卡号绝不入库；列表/用户端只回 card_no_masked；card_no_hash(HMAC) 仅 (user_id,hash) 去重，不可逆。
SET search_path = public;

CREATE SEQUENCE IF NOT EXISTS public.user_bank_cards_id_seq
    START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

CREATE TABLE IF NOT EXISTS public.user_bank_cards (
    id bigint NOT NULL DEFAULT nextval('public.user_bank_cards_id_seq'::regclass),
    user_id bigint NOT NULL,
    holder_name character varying(64) NOT NULL,
    bank_name character varying(128) NOT NULL,
    bank_code character varying(64),
    card_no_ciphertext text NOT NULL,
    card_no_nonce text,
    encrypted_data_key text NOT NULL,
    card_no_masked character varying(32) NOT NULL,
    card_no_hash character varying(128) NOT NULL,
    encryption_version integer DEFAULT 1 NOT NULL,
    status smallint DEFAULT 0 NOT NULL,
    created_at bigint DEFAULT 0 NOT NULL,
    updated_at bigint DEFAULT 0 NOT NULL,
    deleted_at bigint DEFAULT 0 NOT NULL,
    CONSTRAINT user_bank_cards_pkey PRIMARY KEY (id)
);

ALTER SEQUENCE public.user_bank_cards_id_seq OWNED BY public.user_bank_cards.id;

-- 同一用户不可重复绑同一张卡（仅约束有效行）。
CREATE UNIQUE INDEX IF NOT EXISTS uq_user_bank_cards_user_hash
    ON public.user_bank_cards (user_id, card_no_hash) WHERE deleted_at = 0;

-- 查某用户有效卡列表。
CREATE INDEX IF NOT EXISTS idx_user_bank_cards_user
    ON public.user_bank_cards (user_id) WHERE deleted_at = 0;
