-- =====================================================================
-- 006 - Administradores (inicio de sesión del panel)
-- Ejecutar en Supabase > SQL Editor ANTES de usar la versión con login.
-- Es repetible. El primer administrador lo crea la propia aplicación al
-- arrancar si la tabla está vacía (la clave temporal sale en el log).
-- =====================================================================

create table if not exists "admins" (
    "adminId"        uuid primary key default gen_random_uuid(),
    "username"       text        not null,                 -- en minúsculas
    "displayName"    text        not null,
    "passwordHash"   text        not null,                 -- BCrypt, nunca la clave
    "enabled"        boolean     not null default true,
    "failedAttempts" int4        not null default 0,       -- intentos fallidos seguidos
    "lockedUntil"    timestamptz,                          -- bloqueo temporal por intentos
    "lastLoginAt"    timestamptz,
    "createdAt"      timestamptz not null default now()
);

create unique index if not exists admins_username_key on "admins" ("username");

-- Datos privados: sin acceso desde la API pública de Supabase (clave anon).
-- La aplicación se conecta directo a la base y no se ve afectada.
alter table "admins" enable row level security;
