-- =====================================================================
-- 015 - Colaboraciones en la tienda: vista pública de solo lectura
-- Ejecutar en Supabase > SQL Editor. Es repetible.
--
-- Una fila por prenda, solo de las marcas marcadas como visibles. La tienda la lee con la clave
-- pública (anon) igual que store_products; las tablas de colaboraciones siguen cerradas.
--   - images: rutas de Storage, la principal primero (la miniatura es la misma ruta con
--     "-thumb.webp", igual que en el catálogo).
--   - colors: nombre y código de color.
--   - videoPath / videoHasSound: video corto opcional y si su audio vale la pena.
-- =====================================================================

create or replace view "store_collaborations" as
select
    cp."collabPieceId"                                                 as "id",
    c."brandName"                                                      as "brand",
    cp."name"                                                          as "name",
    cp."description"                                                   as "description",
    coalesce((
        select jsonb_agg(i."storagePath"
                         order by (i."storagePath" = cp."primaryImagePath") desc,
                                  i."sortOrder", i."createdAt")
        from "collabPieceImages" i
        where i."collabPieceId" = cp."collabPieceId"
    ), '[]'::jsonb)                                                    as "images",
    coalesce((
        select jsonb_agg(jsonb_build_object('name', co."name", 'hex', co."hexCode")
                         order by co."name")
        from "collabPieceColors" pc
        join "colors" co on co."colorId" = pc."colorId"
        where pc."collabPieceId" = cp."collabPieceId"
    ), '[]'::jsonb)                                                    as "colors",
    cp."videoPath"                                                     as "videoPath",
    cp."videoHasSound"                                                 as "videoHasSound"
from "collabPieces" cp
join "collaborations" c on c."collaborationId" = cp."collaborationId"
where c."visible"
order by c."brandName", cp."createdAt" desc;

grant select on "store_collaborations" to anon;
