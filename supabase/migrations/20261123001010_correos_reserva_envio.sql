
-- Envío ------------------------------------------------------------------------
create or replace function privado.enviar_correo(
  p_destinatario text,
  p_asunto text,
  p_html text,
  p_reserva_id uuid default null,
  p_tipo text default 'manual'
) returns bigint
language plpgsql
security definer
set search_path = public, privado
as $$
declare
  v_proveedor text := lower(coalesce(nullif(trim(privado.secreto('correo_proveedor')), ''), 'brevo'));
  v_remitente text := nullif(trim(coalesce(privado.secreto('correo_remitente'), '')), '');
  v_nombre text := coalesce(nullif(trim(privado.secreto('correo_remitente_nombre')), ''),
                            'Taller Barro Negro');
  v_copia text := nullif(trim(coalesce(privado.secreto('correo_copia'), '')), '');
  v_api_key text := privado.correo_api_key();
  v_destinatario text := lower(trim(coalesce(p_destinatario, '')));
  v_log_id bigint;
  v_request bigint;
  v_url text;
  v_body jsonb;
  v_headers jsonb;
begin
  if v_destinatario !~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$' then
    raise exception 'Destinatario de correo inválido';
  end if;

  insert into privado.correos_log (
    reserva_id, tipo, destinatario, asunto, html, proveedor
  ) values (
    p_reserva_id, coalesce(p_tipo, 'manual'), v_destinatario, p_asunto, p_html, v_proveedor
  ) returning id into v_log_id;

  if v_remitente is null then
    update privado.correos_log
       set estado = 'error', ultimo_error = 'Falta configurar correo_remitente'
     where id = v_log_id;
    return null;
  end if;

  if v_api_key is null or length(v_api_key) < 10 then
    update privado.correos_log
       set estado = 'error', ultimo_error = 'Falta la API key del correo en Vault'
     where id = v_log_id;
    return null;
  end if;

  if v_proveedor = 'brevo' then
    v_url := 'https://api.brevo.com/v3/smtp/email';
    v_body := jsonb_build_object(
      'sender', jsonb_build_object('email', v_remitente, 'name', v_nombre),
      'to', jsonb_build_array(jsonb_build_object('email', v_destinatario)),
      'subject', p_asunto,
      'htmlContent', p_html
    );
    if v_copia is not null then
      v_body := v_body || jsonb_build_object(
        'bcc', jsonb_build_array(jsonb_build_object('email', v_copia))
      );
    end if;
    v_headers := jsonb_build_object(
      'api-key', v_api_key,
      'content-type', 'application/json',
      'accept', 'application/json'
    );
  elsif v_proveedor = 'resend' then
    v_url := 'https://api.resend.com/emails';
    v_body := jsonb_build_object(
      'from', v_nombre || ' <' || v_remitente || '>',
      'to', jsonb_build_array(v_destinatario),
      'subject', p_asunto,
      'html', p_html
    );
    if v_copia is not null then
      v_body := v_body || jsonb_build_object('bcc', jsonb_build_array(v_copia));
    end if;
    v_headers := jsonb_build_object(
      'Authorization', 'Bearer ' || v_api_key,
      'content-type', 'application/json'
    );
  else
    update privado.correos_log
       set estado = 'error', ultimo_error = 'Proveedor no soportado: ' || v_proveedor
     where id = v_log_id;
    return null;
  end if;

  begin
    v_request := privado.http_post(v_url, v_body, v_headers, 10000);
    update privado.correos_log
       set request_id = v_request, estado = 'solicitado'
     where id = v_log_id;
    return v_request;
  exception
    when others then
      update privado.correos_log
         set estado = 'error', ultimo_error = sqlerrm
       where id = v_log_id;
      return null;
  end;
end;
$$;

create or replace function privado.enviar_correo_reserva(
  p_reserva_id uuid,
  p_tipo text
) returns bigint
language plpgsql
security definer
set search_path = public, privado
as $$
declare
  v_destinatario text;
  v_asunto text;
  v_html text;
