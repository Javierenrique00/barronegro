
-- Reserva anónima -------------------------------------------------------------
create or replace function public.reservar_franja_anonima(
  p_sala_id uuid,
  p_fecha date,
  p_hora_inicio time,
  p_duracion_min int default null,
  p_nombre text default null,
  p_correo text default null,
  p_telefono text default null,
  p_comentario text default null,
  p_token_humano text default null,
  p_sesion text default null
) returns jsonb
language plpgsql
security definer
set search_path = public, privado
as $$
declare
  v_sesion_hash text;
  v_correo text;
  v_correo_hash text;
  v_telefono text;
  v_nombre text;
  v_comentario text;
  v_sala public.salas;
  v_duracion int;
  v_fin time;
  v_hoy date;
  v_horario_id uuid;
  v_token public.tokens_humanos;
  v_estado public.estado_reserva;
  v_del_dia int;
  v_id uuid;
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

  v_telefono := regexp_replace(coalesce(p_telefono, ''), '[^0-9]', '', 'g');
  if length(v_telefono) < 7 or length(v_telefono) > 20 then
    raise exception 'Escribe un teléfono válido (entre 7 y 20 dígitos)';
  end if;

  v_nombre := nullif(left(trim(coalesce(p_nombre, '')), 120), '');
  v_comentario := nullif(left(trim(coalesce(p_comentario, '')), 500), '');

  select * into v_sala from public.salas s where s.id = p_sala_id;
  if not found or not v_sala.activa then
    raise exception 'La sala no existe';
  end if;
  if not v_sala.calendario_abierto then
    raise exception 'El calendario del taller está cerrado';
  end if;
  if p_hora_inicio is null then
    raise exception 'Elige una hora de inicio';
  end if;

  v_duracion := coalesce(p_duracion_min, v_sala.duracion_turno_min);
  if v_duracion < v_sala.duracion_turno_min or v_duracion > v_sala.duracion_maxima_min then
    raise exception 'La duración debe estar entre % y % minutos',
      v_sala.duracion_turno_min, v_sala.duracion_maxima_min;
  end if;
  if mod(v_duracion, v_sala.duracion_turno_min) <> 0 then
    raise exception 'La duración debe ser múltiplo de % minutos', v_sala.duracion_turno_min;
  end if;

  v_fin := p_hora_inicio + make_interval(mins => v_duracion);
  if v_fin <= p_hora_inicio then
    raise exception 'La reserva no puede terminar al día siguiente';
  end if;

  v_hoy := (now() at time zone v_sala.zona_horaria)::date;
  if p_fecha > v_hoy + coalesce(v_sala.dias_max_antelacion, 60) then
    raise exception 'Solo puedes reservar hasta % días de antelación',
      coalesce(v_sala.dias_max_antelacion, 60);
  end if;

  perform pg_advisory_xact_lock(hashtextextended(p_sala_id::text, 0));

  select h.id into v_horario_id
  from public.horarios h
  where h.sala_id = p_sala_id
    and h.fecha = p_fecha
    and h.estado = 'disponible'
    and h.hora_inicio <= p_hora_inicio
    and h.hora_fin >= v_fin
  order by h.hora_inicio desc
  limit 1;

  perform privado.validar_franja(p_sala_id, v_horario_id, p_fecha, p_hora_inicio, v_fin, null);

  if coalesce(v_sala.max_reservas_por_dia, 0) > 0 then
    select count(*) into v_del_dia
    from public.reservas r
    where r.correo_contacto = v_correo
      and r.fecha = p_fecha
      and r.estado in ('pendiente', 'aprobada');

    if v_del_dia >= v_sala.max_reservas_por_dia then
      raise exception 'Ese correo ya tiene el máximo de reservas para ese día';
    end if;
  end if;

  if p_token_humano is null or length(p_token_humano) < 32 then
    raise exception 'Falta la verificación humana' using errcode = '42501';
  end if;

  select * into v_token
  from public.tokens_humanos t
  where t.token_hash = privado.hash(p_token_humano)
  for update;

  if not found or v_token.usado_en is not null
     or v_token.expira_en < now()
     or v_token.usuario_id is not null
     or v_token.sesion_hash is distinct from v_sesion_hash
     or v_token.correo_hash is distinct from v_correo_hash then
    raise exception 'La verificación humana no es válida o expiró' using errcode = '42501';
  end if;

  update public.tokens_humanos set usado_en = now() where id = v_token.id;

  v_estado := case
    when coalesce(v_sala.aprobacion_automatica, true) then 'aprobada'::public.estado_reserva
    else 'pendiente'::public.estado_reserva
  end;

  insert into public.reservas (
    sala_id, horario_id, usuario_id, fecha, hora_inicio, hora_fin, estado,
    comentario_usuario, token_humano_id, resuelto_por, resuelto_en,
    nombre_contacto, correo_contacto, telefono_contacto, sesion_hash, origen
  ) values (
    p_sala_id, v_horario_id, null, p_fecha, p_hora_inicio, v_fin, v_estado,
    v_comentario, v_token.id, null,
    case when v_estado = 'aprobada' then now() end,
    v_nombre, v_correo, v_telefono, v_sesion_hash, 'anonima'
  ) returning id into v_id;

  perform privado.registrar_evento(
    v_id,
    case when v_estado = 'aprobada' then 'aprobada'::public.accion_reserva
         else 'solicitada'::public.accion_reserva end,
    jsonb_build_object(
      'fecha', p_fecha,
      'hora_inicio', p_hora_inicio,
      'hora_fin', v_fin,
      'duracion_min', v_duracion,
      'automatica', v_estado = 'aprobada',
      'anonima', true,
      'correo', v_correo
    )
  );

  return jsonb_build_object(
    'reserva_id', v_id,
    'codigo', upper(substr(v_id::text, 1, 8)),
    'estado', v_estado,
    'fecha', p_fecha,
    'hora_inicio', p_hora_inicio,
    'hora_fin', v_fin,
    'correo', v_correo,
    'nombre', coalesce(v_nombre, '')
  );
