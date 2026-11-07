-- =====================================================================
-- 001 - Simplificación del modelo de productos (Saphikuna)
-- Ejecutar en Supabase > SQL Editor. Es idempotente (se puede repetir).
-- NO borra productVariants: el DROP final está comentado al final.
-- =====================================================================

begin;

-- ---------------------------------------------------------------------
-- 1) products: código, etiqueta, disponibilidad y descuento
-- ---------------------------------------------------------------------
alter table "products"
    add column if not exists "code"            text,
    add column if not exists "label"           text,
    add column if not exists "availability"    text          not null default 'Disponible',
    add column if not exists "discountPercent" numeric(5,2)  not null default 0;

do $$
begin
    if not exists (select 1 from pg_constraint where conname = 'products_availability_chk') then
        alter table "products"
            add constraint products_availability_chk
            check ("availability" in ('Disponible', 'Agotado', 'Bajo pedido'));
    end if;

    if not exists (select 1 from pg_constraint where conname = 'products_discount_chk') then
        alter table "products"
            add constraint products_discount_chk
            check ("discountPercent" >= 0 and "discountPercent" <= 90);
    end if;
end $$;

-- Código único (sin distinguir mayúsculas); varios productos pueden no tener código
create unique index if not exists products_code_uq
    on "products" (lower("code"))
    where "code" is not null;

-- ---------------------------------------------------------------------
-- 2) Colores del producto (principales / secundarios)
-- ---------------------------------------------------------------------
create table if not exists "productColors" (
    "productColorId" uuid primary key default gen_random_uuid(),
    "productId"      uuid    not null references "products" ("producttId") on delete cascade,
    "colorId"        uuid    not null references "colors"   ("colorId")    on delete restrict,
    "isPrimary"      boolean not null default true,
    unique ("productId", "colorId")
);

-- ---------------------------------------------------------------------
-- 3) Tallas del producto (precio solo si la talla cuesta distinto)
-- ---------------------------------------------------------------------
create table if not exists "productSizes" (
    "productSizeId" uuid primary key default gen_random_uuid(),
    "productId"     uuid not null references "products" ("producttId") on delete cascade,
    "sizeId"        uuid not null references "sizes"    ("sizeId")     on delete restrict,
    "price"         numeric(10,2),            -- null = usa el precio general
    unique ("productId", "sizeId")
);

-- ---------------------------------------------------------------------
-- 4) Especificaciones (Material, Técnica, Diseño...)
-- ---------------------------------------------------------------------
create table if not exists "productSpecifications" (
    "productSpecificationId" uuid primary key default gen_random_uuid(),
    "productId"  uuid not null references "products" ("producttId") on delete cascade,
    "name"       text not null,
    "value"      text not null,
    "sortOrder"  int2 not null default 0
);

create index if not exists productspecs_product_idx on "productSpecifications" ("productId");

-- ---------------------------------------------------------------------
-- 5) Migrar datos desde productVariants (si existe)
-- ---------------------------------------------------------------------
do $$
begin
    if to_regclass('public."productVariants"') is not null then

        insert into "productColors" ("productId", "colorId", "isPrimary")
        select distinct v."productId", v."colorId", true
        from "productVariants" v
        where v."colorId" is not null
        on conflict ("productId", "colorId") do nothing;

        insert into "productSizes" ("productId", "sizeId", "price")
        select v."productId", v."sizeId", nullif(min(v."price"), p."generalPrice")
        from "productVariants" v
        join "products" p on p."producttId" = v."productId"
        where v."sizeId" is not null
        group by v."productId", v."sizeId", p."generalPrice"
        on conflict ("productId", "sizeId") do nothing;

        -- Con variantes y ninguna con stock => Agotado
        update "products" p
        set "availability" = 'Agotado'
        where exists (select 1 from "productVariants" v where v."productId" = p."producttId")
          and not exists (select 1 from "productVariants" v
                          where v."productId" = p."producttId" and v."stock" > 0);
    end if;
end $$;

commit;

-- ---------------------------------------------------------------------
-- 6) Opcional, cuando todo esté verificado:
-- ---------------------------------------------------------------------
-- drop table "productVariants";
