# Saphikuna

Panel de administración de Saphikuna (tejidos artesanales). Es solo para administrar:
las ventas y compras están en la tienda en línea, que es otra aplicación.

## Qué hace

- **Productos:** crear, editar y eliminar, con fotos (WebP optimizado), colores, tallas, precio,
  descuento, disponibilidad y fecha "Nuevo hasta". Código `SPK-###` automático.
- **Catálogos:** colores (con su hex), tallas, categorías y colecciones.
- **Encargos:** obras por pedido para marcas, con fecha de entrega, estado, precio y adelanto.
  El menú muestra cuántas entregas son urgentes.
- **Inicio:** entregas, resumen, productos por completar y últimos modificados.
- **Configuración:** días de "nuevo", datos de la tienda y estado del sistema.

Pensado para usarse desde el celular y desde la laptop, con el mínimo de JavaScript.

## Tecnologías

Java 21, Spring Boot 4, Thymeleaf, JPA/Hibernate, PostgreSQL y Supabase Storage (fotos), Maven.

## Cómo ejecutarlo

```bash
./mvnw spring-boot:run
```

Las credenciales **no están en el código**. Copia [`.env.example`](.env.example) como `.env` (en la
carpeta del proyecto, no se sube a git) y completa los valores; en un servidor se definen las mismas
variables de entorno. Sin ellas la app no arranca.

| Variable | Para qué |
|---|---|
| `SUPABASE_DB_URL`, `SUPABASE_DB_USER`, `SUPABASE_DB_PASSWORD` | Base de datos |
| `SUPABASE_URL`, `SUPABASE_SERVICE_KEY` | Almacenamiento de fotos |
| `SAPHIKUNA_REMEMBER_KEY` | Firma de la cookie "Recordarme" (cadena larga al azar) |
| `SAPHIKUNA_ADMIN_PASSWORD` | Opcional: clave del primer administrador |
| `SAPHIKUNA_STORE_URL` | Opcional: enlace a la app de ventas |

Al arrancar con la tabla `admins` vacía se crea el usuario `admin`; si no definiste
`SAPHIKUNA_ADMIN_PASSWORD`, la clave temporal sale una sola vez en el log.

## Base de datos

Los cambios del esquema están en [`db/`](db). Se ejecutan en el SQL Editor de Supabase, **en orden**
(son repetibles): `001` a `006`.

## Pruebas

```bash
./mvnw test
```

## Diseño

El logo original está en [`design/`](design). La app usa `static/images/` (logo e iconos) y las
variables de color de `static/css/admin.css`: cambiar la marca es editar ese bloque.