end;
$$;

revoke all on function public.reservar_franja_anonima(uuid, date, time, int, text, text, text, text, text, text) from public;
grant execute on function public.reservar_franja_anonima(uuid, date, time, int, text, text, text, text, text, text) to anon, authenticated;


-- Reservas del navegador anónimo ---------------------------------------------
create or replace function public.mis_reservas_anonimas(p_sesion text)
returns table (
  reserva_id uuid,
  fecha date,
  hora_inicio time,
  hora_fin time,
  estado public.estado_reserva,
  nombre_contacto text,
  correo_contacto text,
  telefono_contacto text,
  comentario_usuario text,
  comentario_admin text,
  creado_en timestamptz
)
language plpgsql
stable
security definer
set search_path = public, privado
as $$
declare
  v_sesion_hash text;
begin
  if p_sesion is null or p_sesion !~ '^[0-9a-f]{64}$' then
    return;
  end if;
  v_sesion_hash := privado.hash(p_sesion);

  return query
  select r.id,
         r.fecha,
         r.hora_inicio,
         r.hora_fin,
         r.estado,
         r.nombre_contacto,
         r.correo_contacto,
         r.telefono_contacto,
         r.comentario_usuario,
         r.comentario_admin,
         r.creado_en
  from public.reservas r
  where r.sesion_hash = v_sesion_hash
    and r.origen = 'anonima'
  order by r.fecha desc, r.hora_inicio desc;
end;
$$;

revoke all on function public.mis_reservas_anonimas(text) from public;
grant execute on function public.mis_reservas_anonimas(text) to anon, authenticated;

-- Cancelación por el propio visitante ----------------------------------------
create or replace function public.cancelar_reserva_anonima(
  p_reserva_id uuid,
  p_sesion text,
  p_motivo text default null
) returns public.reservas
language plpgsql
security definer
set search_path = public, privado
as $$
declare
  v public.reservas;
  v_sesion_hash text;
