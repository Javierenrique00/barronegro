-- 0009 · Reservas anónimas identificadas por una sesión de navegador
--
-- El visitante no crea un usuario de Supabase Auth. Al entrar por primera vez recibe una
-- sesión aleatoria (64 caracteres hex) que el cliente guarda en una cookie en web y en
-- SharedPreferences en Android. Solo se almacena su hash en `reservas.sesion_hash`, de modo
-- que el valor de la cookie nunca queda en la base de datos.
--
-- Con esa sesión el visitante puede:
--   * resolver el reto humano y reservar una franja,
--   * volver a la web y ver sus reservas anónimas,
--   * cancelar una reserva propia.
--
-- Las reglas de cupos, bloqueos, duración, buffer y horizonte siguen viviendo aquí.

-- Tipo de origen de la reserva ------------------------------------------------
do $$
begin
  create type public.origen_reserva as enum ('cuenta', 'anonima');
exception
  when duplicate_object then null;
end $$;

-- Datos de contacto y sesión anónima -----------------------------------------
alter table public.reservas
  alter column usuario_id drop not null,
  add column nombre_contacto text,
  add column correo_contacto text,
  add column telefono_contacto text,
  add column sesion_hash text,
  add column origen public.origen_reserva not null default 'cuenta';

alter table public.reservas
  add constraint reservas_identidad_check check (
    (origen = 'cuenta' and usuario_id is not null)
    or (
      origen = 'anonima'
      and usuario_id is null
      and sesion_hash is not null
      and correo_contacto is not null
      and telefono_contacto is not null
    )
  ),
  add constraint reservas_sesion_hash_check check (
    sesion_hash is null or sesion_hash ~ '^[0-9a-f]{64}$'
  );

create index reservas_sesion_fecha_idx
  on public.reservas (sesion_hash, fecha)
  where origen = 'anonima';

create index reservas_correo_fecha_idx
  on public.reservas (correo_contacto, fecha)
  where origen = 'anonima';

-- Retos y tokens anónimos -----------------------------------------------------
alter table public.retos_humanos
  alter column usuario_id drop not null,
  add column correo_hash text,
  add column sesion_hash text;

alter table public.tokens_humanos
  alter column usuario_id drop not null,
  add column correo_hash text,
  add column sesion_hash text;

alter table public.retos_humanos
  add constraint retos_identidad_check check (
    usuario_id is not null
    or (correo_hash is not null and sesion_hash is not null)
  );

alter table public.tokens_humanos
  add constraint tokens_identidad_check check (
    usuario_id is not null
    or (correo_hash is not null and sesion_hash is not null)
  );

create index retos_humanos_sesion_idx
  on public.retos_humanos (sesion_hash, creado_en)
  where usuario_id is null;

create index retos_humanos_correo_idx
  on public.retos_humanos (correo_hash, creado_en)
  where usuario_id is null;

create index tokens_humanos_sesion_idx
  on public.tokens_humanos (sesion_hash)
  where usuario_id is null;

-- Sesión anónima --------------------------------------------------------------
-- No se guarda una fila por sesión: el token es un valor aleatorio de 256 bits y en las
-- reservas solo se conserva su hash. Así no hay que mantener una tabla de sesiones.
create or replace function public.crear_sesion_anonima()
returns jsonb
language sql
security definer
set search_path = public
as $$
  select jsonb_build_object(
    'clave', encode(extensions.gen_random_bytes(32), 'hex'),
    'expira_en', now() + interval '1 year'
  );
$$;

revoke all on function public.crear_sesion_anonima() from public;
grant execute on function public.crear_sesion_anonima() to anon, authenticated;

