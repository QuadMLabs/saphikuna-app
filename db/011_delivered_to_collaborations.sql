-- =====================================================================
-- 011 - Encargos entregados pasan solos a Colaboraciones
-- Ejecutar en Supabase > SQL Editor DESPUÉS de 009 y 010. Es repetible.
--
-- Cuando un encargo de una marca queda "Entregado", la aplicación crea una prenda en esa marca
-- (sin fotos; se agregan después). "commissionId" guarda de qué encargo viene, para no crearla
-- dos veces. Si el encargo se borra, la prenda se conserva.
--
-- Además, esta misma consulta pasa a Colaboraciones los encargos de marcas que YA están
-- entregados (por ejemplo, los que se entregaron antes de existir esta función).
-- =====================================================================

alter table "collabPieces"
    add column if not exists "commissionId" uuid references "commissions" ("commissionId") on delete set null;

create unique index if not exists collabpieces_commission_uidx
    on "collabPieces" ("commissionId") where "commissionId" is not null;

insert into "collabPieces" ("collabPieceId", "collaborationId", "name", "description", "commissionId", "createdAt", "updatedAt")
select gen_random_uuid(), c."collaborationId", left(c."title", 150), left(c."description", 1000),
       c."commissionId", c."updatedAt", c."updatedAt"
from "commissions" c
where c."status" = 'Entregado'
  and c."collaborationId" is not null
  and not exists (select 1 from "collabPieces" p where p."commissionId" = c."commissionId");