begin
  select c.destinatario, c.asunto, c.html
    into v_destinatario, v_asunto, v_html
  from privado.correo_reserva(p_reserva_id, p_tipo) c;

  if v_destinatario is null then
    return null;
  end if;

  return privado.enviar_correo(
    v_destinatario, v_asunto, v_html, p_reserva_id, p_tipo
  );
end;
$$;


-- Disparadores -----------------------------------------------------------------
create or replace function privado.notificar_reserva()
returns trigger
language plpgsql
security definer
set search_path = public, privado
as $$
declare
  v_tipo text;
begin
  if new.correo_contacto is null then
    return new;
  end if;

  if tg_op = 'INSERT' then
    v_tipo := case
      when new.estado = 'pendiente' then 'solicitud'
      else 'confirmacion'
    end;
    perform privado.enviar_correo_reserva(new.id, v_tipo);
    return new;
  end if;

  if new.estado = old.estado then
    return new;
  end if;

  v_tipo := case new.estado
    when 'aprobada' then 'confirmacion'
    when 'rechazada' then 'rechazo'
    when 'cancelada' then 'cancelacion'
    else null
  end;

  if v_tipo is not null then
    perform privado.enviar_correo_reserva(new.id, v_tipo);
  end if;

  return new;
exception
  when others then
    raise warning 'No se pudo preparar el correo de la reserva %: %', new.id, sqlerrm;
    return new;
end;
$$;

drop trigger if exists reservas_correo_insert on public.reservas;
create trigger reservas_correo_insert
  after insert on public.reservas
  for each row execute function privado.notificar_reserva();

drop trigger if exists reservas_correo_update on public.reservas;
create trigger reservas_correo_update
  after update of estado on public.reservas
  for each row execute function privado.notificar_reserva();

-- Reenvío manual desde el panel -------------------------------------------------
create or replace function public.reenviar_correo_reserva(
  p_reserva_id uuid,
  p_tipo text default null
) returns bigint
language plpgsql
security definer
set search_path = public, privado
as $$
declare
  v public.reservas;
  v_tipo text;
begin
  if not public.es_admin() then
    raise exception 'Solo un administrador puede reenviar correos' using errcode = '42501';
  end if;

  select * into v from public.reservas where id = p_reserva_id;
  if not found then
    raise exception 'La reserva no existe';
  end if;
  if coalesce(trim(v.correo_contacto), '') = '' then
    raise exception 'Esta reserva no tiene correo de contacto';
  end if;

  v_tipo := coalesce(
    nullif(trim(p_tipo), ''),
    case v.estado
      when 'pendiente' then 'solicitud'
      when 'aprobada' then 'confirmacion'
      when 'rechazada' then 'rechazo'
      when 'cancelada' then 'cancelacion'
      else 'confirmacion'
    end
  );

  return privado.enviar_correo_reserva(p_reserva_id, v_tipo);
end;
$$;

revoke all on function public.reenviar_correo_reserva(uuid, text) from public, anon;
grant execute on function public.reenviar_correo_reserva(uuid, text) to authenticated;

-- Prueba de configuración -------------------------------------------------------
create or replace function privado.probar_correo(p_destinatario text)
returns bigint
language plpgsql
security definer
set search_path = public, privado
as $$
declare
  v_html text;
begin
  v_html :=
    '<div style="font-family:Arial,Helvetica,sans-serif;max-width:560px;margin:0 auto;color:#33140a">' ||
    '<h2 style="color:#8a4b2a;margin:0 0 12px">Prueba de correo</h2>' ||
    '<p>Si recibes este mensaje, el taller ya puede enviar confirmaciones, avisos y cancelaciones.</p>' ||
    '<p style="font-size:13px;color:#556b5d">Taller Barro Negro</p></div>';

  return privado.enviar_correo(
    p_destinatario,
    'Prueba de correo · Taller de cerámica',
    v_html,
    null,
    'manual'
  );
end;
$$;

-- Las funciones privadas no se exponen por la API.
revoke all on function privado.configurar_correo(text, text, text, text) from public;
revoke all on function privado.configurar_api_key_correo(text) from public;
revoke all on function privado.probar_correo(text) from public;

