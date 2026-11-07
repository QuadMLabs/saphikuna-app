-- =====================================================================
-- 004 - Encargos (obras para entrega a marcas/empresas)
-- Ejecutar en Supabase > SQL Editor ANTES de abrir Encargos o el Home.
-- Es repetible (no borra ni cambia datos existentes).
-- =====================================================================

create table if not exists "commissions" (
    "commissionId" uuid primary key default gen_random_uuid(),
    "clientName"   text          not null,                         -- empresa o marca
    "title"        text          not null,                         -- qué se hace
    "description"  text,
    "quantity"     int4          not null default 1 check ("quantity" > 0),
    "dueDate"      date          not null,                         -- fecha de entrega
    "status"       text          not null default 'Por empezar'
                   check ("status" in ('Por empezar', 'En proceso', 'Listo', 'Entregado')),
    "agreedPrice"  numeric(10,2) check ("agreedPrice" >= 0),       -- precio acordado (opcional)
    "advancePaid"  numeric(10,2) not null default 0 check ("advancePaid" >= 0),
    "notes"        text,
    "createdAt"    timestamptz   not null default now(),
    "updatedAt"    timestamptz   not null default now()
);

-- Para listar pendientes por fecha rápido
create index if not exists commissions_status_due_idx on "commissions" ("status", "dueDate");

-- Datos privados del negocio (clientes y montos): se activa RLS sin políticas para
-- que la API pública de Supabase (clave anon) NO pueda leerlos. La aplicación se
-- conecta directo a la base con su usuario y no se ve afectada.
alter table "commissions" enable row level security;
