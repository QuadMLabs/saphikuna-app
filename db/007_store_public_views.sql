-- =====================================================================
-- 007 - Lectura pública para la tienda (reemplaza a products.json)
-- Ejecutar en Supabase > SQL Editor. Es repetible.
--
-- Qué hace:
--   1) CIERRA todas las tablas a la API pública de Supabase (clave "publishable"/anon):
--      activa RLS y quita los permisos de anon y authenticated. Hasta ahora varias tablas
--      (products, categories, colors...) no tenían RLS: con la clave pública se podían
--      leer y hasta modificar. El panel NO se ve afectado: se conecta directo a la base
--      con el usuario postgres, que ignora RLS.
--   2) Crea DOS vistas de solo lectura con lo que la tienda necesita mostrar:
--        store_products  -> productos activos, con tallas, colores, fotos y especificaciones
--        store_settings  -> datos de la tienda (nombre, WhatsApp, redes...)
--      Es lo único que la tienda puede leer; ninguna otra tabla es accesible.
--
-- Nota: Supabase marca las vistas como "security definer" en su asesor. Es lo buscado:
-- la vista lee las tablas con permisos del dueño y expone solo estas columnas.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) Cerrar todas las tablas a la API pública
-- ---------------------------------------------------------------------
do $$
declare
    t record;
begin
    for t in select tablename from pg_tables where schemaname = 'public' loop
        execute format('alter table public.%I enable row level security', t.tablename);
        execute format('revoke all on table public.%I from anon, authenticated', t.tablename);
    end loop;
end $$;

-- ---------------------------------------------------------------------
-- 2) Productos para la tienda
--    - Solo productos Activos y con código.
--    - Los agotados se incluyen (la tienda los muestra como agotados).
--    - images: rutas de Storage, la principal primero. La miniatura de cada foto es la
--      misma ruta con "-thumb.webp" (ver StorageService.thumbPath).
-- ---------------------------------------------------------------------
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
    ), '[]'::jsonb)                                                    as "specifications"
from "products" p
left join "categories" c on c."categorieId" = p."categoryId"
where p."status" = 'Activo'
  and p."code" is not null;

-- ---------------------------------------------------------------------
-- 3) Datos de la tienda (solo estas claves; nada más de "settings")
-- ---------------------------------------------------------------------
create or replace view "store_settings" as
select s."key", s."value"
from "settings" s
where s."key" in ('storeName', 'storeTagline', 'storeWhatsapp', 'storePhone',
                  'storeEmail', 'storeInstagram', 'storeFacebook', 'storeAddress');

-- ---------------------------------------------------------------------
-- 4) Lo único que la clave pública puede leer
-- ---------------------------------------------------------------------
grant select on "store_products" to anon;
grant select on "store_settings" to anon;
