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

## Publicarlo en Render (plan gratis)

El panel es una aplicación Java, así que necesita un servidor (Netlify y GitHub Pages solo sirven páginas
estáticas). Se publica en Render con el [`Dockerfile`](Dockerfile) y el blueprint [`render.yaml`](render.yaml).

1. Ejecuta en Supabase los scripts de [`db/`](db) (`001` a `007`), en orden.
2. En [Render](https://render.com): **New > Blueprint**, conecta el repositorio de GitHub y elige la rama `main`.
3. Render pide los valores marcados `sync: false`. Son los mismos de tu `.env`
   (`SUPABASE_DB_URL`, `SUPABASE_DB_USER`, `SUPABASE_DB_PASSWORD`, `SUPABASE_URL`, `SUPABASE_SERVICE_KEY`).
   `SAPHIKUNA_REMEMBER_KEY` la genera Render sola.
4. Espera el primer despliegue (varios minutos). La dirección queda como `https://saphikuna-admin.onrender.com`.
5. Cada push a `main` vuelve a desplegar.

Cómo se porta el plan gratis (512 MB de memoria y poca CPU):

- **Se duerme tras 15 minutos sin visitas** y la primera visita siguiente espera mientras arranca
  (puede ser más de un minuto). Con "Recordarme" no hace falta volver a iniciar sesión.
- **La memoria está ajustada** en el `Dockerfile` (heap de 192 MB). Las fotos se procesan de a una y el PDF
  del catálogo se genera de a uno, guardándose un minuto por si se pide de nuevo. Si algo agotara la memoria,
  el contenedor se reinicia solo.
- **Lo pesado va lento** (subir fotos, generar el PDF): se mide en decenas de segundos, no en segundos.
- Las claves **nunca** van en el repositorio: solo en las variables de entorno de Render.

El perfil de producción está en [`application-prod.properties`](src/main/resources/application-prod.properties).

## Marca de agua "Dola AI"

Las fotos de modelo generadas con IA traen la marca "Dola AI" abajo a la derecha. El panel la detecta y la
quita al subir una foto ([`WatermarkRemover`](src/main/java/com/quadmlabs/saphikuna/image/WatermarkRemover.java));
una foto sin marca no se toca. Se puede apagar con `app.images.remove-watermark=false`.

Para limpiar fotos ya publicadas existe una herramienta de una sola vez (no forma parte de la app normal):

```bash
# 1) Vista previa: no escribe nada, guarda un antes/después de cada foto con marca en la carpeta indicada
./mvnw spring-boot:run -Dspring-boot.run.arguments="--cleanup.watermark.mode=preview --cleanup.watermark.out=./preview --server.port=0"
# 2) Aplicar: sube las versiones limpias con nombre nuevo, actualiza el producto y anota las rutas viejas
#    en old-paths.txt (NO borra las fotos viejas)
./mvnw spring-boot:run -Dspring-boot.run.arguments="--cleanup.watermark.mode=apply --cleanup.watermark.out=./cleanup --server.port=0"
```

## Base de datos

Los cambios del esquema están en [`db/`](db). Se ejecutan en el SQL Editor de Supabase, **en orden**
(son repetibles): `001` a `007`.

## Pruebas

```bash
./mvnw test
```

## Diseño

El logo original está en [`design/`](design). La app usa `static/images/` (logo e iconos) y las
variables de color de `static/css/admin.css`: cambiar la marca es editar ese bloque.