-- Datos públicos de la sala ---------------------------------------------------
-- El calendario anónimo necesita la política de reservas (duración, aprobación automática…)
-- sin dar acceso directo a la tabla `salas`.
create or replace function public.sala_publica(p_sala_id uuid)
returns table (
  id uuid,
  nombre text,
  descripcion text,
  zona_horaria text,
  capacidad_por_turno int,
  calendario_abierto boolean,
  duracion_turno_min int,
  duracion_maxima_min int,
  buffer_min int,
  antelacion_min_horas int,
  dias_max_antelacion int,
  aprobacion_automatica boolean,
  max_reservas_por_dia int,
  activa boolean
)
language sql
stable
security definer
set search_path = public
as $$
  select s.id,
         s.nombre,
         s.descripcion,
         s.zona_horaria,
         s.capacidad_por_turno,
         s.calendario_abierto,
         s.duracion_turno_min,
         s.duracion_maxima_min,
         s.buffer_min,
         s.antelacion_min_horas,
         s.dias_max_antelacion,
         s.aprobacion_automatica,
         s.max_reservas_por_dia,
         s.activa
  from public.salas s
  where s.id = p_sala_id;
$$;

revoke all on function public.sala_publica(uuid) from public;
grant execute on function public.sala_publica(uuid) to anon, authenticated;

-- El calendario anónimo también necesita franjas_disponibles.
grant execute on function public.franjas_disponibles(uuid, date, date) to anon;

-- Reto humano anónimo ---------------------------------------------------------
create or replace function public.crear_reto_humano_anonimo(
  p_sesion text,
  p_correo text
) returns jsonb
language plpgsql
security definer
set search_path = public, privado
as $$
declare
  v_sesion_hash text;
  v_correo text;
  v_correo_hash text;
  v_total int;
  v_id uuid := gen_random_uuid();
  v_nonce text := encode(extensions.gen_random_bytes(8), 'hex');
  v_formas text[] := array['taza', 'plato', 'jarron', 'maceta'];
  v_colores text[] := array['#C2704B', '#5B7F6B', '#3E5C76', '#8C6A3F'];
  v_forma_obj text;
  v_franjas_obj int;
  v_correcta int;
  v_piezas jsonb := '[]'::jsonb;
  v_forma text;
  v_franjas int;
  v_i int;
begin
  if p_sesion is null or p_sesion !~ '^[0-9a-f]{64}$' then
    raise exception 'La sesión de este navegador no es válida. Recarga la página.';
  end if;

  v_sesion_hash := privado.hash(p_sesion);
  v_correo := lower(trim(coalesce(p_correo, '')));
  if length(v_correo) > 254
     or v_correo !~ '^[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}$' then
    raise exception 'Escribe un correo válido';
  end if;
  v_correo_hash := privado.hash(v_correo);

  select count(*) into v_total
  from public.retos_humanos r
  where r.sesion_hash = v_sesion_hash and r.creado_en > now() - interval '1 hour';
  if v_total >= 10 then
    raise exception 'Demasiadas verificaciones desde este navegador, intenta más tarde' using errcode = '55000';
  end if;

  select count(*) into v_total
  from public.retos_humanos r
  where r.correo_hash = v_correo_hash and r.creado_en > now() - interval '1 hour';
  if v_total >= 5 then
    raise exception 'Demasiadas verificaciones para ese correo, intenta más tarde' using errcode = '55000';
  end if;

  select count(*) into v_total
  from public.retos_humanos r
  where r.usuario_id is null and r.creado_en > now() - interval '1 hour';
  if v_total >= 300 then
    raise exception 'El taller está recibiendo demasiadas solicitudes, intenta en unos minutos' using errcode = '55000';
  end if;

  delete from public.retos_humanos where expira_en < now() - interval '1 day';
  delete from public.tokens_humanos where expira_en < now() - interval '1 day';

  v_forma_obj := v_formas[1 + floor(random() * 4)::int];
  v_franjas_obj := 1 + floor(random() * 4)::int;
  v_correcta := 1 + floor(random() * 4)::int;

  for v_i in 1..4 loop
    if v_i = v_correcta then
      v_forma := v_forma_obj;
      v_franjas := v_franjas_obj;
    elsif v_i % 2 = 0 then
      v_forma := v_formas[(array_position(v_formas, v_forma_obj) % 4) + 1];
      v_franjas := v_franjas_obj;
    else
      v_forma := v_forma_obj;
      v_franjas := (v_franjas_obj % 4) + 1;
    end if;

    v_piezas := v_piezas || jsonb_build_object(
      'id', v_i,
      'forma', v_forma,
      'franjas', v_franjas,
      'color', v_colores[((v_i - 1) % 4) + 1],
      'rotacion', ((v_i * 37 + v_franjas * 11) % 21) - 10
    );
  end loop;

  insert into public.retos_humanos (
    id, usuario_id, nonce, reto, respuesta_hash, expira_en, correo_hash, sesion_hash
  ) values (
    v_id, null, v_nonce,
    jsonb_build_object(
      'molde', jsonb_build_object('forma', v_forma_obj, 'franjas', v_franjas_obj),
      'piezas', v_piezas
    ),
    privado.firma(v_id, v_correcta, v_nonce),
    now() + interval '3 minutes',
    v_correo_hash,
    v_sesion_hash
  );

  return jsonb_build_object(
    'id', v_id,
    'molde', jsonb_build_object('forma', v_forma_obj, 'franjas', v_franjas_obj),
    'piezas', v_piezas,
    'instruccion', 'Toca la pieza que coincide con el molde: misma forma y mismo número de franjas.',
    'expira_en', now() + interval '3 minutes',
    'min_ms', 1200
  );
