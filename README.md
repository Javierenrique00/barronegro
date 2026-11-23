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
| `salas` | La sala reservable. Semilla: `Taller de cerámica` (`America/Bogota`). |
| `perfiles` | 1:1 con `auth.users`. `rol` (`admin`/`usuario`) y `estado` (`activo`/`bloqueado`). |
| `horarios` | Franjas que crea el administrador: `fecha`, `hora_inicio`, `hora_fin`, `capacidad`, `estado`. |
| `reservas` | Solicitudes: `pendiente → aprobada | rechazada | reprogramada → aprobada/cancelada`. |
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

## Primer administrador

El registro crea usuarios con rol `usuario`. Para habilitar el panel de administración, registra
`javierenrique@yahoo.com` desde la app (o créalo en *Authentication → Users*) y después ejecuta
`supabase/promover_admin.sql`. Con al menos un administrador activo, los demás roles se cambian
desde la función `cambiar_rol_usuario(...)`.

## Antes de la primera prueba

El registro usa el SMTP integrado de Supabase, que tiene un límite muy bajo de envíos por hora
(la primera prueba devolvió `429 over_email_send_rate_limit`). Para poder probar hoy mismo, elige una:

- **Desarrollo**: *Authentication → Providers → Email* → desactivar **Confirm email**. El registro
  devuelve sesión inmediatamente y la app entra sin esperar el correo.
- **Producción**: conectar un SMTP propio (Resend, SES, Postmark…) y mantener **Confirm email**
  activado.

## Ajustes recomendados en el panel de Supabase

- *Authentication → Providers → Email*: activar el proveedor y **Confirm email**.
- *Authentication → URL Configuration*: incluir el dominio de la web, `http://localhost:8080/**`
  y `org.mundocreativo.barronegro://auth-callback` (enlace profundo de Android).
- *Authentication → Security*: si quieres una capa extra, activa **Cloudflare Turnstile**
  (el cliente ya soporta `captchaToken`; hoy no se envía porque el reto propio cubre el flujo).
- *Authentication → SMTP*: configurar un proveedor propio antes de abrir al público.
- Revisar `get_advisors` en el MCP de Supabase tras cualquier cambio de esquema.

## Pendientes conocidos

- Subida de fotos desde la UI (los buckets y las políticas ya existen; falta el selector de archivos
  por plataforma).
- Notificaciones por correo al aprobar o proponer una fecha.
- Realtime para que el panel del administrador se actualice sin pulsar "Actualizar".
