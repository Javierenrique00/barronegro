-- Reservas ----------------------------------------------------------------
create or replace function public.solicitar_reserva(
  p_horario_id uuid,
  p_comentario text default null,
  p_token_humano text default null
) returns uuid
language plpgsql security definer set search_path = public, privado as $$
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
     or v_token.expira_en < now() or v_token.usuario_id <> v_uid then
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

create or replace function public.aprobar_reserva(p_reserva_id uuid)
returns public.reservas
language plpgsql security definer set search_path = public, privado as $$
declare
  v public.reservas;
begin
  if not public.es_admin() then
    raise exception 'Solo un administrador puede aprobar reservas' using errcode = '42501';
  end if;

  select * into v from public.reservas where id = p_reserva_id for update;
  if not found then
    raise exception 'La reserva no existe';
  end if;
  if v.estado not in ('pendiente', 'reprogramada') then
    raise exception 'Solo se aprueban reservas pendientes o reprogramadas (estado actual: %)', v.estado;
  end if;

  perform privado.validar_franja(
    v.sala_id, v.horario_id,
    coalesce(v.propuesta_fecha, v.fecha),
    coalesce(v.propuesta_hora_inicio, v.hora_inicio),
    coalesce(v.propuesta_hora_fin, v.hora_fin),
    v.id);

  update public.reservas
     set fecha = coalesce(propuesta_fecha, fecha),
         hora_inicio = coalesce(propuesta_hora_inicio, hora_inicio),
         hora_fin = coalesce(propuesta_hora_fin, hora_fin),
         estado = 'aprobada',
         resuelto_por = auth.uid(),
         resuelto_en = now(),
         propuesta_fecha = null,
         propuesta_hora_inicio = null,
         propuesta_hora_fin = null
   where id = p_reserva_id
   returning * into v;

  perform privado.registrar_evento(
    p_reserva_id, 'aprobada',
    jsonb_build_object('fecha', v.fecha, 'hora_inicio', v.hora_inicio, 'hora_fin', v.hora_fin));

  return v;
end;
$$;

create or replace function public.rechazar_reserva(p_reserva_id uuid, p_motivo text)
returns public.reservas
language plpgsql security definer set search_path = public, privado as $$
declare
  v public.reservas;
begin
  if not public.es_admin() then
    raise exception 'Solo un administrador puede rechazar reservas' using errcode = '42501';
  end if;

  select * into v from public.reservas where id = p_reserva_id for update;
  if not found then
    raise exception 'La reserva no existe';
  end if;
  if v.estado not in ('pendiente', 'reprogramada') then
    raise exception 'Solo se rechazan reservas pendientes o reprogramadas (estado actual: %)', v.estado;
  end if;

  update public.reservas
     set estado = 'rechazada',
         comentario_admin = coalesce(p_motivo, comentario_admin),
         resuelto_por = auth.uid(),
         resuelto_en = now(),
         propuesta_fecha = null,
         propuesta_hora_inicio = null,
         propuesta_hora_fin = null
   where id = p_reserva_id
   returning * into v;

  perform privado.registrar_evento(
    p_reserva_id, 'rechazada', jsonb_build_object('motivo', p_motivo));

  return v;
end;
$$;

create or replace function public.proponer_reprogramacion(
  p_reserva_id uuid, p_fecha date, p_hora_inicio time, p_hora_fin time, p_motivo text default null
) returns public.reservas
language plpgsql security definer set search_path = public, privado as $$
declare
  v public.reservas;
begin
  if not public.es_admin() then
    raise exception 'Solo un administrador puede proponer una nueva fecha' using errcode = '42501';
  end if;

  select * into v from public.reservas where id = p_reserva_id for update;
  if not found then
    raise exception 'La reserva no existe';
  end if;
  if v.estado not in ('pendiente', 'reprogramada') then
    raise exception 'Solo se reprograma una reserva pendiente (estado actual: %)', v.estado;
  end if;

  perform privado.validar_franja(v.sala_id, v.horario_id, p_fecha, p_hora_inicio, p_hora_fin, v.id);

  update public.reservas
     set estado = 'reprogramada',
         propuesta_fecha = p_fecha,
         propuesta_hora_inicio = p_hora_inicio,
         propuesta_hora_fin = p_hora_fin,
         comentario_admin = coalesce(p_motivo, comentario_admin),
         resuelto_por = null,
         resuelto_en = null
   where id = p_reserva_id
   returning * into v;

  perform privado.registrar_evento(
    p_reserva_id, 'reprogramada',
    jsonb_build_object('fecha', p_fecha, 'hora_inicio', p_hora_inicio, 'hora_fin', p_hora_fin,
                       'motivo', p_motivo));

  return v;
end;
$$;

create or replace function public.responder_propuesta(p_reserva_id uuid, p_aceptar boolean)
returns public.reservas
language plpgsql security definer set search_path = public, privado as $$
declare
  v public.reservas;
