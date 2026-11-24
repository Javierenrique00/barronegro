# Barronegro · Reservas del "Taller de cerámica"

Aplicación **Kotlin Multiplatform + Compose Multiplatform** (Android y Web/Wasm) para reservar
franjas horarias del taller de cerámica. El backend completo vive en **Supabase** (Postgres + Auth + Storage).

Todo el texto de la aplicación está en español y la zona horaria del taller es **America/Bogota (UTC-5)**.

## Arquitectura

```
Android (KMP) ─┐
               ├── :shared (toda la UI y la lógica de cliente)
Web (Wasm/JS) ─┘        │
                        └── Supabase (qxsehofkuwmypuhjzult)
                              • Auth: correo + contraseña (confirmación obligatoria)
                              • Postgres: tablas + RLS + RPC con las reglas de negocio
                              • Storage: avatares, adjuntos de reservas, galería
```

**Las reglas nunca viven en el cliente.** Permisos, cupos, solapamientos, bloqueos y la
verificación humana se validan en Postgres mediante funciones `SECURITY DEFINER`; el cliente solo
invoca RPC y consulta vistas protegidas por RLS. Así la web y el APK no pueden comportarse distinto
ni saltarse reglas modificando el código.

## Modelo de datos

| Tabla | Contenido |
|---|---|
| `salas` | La sala reservable y su **política de reservas**: duración del turno, duración máxima, buffer, antelación mínima, días a la vista, cupos, aprobación automática y reservas por persona y día. Semilla: `Taller de cerámica` (`America/Bogota`). |
| `perfiles` | 1:1 con `auth.users`. `rol` (`admin`/`usuario`) y `estado` (`activo`/`bloqueado`). |
| `disponibilidad_reglas` | **Plantilla semanal**: una ventana `hora_inicio`–`hora_fin` por día de la semana (1 = lunes … 7 = domingo). De aquí salen todas las franjas de todos los días. |
| `horarios` | Franjas puntuales creadas a mano, para fechas u horas fuera de la plantilla. Opcional. |
| `reservas` | Reservas: `pendiente`/`aprobada` (confirmada) → `reprogramada` → `cancelada`/`rechazada`/`expirada`. `horario_id` puede ser `null` cuando la reserva sale de la plantilla. |
| `reserva_eventos` | Bitácora de cada transición (auditoría). |
| `bloqueos_sala` | Rangos en los que el administrador cierra el calendario. |
| `adjuntos` | Metadatos de fotos subidas al bucket privado. |
| `retos_humanos`, `tokens_humanos` | Reto anti-bot y tokens de un solo uso (deny-all para el cliente). |

Migraciones aplicadas (también versionadas en `supabase/migrations/`):

1. `esquema_inicial`
2. `funciones_base`
3. `funciones_calendario_y_reto`
4. `funciones_reservas`
5. `rls_y_privilegios`
6. `storage_buckets_y_politicas`
7. `endurecer_funciones`
8. `borrado_de_usuarios`
9. `disponibilidad_calendly`
10. `hoy_en_zona_sala`

## Reservas tipo Calendly

El administrador publica **una sola vez** el horario de cada día de la semana y el sistema genera las
franjas de todos los días. El usuario elige día → hora → confirma, y la reserva queda **confirmada al
instante** (salvo que el administrador desactive la aprobación automática).

- `disponibilidad_reglas` guarda la plantilla semanal; `guardar_disponibilidad(uuid, jsonb)` la
  reemplaza de forma atómica y valida que no haya dos ventanas solapadas el mismo día.
- `franjas_disponibles(sala, desde, hasta)` devuelve solo lo que se puede reservar: aplica día de la
  semana, duración del turno, cupos, buffer entre reservas, bloqueos, antelación mínima y horizonte.
  El cliente **no calcula disponibilidad**, solo pinta lo que recibe (con `hora_fin_maxima` para
  ofrecer duraciones válidas).
- `reservar_franja(...)` valida otra vez todo en Postgres: rejilla horaria, cupos, bloqueos, límite
  diario, token humano de un solo uso y, con `pg_advisory_xact_lock` por sala, evita que dos personas
  se queden con la misma franja a la vez.
- `actualizar_politica_sala(...)` cambia duración, buffer, antelación, horizonte, cupos, aprobación
  automática y reservas por día desde el panel del administrador.
- `agenda_admin(dias)` devuelve las reservas próximas con nombre y teléfono, para el panel.
- Las franjas de `horarios` creadas a mano siguen apareciendo en el calendario y son reservables;
  se deduplican con las de la plantilla.

> El flujo anterior (`crear_horarios` + `solicitar_reserva`) se conserva por compatibilidad, pero la
> pestaña Calendario ya usa la disponibilidad semanal.

## Verificación humana (opción implementada)

Mecánica propia, idéntica en Android y en web porque es el mismo composable:

1. El cliente llama `crear_reto_humano()` → recibe un molde y cuatro piezas de cerámica
   (forma, color y número de franjas dibujadas con `Canvas`).
2. La persona toca la pieza que coincide con el molde. La respuesta **no viaja al cliente**:
   en el servidor solo se guarda `hmac(secreto, reto || respuesta || nonce)` en `privado.secretos`.
