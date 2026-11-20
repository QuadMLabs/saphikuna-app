-- =====================================================================
-- 014 - Video en la tienda: "audio útil" y la vista pública con el video
-- Ejecutar en Supabase > SQL Editor DESPUÉS de 013. Es repetible.
--
--   - videoHasSound: si el audio del video vale la pena. Por defecto NO: la tienda muestra el
--     video sin sonido y sin control de volumen; con "sí", deja activar el sonido.
--   - store_products ahora también entrega videoPath y videoHasSound (al final de las columnas,
--     para que la tienda actual siga funcionando igual).
-- =====================================================================

alter table "products"     add column if not exists "videoHasSound" boolean not null default false;
alter table "collabPieces" add column if not exists "videoHasSound" boolean not null default false;

create or replace view "store_products" as
select
    p."code",
    p."name",
    p."description",
    c."name"                                                           as "category",
    p."label",
    p."availability",
    p."generalPrice"                                                   as "price",
    p."discountPercent",
    round(p."generalPrice" * (1 - p."discountPercent" / 100), 2)       as "finalPrice",
    (p."newUntil" is not null
        and p."newUntil" >= (now() at time zone 'America/Guayaquil')::date) as "isNew",
    p."primaryImagePath",
    coalesce((
        select jsonb_agg(i."storagePath"
                         order by (i."storagePath" = p."primaryImagePath") desc,
                                  i."sortOrder", i."createdAt")
        from "productImages" i
        where i."productId" = p."producttId"
    ), '[]'::jsonb)                                                    as "images",
    coalesce((
        select jsonb_agg(jsonb_build_object('name', s."name", 'price', ps."price")
                         order by s."sortOrder", s."name")
        from "productSizes" ps
        join "sizes" s on s."sizeId" = ps."sizeId"
        where ps."productId" = p."producttId"
    ), '[]'::jsonb)                                                    as "sizes",
    coalesce((
        select jsonb_agg(jsonb_build_object('name', co."name", 'hex', co."hexCode",
                                            'primary', pc."isPrimary")
                         order by pc."isPrimary" desc, co."name")
        from "productColors" pc
        join "colors" co on co."colorId" = pc."colorId"
        where pc."productId" = p."producttId"
    ), '[]'::jsonb)                                                    as "colors",
    coalesce((
        select jsonb_agg(jsonb_build_object('name', sp."name", 'value', sp."value")
                         order by sp."sortOrder", sp."name")
        from "productSpecifications" sp
        where sp."productId" = p."producttId"
    ), '[]'::jsonb)                                                    as "specifications",
    p."videoPath"                                                      as "videoPath",
    p."videoHasSound"                                                  as "videoHasSound"
from "products" p
left join "categories" c on c."categorieId" = p."categoryId"
where p."status" = 'Activo'
  and p."code" is not null;

grant select on "store_products" to anon;