begin
  if auth.uid() is null then
    raise exception 'Debes iniciar sesión' using errcode = '28000';
  end if;

  select * into v from public.reservas where id = p_reserva_id for update;
  if not found then
    raise exception 'La reserva no existe';
  end if;
  if v.usuario_id <> auth.uid() then
    raise exception 'Esta reserva no te pertenece' using errcode = '42501';
  end if;
  if v.estado <> 'reprogramada' then
    raise exception 'La reserva no tiene una propuesta pendiente';
  end if;

  if p_aceptar then
    perform privado.validar_franja(
      v.sala_id, v.horario_id, v.propuesta_fecha, v.propuesta_hora_inicio, v.propuesta_hora_fin, v.id);

    update public.reservas
       set fecha = v.propuesta_fecha,
           hora_inicio = v.propuesta_hora_inicio,
           hora_fin = v.propuesta_hora_fin,
           estado = 'aprobada',
           resuelto_por = auth.uid(),
           resuelto_en = now(),
           propuesta_fecha = null,
           propuesta_hora_inicio = null,
           propuesta_hora_fin = null
     where id = p_reserva_id
     returning * into v;

    perform privado.registrar_evento(p_reserva_id, 'propuesta_aceptada', '{}'::jsonb);
  else
    update public.reservas
       set estado = 'cancelada',
           comentario_usuario = coalesce(comentario_usuario, 'Propuesta rechazada por el usuario'),
           resuelto_por = auth.uid(),
           resuelto_en = now(),
           propuesta_fecha = null,
           propuesta_hora_inicio = null,
           propuesta_hora_fin = null
     where id = p_reserva_id
     returning * into v;

    perform privado.registrar_evento(p_reserva_id, 'propuesta_rechazada', '{}'::jsonb);
  end if;

  return v;
end;
$$;

create or replace function public.cancelar_reserva(p_reserva_id uuid)
returns public.reservas
language plpgsql security definer set search_path = public, privado as $$
declare
  v public.reservas;
begin
  if auth.uid() is null then
    raise exception 'Debes iniciar sesión' using errcode = '28000';
  end if;

  select * into v from public.reservas where id = p_reserva_id for update;
  if not found then
    raise exception 'La reserva no existe';
  end if;
  if v.usuario_id <> auth.uid() and not public.es_admin() then
    raise exception 'Esta reserva no te pertenece' using errcode = '42501';
  end if;
  if v.estado not in ('pendiente', 'reprogramada', 'aprobada') then
    raise exception 'La reserva ya está cerrada (estado: %)', v.estado;
  end if;

  update public.reservas
     set estado = 'cancelada',
         resuelto_por = auth.uid(),
         resuelto_en = now(),
         propuesta_fecha = null,
         propuesta_hora_inicio = null,
         propuesta_hora_fin = null
   where id = p_reserva_id
   returning * into v;

  perform privado.registrar_evento(p_reserva_id, 'cancelada', '{}'::jsonb);
  return v;
end;
$$;

create or replace function public.registrar_adjunto(p_reserva_id uuid, p_ruta text)
returns uuid
language plpgsql security definer set search_path = public as $$
declare
  v_uid uuid := auth.uid();
  v_reserva public.reservas;
  v_id uuid;
begin
  if v_uid is null then
    raise exception 'Debes iniciar sesión' using errcode = '28000';
  end if;

  select * into v_reserva from public.reservas where id = p_reserva_id;
  if not found then
    raise exception 'La reserva no existe';
  end if;
  if v_reserva.usuario_id <> v_uid and not public.es_admin() then
    raise exception 'Esta reserva no te pertenece' using errcode = '42501';
  end if;
  if p_ruta is null or position(v_uid::text in p_ruta) <> 1 then
    raise exception 'La ruta del adjunto debe empezar por tu identificador de usuario';
  end if;

  insert into public.adjuntos (reserva_id, ruta, subido_por)
  values (p_reserva_id, p_ruta, v_uid)
  returning id into v_id;

  return v_id;
end;
$$;

create or replace function public.cambiar_estado_usuario(p_usuario_id uuid, p_estado public.estado_perfil)
returns public.perfiles
language plpgsql security definer set search_path = public as $$
declare
  v public.perfiles;
begin
  if not public.es_admin() then
    raise exception 'Solo un administrador puede cambiar el estado de un usuario' using errcode = '42501';
  end if;
  if p_usuario_id = auth.uid() then
    raise exception 'No puedes cambiar el estado de tu propio usuario';
  end if;

  update public.perfiles set estado = p_estado where id = p_usuario_id returning * into v;
  if v.id is null then
    raise exception 'El usuario no existe';
  end if;
  return v;
end;
$$;

create or replace function public.cambiar_rol_usuario(p_usuario_id uuid, p_rol public.rol_usuario)
returns public.perfiles
language plpgsql security definer set search_path = public as $$
declare
  v public.perfiles;
  v_admins int;
begin
  if not public.es_admin() then
    raise exception 'Solo un administrador puede cambiar roles' using errcode = '42501';
  end if;

  if p_rol = 'usuario' and p_usuario_id = auth.uid() then
    select count(*) into v_admins from public.perfiles p where p.rol = 'admin' and p.estado = 'activo';
    if v_admins <= 1 then
      raise exception 'No puedes quitarte el último rol de administrador';
    end if;
  end if;

  update public.perfiles set rol = p_rol where id = p_usuario_id returning * into v;
  if v.id is null then
    raise exception 'El usuario no existe';
  end if;
  return v;
end;
$$;
