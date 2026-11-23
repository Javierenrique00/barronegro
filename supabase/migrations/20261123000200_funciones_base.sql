-- 0002 · Funciones, triggers y reglas de negocio
create or replace function public.tocar_actualizado_en() returns trigger
language plpgsql as $$
begin
  new.actualizado_en := now();
  return new;
end;
$$;

create trigger salas_actualizado before update on public.salas for each row execute function public.tocar_actualizado_en();
create trigger perfiles_actualizado before update on public.perfiles for each row execute function public.tocar_actualizado_en();
create trigger horarios_actualizado before update on public.horarios for each row execute function public.tocar_actualizado_en();
create trigger reservas_actualizado before update on public.reservas for each row execute function public.tocar_actualizado_en();

create or replace function public.a_timestamptz(p_sala_id uuid, p_fecha date, p_hora time)
returns timestamptz
language sql stable set search_path = public as $$
  select ((p_fecha + p_hora) at time zone coalesce(
    (select s.zona_horaria from public.salas s where s.id = p_sala_id), 'America/Bogota'));
$$;

create or replace function public.es_admin() returns boolean
language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.perfiles p
    where p.id = auth.uid() and p.rol = 'admin' and p.estado = 'activo'
  );
$$;

create or replace function public.esta_activo() returns boolean
language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.perfiles p where p.id = auth.uid() and p.estado = 'activo'
  );
$$;

create or replace function public.manejar_nuevo_usuario() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  insert into public.perfiles (id, nombre_completo)
  values (
    new.id,
    coalesce(nullif(trim(new.raw_user_meta_data ->> 'nombre_completo'), ''),
             split_part(coalesce(new.email, ''), '@', 1))
  )
  on conflict (id) do nothing;
  return new;
end;
$$;

create trigger on_auth_user_created
after insert on auth.users
for each row execute function public.manejar_nuevo_usuario();

-- Helpers privados (no expuestos por la API) -------------------------------
create or replace function privado.secreto(p_nombre text) returns text
language sql stable security definer set search_path = privado as $$
  select s.valor from privado.secretos s where s.nombre = p_nombre;
$$;

create or replace function privado.hash(p_valor text) returns text
language sql immutable as $$
  select encode(extensions.digest(p_valor, 'sha256'), 'hex');
$$;

create or replace function privado.firma(p_reto uuid, p_respuesta int, p_nonce text) returns text
language sql stable security definer set search_path = privado as $$
  select encode(
    extensions.hmac(p_reto::text || ':' || p_respuesta::text || ':' || p_nonce,
                    privado.secreto('reto_humano'), 'sha256'),
    'hex');
$$;

create or replace function privado.registrar_evento(
  p_reserva_id uuid, p_accion public.accion_reserva, p_datos jsonb default '{}'::jsonb
) returns void
language sql security definer set search_path = public as $$
  insert into public.reserva_eventos (reserva_id, actor_id, accion, datos)
  values (p_reserva_id, auth.uid(), p_accion, coalesce(p_datos, '{}'::jsonb));
$$;

create or replace function privado.validar_franja(
  p_sala_id uuid,
  p_horario_id uuid,
  p_fecha date,
  p_hora_inicio time,
  p_hora_fin time,
  p_reserva_id uuid default null
) returns void
language plpgsql stable security definer set search_path = public as $$
declare
  v_inicio timestamptz;
  v_fin timestamptz;
  v_capacidad int;
  v_ocupadas int;
begin
  if p_hora_fin <= p_hora_inicio then
    raise exception 'La hora final debe ser posterior a la hora inicial';
  end if;

  v_inicio := public.a_timestamptz(p_sala_id, p_fecha, p_hora_inicio);
  v_fin := public.a_timestamptz(p_sala_id, p_fecha, p_hora_fin);

  if v_inicio <= now() then
    raise exception 'Esa franja ya pasó, elige una fecha futura';
  end if;

  if exists (
    select 1 from public.bloqueos_sala b
    where b.sala_id = p_sala_id
      and tstzrange(b.desde, b.hasta) && tstzrange(v_inicio, v_fin)
  ) then
    raise exception 'La sala está bloqueada en esa franja';
  end if;

  select coalesce(
    (select h.capacidad from public.horarios h where h.id = p_horario_id),
    (select s.capacidad_por_turno from public.salas s where s.id = p_sala_id),
    1) into v_capacidad;

  select count(*) into v_ocupadas
  from public.reservas r
  where r.sala_id = p_sala_id
    and r.estado = 'aprobada'
    and (p_reserva_id is null or r.id <> p_reserva_id)
    and tstzrange(
          public.a_timestamptz(r.sala_id, r.fecha, r.hora_inicio),
          public.a_timestamptz(r.sala_id, r.fecha, r.hora_fin)
        ) && tstzrange(v_inicio, v_fin);

  if v_ocupadas >= v_capacidad then
    raise exception 'Esa franja ya está completa';
  end if;
end;
$$;

