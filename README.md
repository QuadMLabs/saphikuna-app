# Saphikuna

Panel de administración de Saphikuna (tejidos artesanales). Es solo para administrar:
las ventas y compras están en la tienda en línea, que es otra aplicación.

## Qué hace

- **Productos:** crear, editar y eliminar, con fotos (WebP optimizado), colores, tallas, precio,
  descuento, disponibilidad y fecha "Nuevo hasta". Código `SPK-###` automático.
- **Atributos:** colores (con su hex), tallas, categorías y colecciones.
- **Catálogos en PDF:** se arman desde el panel (nombre, texto de inicio, prendas elegidas y, por prenda, la foto
  principal, hasta 2 de apoyo y dos textos editables). Se guardan, se duplican y se descargan en PDF con el logo
  en la esquina y un enlace "Ver en la tienda" por prenda.
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

### Antes de empezar
- Los scripts de [`db/`](db) (`001` a `008`) ya están ejecutados en Supabase, en orden.
- Los cambios están unidos a la rama `main` (Render despliega desde ahí).
- Tienes a mano los valores de tu `.env`: `SUPABASE_DB_URL`, `SUPABASE_DB_USER`, `SUPABASE_DB_PASSWORD`,
  `SUPABASE_URL` y `SUPABASE_SERVICE_KEY`. **No los pegues en chats ni en archivos del repositorio.**

### Paso a paso
1. Crea la cuenta en [render.com](https://render.com) (se puede entrar con GitHub).
2. **Conecta GitHub:** el repositorio es privado y pertenece a la organización `QuadMLabs`. En la pantalla de Render
   para conectar GitHub, instala la aplicación de Render en esa organización y da acceso al repositorio
   `saphikuna-app`. Si no eres dueño de la organización, un dueño debe aprobar la instalación.
3. En Render: **New > Blueprint**, elige el repositorio `saphikuna-app` y la rama `main`. Render lee
   [`render.yaml`](render.yaml) y propone el servicio `saphikuna-admin` (plan gratis, región Ohio).
4. Render pide los 5 valores marcados como secretos (`sync: false`): pega los de tu `.env`.
   `SAPHIKUNA_REMEMBER_KEY` la genera Render sola y `SAPHIKUNA_STORE_URL` ya viene puesta.
5. Pulsa **Apply**. El primer despliegue compila la aplicación dentro de Render: calcula entre 5 y 10 minutos.
6. Cuando el servicio diga **Live**, abre su dirección (`https://saphikuna-admin.onrender.com`, o la que Render
   asigne) e ingresa con tu usuario del panel. Los administradores viven en Supabase, así que ya existen.
7. Desde ahora, cada push a `main` vuelve a desplegar solo.

### Cómo se porta el plan gratis (512 MB de memoria y 0,1 de CPU)
- **Se duerme tras 15 minutos sin visitas.** Al despertar tiene que arrancar de nuevo: medido en un equipo
  rápido son 8 segundos y unos 11 segundos de CPU; con la CPU del plan gratis se estiman cerca de **2 minutos**
  (es una estimación, no una medición en Render). Con "Recordarme" no hace falta volver a iniciar sesión.
- **La memoria está ajustada** en el `Dockerfile` (heap de 192 MB; el proceso queda en unos 365 MB). Las fotos se
  procesan de a una y el PDF del catálogo se genera de a uno. Si algo agotara la memoria, el contenedor se
  reinicia solo.
- **Lo pesado va lento** (subir fotos, generar el PDF): se mide en decenas de segundos, no en segundos.
- Para evitar el arranque lento se puede llamar a `/login` cada 10 minutos desde un servicio externo gratuito
  (por ejemplo cron-job.org). Así el servicio no se duerme y entra en las 750 horas gratis del mes. Es opcional.
- Las claves **nunca** van en el repositorio: solo en las variables de entorno de Render.

### Si algo falla
- **El despliegue se queda en "Deploying" o dice que no detecta puertos:** revisa los *Logs*; con CPU limitada el
  arranque tarda. La aplicación escucha en el puerto que Render indica (variable `PORT`).
- **No conecta con la base de datos:** revisa que `SUPABASE_DB_URL` sea la del *Session pooler* (puerto 5432) y que
  la contraseña sea la vigente.
- **No se ven las fotos:** revisa `SUPABASE_URL` y `SUPABASE_SERVICE_KEY` (la clave `secret`, no la `publishable`).

El perfil de producción está en [`application-prod.properties`](src/main/resources/application-prod.properties).

## Marca de agua "Dola AI"

Las fotos de modelo generadas con IA traen la marca "Dola AI" abajo a la derecha. El panel la detecta y la
quita al subir una foto ([`WatermarkRemover`](src/main/java/com/quadmlabs/saphikuna/image/WatermarkRemover.java));
una foto sin marca no se toca. Se puede apagar con `app.images.remove-watermark=false`.

## Base de datos

Los cambios del esquema están en [`db/`](db). Se ejecutan en el SQL Editor de Supabase, **en orden**
(son repetibles): `001` a `008`.

## Pruebas

```bash
./mvnw test
```

## Diseño

El logo original está en [`design/`](design). La app usa `static/images/` (logo e iconos) y las
variables de color de `static/css/admin.css`: cambiar la marca es editar ese bloque.