end;
$$;


create or replace function public.verificar_reto_humano_anonimo(
  p_reto_id uuid,
  p_pieza_id int,
  p_duracion_ms int,
  p_sesion text
) returns jsonb
language plpgsql
security definer
set search_path = public, privado
as $$
declare
  v_sesion_hash text;
  v_reto public.retos_humanos;
  v_token text;
  v_expira timestamptz;
begin
  if p_sesion is null or p_sesion !~ '^[0-9a-f]{64}$' then
    raise exception 'La sesión de este navegador no es válida. Recarga la página.' using errcode = '42501';
  end if;
  v_sesion_hash := privado.hash(p_sesion);

  select * into v_reto from public.retos_humanos where id = p_reto_id for update;
  if not found then
    raise exception 'El reto no existe';
  end if;
  if v_reto.usuario_id is not null
     or v_reto.sesion_hash is distinct from v_sesion_hash then
    raise exception 'Este reto pertenece a otra sesión' using errcode = '42501';
  end if;
  if v_reto.resuelto_en is not null then
    raise exception 'Este reto ya fue utilizado';
  end if;
  if v_reto.expira_en < now() then
    raise exception 'El reto expiró, solicita uno nuevo';
  end if;
  if v_reto.intentos >= 3 then
    raise exception 'Demasiados intentos fallidos, solicita un reto nuevo' using errcode = '55000';
  end if;

  if coalesce(p_duracion_ms, 0) < 1200 then
    update public.retos_humanos set intentos = intentos + 1 where id = p_reto_id;
    raise exception 'La verificación fue demasiado rápida';
  end if;

  if privado.firma(v_reto.id, coalesce(p_pieza_id, -1), v_reto.nonce) <> v_reto.respuesta_hash then
    update public.retos_humanos set intentos = intentos + 1 where id = p_reto_id;
    raise exception 'La pieza elegida no coincide con el molde';
  end if;

  v_token := encode(extensions.gen_random_bytes(32), 'hex');
  v_expira := now() + interval '10 minutes';

  insert into public.tokens_humanos (
    usuario_id, token_hash, expira_en, correo_hash, sesion_hash
  ) values (
    null, privado.hash(v_token), v_expira, v_reto.correo_hash, v_reto.sesion_hash
  );

  update public.retos_humanos set resuelto_en = now() where id = p_reto_id;

  return jsonb_build_object('token', v_token, 'expira_en', v_expira);
end;
$$;

revoke all on function public.crear_reto_humano_anonimo(text, text) from public;
revoke all on function public.verificar_reto_humano_anonimo(uuid, integer, integer, text) from public;
grant execute on function public.crear_reto_humano_anonimo(text, text) to anon, authenticated;
grant execute on function public.verificar_reto_humano_anonimo(uuid, integer, integer, text) to anon, authenticated;