3. `verificar_reto_humano(...)` valida la elección, exige ≥1,2 s de interacción, máximo 3 intentos
   y emite un **token aleatorio de un solo uso** (10 minutos).
4. `solicitar_reserva(...)` consume el token dentro de la misma transacción que bloquea la franja
   (`FOR UPDATE`), por lo que no se puede reutilizar ni paralelizar.

Además: confirmación de correo obligatoria, límite de 1 solicitud pendiente por día y por persona
y máximo 10 retos por hora.

> Honestidad técnica: ningún reto resuelto en el cliente es infalible frente a un bot dirigido.
> La garantía real la dan la verificación en servidor, el token de un solo uso, los límites y el
> correo confirmado.

## Puesta en marcha

1. Crea `local.properties` en la raíz (ya está en `.gitignore`) con:

```properties
supabase.url=https://qxsehofkuwmypuhjzult.supabase.co
supabase.anonKey=<clave pública del proyecto>
```

La tarea `:shared:generarConfiguracionSupabase` genera `SupabaseConfig.kt` en
`shared/build/generated/supabase/` para que Android, JS y Wasm usen los mismos valores.
También se admiten las variables de entorno `SUPABASE_URL` y `SUPABASE_ANON_KEY`.

2. Ejecuta la app:

```bash
./gradlew :androidApp:assembleDebug          # APK de depuración
./gradlew :webApp:wasmJsBrowserDevelopmentRun   # Web (Wasm, navegadores modernos)
./gradlew :webApp:jsBrowserDevelopmentRun       # Web (JS, navegadores antiguos)
```

3. Pruebas:

```bash
./gradlew :shared:testAndroidHostTest
./gradlew :shared:wasmJsTest
```

## Publicación en GitHub Pages

La web (Wasm) se publica en **https://javierenrique00.github.io/barronegro/** cada vez que se hace
push a `main`, mediante `.github/workflows/publicar-paginas.yml` (build → artefacto → `deploy-pages`).

Configuración inicial (una sola vez):

1. **GitHub → Settings → Pages → Build and deployment → Source**: elige **GitHub Actions**.
2. **GitHub → Settings → Secrets and variables → Actions → New repository secret**:
   - `SUPABASE_URL` → `https://qxsehofkuwmypuhjzult.supabase.co`
   - `SUPABASE_ANON_KEY` → la clave pública (`anon`) del proyecto

   El workflow las inyecta como variables de entorno, así que `local.properties` nunca se versiona.
3. **Supabase → Authentication → URL Configuration**:
   - **Site URL**: `https://javierenrique00.github.io/barronegro/`
   - **Redirect URLs**: añade esa misma URL y conserva
     `org.mundocreativo.barronegro://auth-callback` para Android.

   Sin este paso los correos de confirmación siguen apuntando a `localhost` y el enlace no vuelve a
   la web publicada.

El sitio se sirve desde un subdirectorio (`/barronegro/`) pero `index.html` usa rutas relativas, así
que no hace falta configurar ninguna base. La tarea `:webApp:prepararSitioPages` copia el bundle de
producción a `webApp/build/pages`, descarta los `.map` y añade `.nojekyll`.

Revisar el sitio antes de publicarlo:

```bash
./gradlew :webApp:prepararSitioPages
python3 -m http.server 8080 --directory webApp/build/pages
# abre http://localhost:8080/
```

> El bundle usa WebAssembly: funciona en navegadores modernos y necesita `https`
> (GitHub Pages ya lo ofrece).

## Primer administrador

El registro crea usuarios con rol `usuario`. Para habilitar el panel de administración, registra
`javierenrique@yahoo.com` desde la app (o créalo en *Authentication → Users*) y después ejecuta
`supabase/promover_admin.sql`. Con al menos un administrador activo, los demás roles se cambian
desde la función `cambiar_rol_usuario(...)`.

## Consultar la base de datos

`curl` llega a la API HTTP de Supabase (PostgREST, Auth y Storage), pero **no** ejecuta SQL libre y
la clave `anon` no tiene `SELECT` sobre ninguna tabla: hace falta un JWT de usuario. Para probarlo
todo sin recordar las URLs:

```bash
./supabase/consultar.sh salud      # Auth + Storage + ajustes (sin usuario)
./supabase/consultar.sh sql        # qué usar cuando de verdad hace falta SQL
```

Recetas completas, errores esperados y avisos de seguridad en
[`supabase/README.md`](supabase/README.md).

## Correo (confirmación de cuenta y recuperación)

Supabase Auth envía sus propios correos: confirmación de registro, recuperación de contraseña,
invitaciones y cambio de correo. El proyecto **no** puede usar el SMTP integrado porque solo
entrega a las direcciones del equipo de la organización (el resto falla con
`email_address_not_authorized`) y tiene un límite de unos pocos envíos por hora
(`429 over_email_send_rate_limit`).

### Conectar un SMTP propio (Resend)

1. Crea la cuenta en <https://resend.com>, añade tu dominio y publica los DNS que indica
   (SPF y DKIM; añade también un `DMARC` con `v=DMARC1; p=none;`).
