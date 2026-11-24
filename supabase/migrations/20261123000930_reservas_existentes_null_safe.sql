
-- Ajustes de seguridad en funciones existentes -------------------------------
-- Al permitir `usuario_id = null`, las comparaciones `<>` dejaban pasar tokens y
-- reservas anónimas. Se reemplazan por `is distinct from`, que trata null correctamente.

create or replace function public.verificar_reto_humano(
  p_reto_id uuid,
  p_pieza_id int,
  p_duracion_ms int
) returns jsonb
language plpgsql
security definer
set search_path = public, privado
as $$
declare
  v_uid uuid := auth.uid();
  v_reto public.retos_humanos;
  v_token text;
  v_expira timestamptz;
begin
  if v_uid is null then
    raise exception 'Debes iniciar sesión' using errcode = '28000';
  end if;

  select * into v_reto from public.retos_humanos where id = p_reto_id for update;
  if not found then
    raise exception 'El reto no existe';
  end if;
  if v_reto.usuario_id is distinct from v_uid then
    raise exception 'Este reto pertenece a otro usuario' using errcode = '42501';
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

  insert into public.tokens_humanos (usuario_id, token_hash, expira_en)
  values (v_uid, privado.hash(v_token), v_expira);

  update public.retos_humanos set resuelto_en = now() where id = p_reto_id;

  return jsonb_build_object('token', v_token, 'expira_en', v_expira);
end;
$$;


create or replace function public.solicitar_reserva(
  p_horario_id uuid,
  p_comentario text default null,
  p_token_humano text default null
) returns uuid
language plpgsql
security definer
set search_path = public, privado
as $$
declare
  v_uid uuid := auth.uid();
  v_horario public.horarios;
  v_sala public.salas;
  v_token public.tokens_humanos;
  v_id uuid;
begin
  if v_uid is null then
    raise exception 'Debes iniciar sesión para reservar' using errcode = '28000';
  end if;

  if not exists (select 1 from public.perfiles p where p.id = v_uid and p.estado = 'activo') then
    raise exception 'Tu usuario está bloqueado, contacta al administrador' using errcode = '42501';
  end if;

  select * into v_horario from public.horarios where id = p_horario_id for update;
  if not found then
    raise exception 'El horario no existe';
  end if;

  select * into v_sala from public.salas where id = v_horario.sala_id;
  if not v_sala.calendario_abierto then
    raise exception 'El calendario del taller está cerrado';
  end if;
  if v_horario.estado <> 'disponible' then
    raise exception 'Ese horario no está disponible';
  end if;

  perform privado.validar_franja(
    v_horario.sala_id, v_horario.id, v_horario.fecha, v_horario.hora_inicio, v_horario.hora_fin);

  if exists (
    select 1 from public.reservas r
    where r.usuario_id = v_uid and r.fecha = v_horario.fecha
      and r.estado in ('pendiente', 'aprobada')
  ) then
    raise exception 'Ya tienes una reserva o solicitud para ese día';
  end if;

  if p_token_humano is null or length(p_token_humano) < 32 then
    raise exception 'Falta la verificación humana' using errcode = '42501';
  end if;

  select * into v_token from public.tokens_humanos t
  where t.token_hash = privado.hash(p_token_humano) for update;

  if not found or v_token.usado_en is not null
     or v_token.expira_en < now()
     or v_token.usuario_id is distinct from v_uid then
    raise exception 'La verificación humana no es válida o expiró' using errcode = '42501';
  end if;

  update public.tokens_humanos set usado_en = now() where id = v_token.id;

  insert into public.reservas (
    sala_id, horario_id, usuario_id, fecha, hora_inicio, hora_fin, comentario_usuario, token_humano_id
  ) values (
    v_horario.sala_id, v_horario.id, v_uid, v_horario.fecha, v_horario.hora_inicio,
    v_horario.hora_fin, p_comentario, v_token.id
  ) returning id into v_id;

  perform privado.registrar_evento(
    v_id, 'solicitada',
    jsonb_build_object('fecha', v_horario.fecha, 'hora_inicio', v_horario.hora_inicio,
                       'hora_fin', v_horario.hora_fin));

  return v_id;
exception
  when unique_violation then
    raise exception 'Ya existe una solicitud tuya para ese horario';
end;
$$;


create or replace function public.reservar_franja(
  p_sala_id uuid,
  p_fecha date,
  p_hora_inicio time,
  p_duracion_min int default null,
  p_comentario text default null,
  p_token_humano text default null
) returns uuid
language plpgsql
security definer
set search_path = public, privado
as $$
declare
  v_uid uuid := auth.uid();
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
  if v_uid is null then
    raise exception 'Debes iniciar sesión para reservar' using errcode = '28000';
  end if;

  if not exists (select 1 from public.perfiles p where p.id = v_uid and p.estado = 'activo') then
    raise exception 'Tu usuario está bloqueado, contacta al administrador' using errcode = '42501';
  end if;

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
    where r.usuario_id = v_uid
      and r.fecha = p_fecha
      and r.estado in ('pendiente', 'aprobada');

    if v_del_dia >= v_sala.max_reservas_por_dia then
      raise exception 'Ya tienes el máximo de reservas para ese día';
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
     or v_token.usuario_id is distinct from v_uid then
    raise exception 'La verificación humana no es válida o expiró' using errcode = '42501';
  end if;

  update public.tokens_humanos set usado_en = now() where id = v_token.id;

  v_estado := case
    when coalesce(v_sala.aprobacion_automatica, true) then 'aprobada'::public.estado_reserva
    else 'pendiente'::public.estado_reserva
  end;

  insert into public.reservas (
    sala_id, horario_id, usuario_id, fecha, hora_inicio, hora_fin, estado,
    comentario_usuario, token_humano_id, resuelto_por, resuelto_en
  ) values (
    p_sala_id, v_horario_id, v_uid, p_fecha, p_hora_inicio, v_fin, v_estado,
    nullif(trim(coalesce(p_comentario, '')), ''), v_token.id,
    case when v_estado = 'aprobada' then v_uid end,
    case when v_estado = 'aprobada' then now() end
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
      'automatica', v_estado = 'aprobada'));

  return v_id;
end;
$$;