begin
  if p_sesion is null or p_sesion !~ '^[0-9a-f]{64}$' then
    raise exception 'La sesión de este navegador no es válida. Recarga la página.' using errcode = '42501';
  end if;
  v_sesion_hash := privado.hash(p_sesion);

  select * into v from public.reservas where id = p_reserva_id for update;
  if not found then
    raise exception 'La reserva no existe';
  end if;
  if v.origen <> 'anonima' or v.sesion_hash is distinct from v_sesion_hash then
    raise exception 'Esta reserva no pertenece a este navegador' using errcode = '42501';
  end if;
  if v.estado not in ('pendiente', 'reprogramada', 'aprobada') then
    raise exception 'La reserva ya está cerrada (estado: %)', v.estado;
  end if;

  update public.reservas
     set estado = 'cancelada',
         comentario_admin = nullif(left(trim(coalesce(p_motivo, '')), 500), ''),
         resuelto_por = null,
         resuelto_en = now(),
         propuesta_fecha = null,
         propuesta_hora_inicio = null,
         propuesta_hora_fin = null
   where id = p_reserva_id
   returning * into v;

  perform privado.registrar_evento(
    p_reserva_id,
    'cancelada',
    jsonb_build_object('por', 'visitante', 'motivo', nullif(trim(coalesce(p_motivo, '')), ''))
  );
  return v;
end;
$$;

revoke all on function public.cancelar_reserva_anonima(uuid, text, text) from public;
grant execute on function public.cancelar_reserva_anonima(uuid, text, text) to anon, authenticated;


-- Cancelación por el administrador -------------------------------------------
create or replace function public.cancelar_reserva_admin(
  p_reserva_id uuid,
  p_motivo text default null
) returns public.reservas
language plpgsql
security definer
set search_path = public, privado
as $$
declare
  v public.reservas;
begin
  if not public.es_admin() then
    raise exception 'Solo un administrador puede cancelar reservas' using errcode = '42501';
  end if;

  select * into v from public.reservas where id = p_reserva_id for update;
  if not found then
    raise exception 'La reserva no existe';
  end if;
  if v.estado not in ('pendiente', 'reprogramada', 'aprobada') then
    raise exception 'La reserva ya está cerrada (estado: %)', v.estado;
  end if;

  update public.reservas
     set estado = 'cancelada',
         comentario_admin = coalesce(nullif(left(trim(coalesce(p_motivo, '')), 500), ''), comentario_admin),
         resuelto_por = auth.uid(),
         resuelto_en = now(),
         propuesta_fecha = null,
         propuesta_hora_inicio = null,
         propuesta_hora_fin = null
   where id = p_reserva_id
   returning * into v;

  perform privado.registrar_evento(
    p_reserva_id,
    'cancelada',
    jsonb_build_object('por', 'admin', 'motivo', nullif(trim(coalesce(p_motivo, '')), ''))
  );
  return v;
end;
$$;

revoke all on function public.cancelar_reserva_admin(uuid, text) from public;
grant execute on function public.cancelar_reserva_admin(uuid, text) to authenticated;

-- Agenda del administrador ----------------------------------------------------
-- Las reservas anónimas no tienen fila en `perfiles`; el left join y los coalesce
-- permiten mostrarlas con sus datos de contacto.
drop function if exists public.agenda_admin(int);

create function public.agenda_admin(p_dias int default 60)
returns table (
  reserva_id uuid,
  fecha date,
  hora_inicio time,
  hora_fin time,
  estado public.estado_reserva,
  usuario_id uuid,
  nombre text,
  telefono text,
  comentario text,
  creado_en timestamptz,
  correo text,
  origen public.origen_reserva
)
language plpgsql
stable
security definer
set search_path = public
as $$
begin
  if not public.es_admin() then
    raise exception 'Solo un administrador puede ver la agenda' using errcode = '42501';
  end if;

  return query
  select r.id,
         r.fecha,
         r.hora_inicio,
         r.hora_fin,
         r.estado,
         r.usuario_id,
         coalesce(nullif(p.nombre_completo, ''), r.nombre_contacto, 'Sin nombre') as nombre,
         coalesce(p.telefono, r.telefono_contacto) as telefono,
         r.comentario_usuario,
         r.creado_en,
         r.correo_contacto,
         r.origen
  from public.reservas r
  left join public.perfiles p on p.id = r.usuario_id
  where r.fecha >= current_date
    and r.fecha <= current_date + greatest(coalesce(p_dias, 60), 1)
  order by r.fecha, r.hora_inicio;
end;
$$;

revoke all on function public.agenda_admin(int) from public, anon;
grant execute on function public.agenda_admin(int) to authenticated;