2. *Authentication → Emails → SMTP Settings* → **Enable custom SMTP**:

   | Campo | Valor |
   | --- | --- |
   | Host | `smtp.resend.com` |
   | Port | `465` |
   | Username | `resend` |
   | Password | la API key de Resend (`re_...`) |
   | Sender email | `no-reply@tudominio` |
   | Sender name | `Taller Barro Negro` |

   Brevo, SES, Postmark o SendGrid funcionan igual: solo cambian host, puerto y credenciales.

   **Brevo**: host `smtp-relay.brevo.com`, puerto `587`, usuario = el **SMTP login** que muestra
   *SMTP & API → SMTP* (formato `[ID]@smtp-brevo.com`), contraseña = **SMTP key** (`xsmtpsib-…`,
   **no** la API v3) y el remitente debe estar verificado en *Senders*. No actives
   *Security → Authorized IPs*: Supabase envía desde IPs compartidas y rotativas, y el bloqueo
   haría fallar los correos con un error genérico.

   El PATCH se puede automatizar con `supabase/configurar_smtp.py`, que lee las credenciales de
   variables de entorno (nunca del repositorio) y aplica también las plantillas en español:

   ```bash
   export SUPABASE_ACCESS_TOKEN=sbp_...
   export SMTP_KEY=xsmtpsib-...
   export SMTP_FROM=barronegro.omd@gmail.com
   python3 supabase/configurar_smtp.py
   ```
3. *Authentication → Sign In / Providers → Email*: **Confirm email** activado.
4. *Authentication → Rate Limits*: sube el límite de correos por hora (el valor por defecto es
   bajo) y deja 60 s como frecuencia mínima por usuario.
5. *Authentication → Emails → Templates*: las plantillas llegan en inglés; tradúcelas. Variables
   disponibles: `{{ .ConfirmationURL }}`, `{{ .Token }}`, `{{ .SiteURL }}`, `{{ .Email }}`.
6. Prueba con un correo que **no** pertenezca al equipo. Si no llega, *Authentication → Logs*
   muestra el motivo real.

> Con *Confirm email* activo y sin SMTP propio **ningún** registro de cliente funcionaría.

### Enlaces de vuelta a la app

- **Web**: el `Site URL` debe ser `https://javierenrique00.github.io/barronegro/`.
- **Android**: `SupabaseCliente.kt` declara `scheme = "org.mundocreativo.barronegro"` y
  `host = "auth-callback"`, y `MainActivity` llama a `supabase.handleDeeplinks(intent)` para
  importar la sesión. La URL `org.mundocreativo.barronegro://auth-callback` debe estar en
  *Redirect URLs*.
- La recuperación de contraseña se pide desde *"¿Olvidaste tu contraseña?"*. Al abrir el enlace
  el usuario entra con sesión y fija la clave nueva con el botón **Clave** de la cabecera.

### Probar correos sin gastar cuota (local)

```bash
supabase start     # Postgres + Auth + Mailpit
supabase status    # muestra la URL local y la de Mailpit (http://127.0.0.1:54324)
```

`supabase/config.toml` ya trae `[auth.email] enable_confirmations = true`. Todos los correos se
capturan en Mailpit y no hay límite por hora.

> No ejecutes `supabase config push`: este `config.toml` describe el stack **local** (su `site_url`
> es `http://localhost:8080`). El `Site URL`, las *Redirect URLs* y el SMTP de producción se
> configuran en el panel del proyecto alojado.

## Ajustes recomendados en el panel de Supabase

- *Authentication → Providers → Email*: activar el proveedor y **Confirm email**.
- *Authentication → URL Configuration*: `https://javierenrique00.github.io/barronegro/`,
  `http://localhost:8080/**` y `org.mundocreativo.barronegro://auth-callback`.
- *Authentication → Security*: si quieres una capa extra, activa **Cloudflare Turnstile**
  (el cliente ya soporta `captchaToken`; hoy no se envía porque el reto propio cubre el flujo).
- *Authentication → SMTP*: Resend (o equivalente) antes de abrir al público; ver arriba.
- Revisar `get_advisors` en el MCP de Supabase tras cualquier cambio de esquema.

## Pendientes conocidos

- Subida de fotos desde la UI (los buckets y las políticas ya existen; falta el selector de archivos
  por plataforma).
- Notificaciones por correo al aprobar o proponer una fecha: Supabase Auth solo envía correos de
  autenticación, así que requieren un proveedor propio invocado desde una Edge Function o un
  trigger de Postgres.
- Realtime para que el panel del administrador se actualice sin pulsar "Actualizar".
- **Sincronización con Google Calendar (fase 2).** Queda preparada pero no implementada: al crear,
  reprogramar o cancelar una reserva habrá que escribir el evento en el calendario del taller. La
  vía prevista es una Edge Function con cuenta de servicio de Google (se comparte el calendario con
  su correo, sin OAuth) más una cola `google_cola` alimentada por un trigger; `reservas.horario_id`
  ya es opcional y las reglas viven en Postgres, así que la fase 2 solo añade columnas y la función.
