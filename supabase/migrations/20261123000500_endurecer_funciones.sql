-- 0005 · Endurecimiento tras los avisos del linter de seguridad
alter function public.tocar_actualizado_en() set search_path = pg_catalog, public;
alter function privado.hash(text) set search_path = pg_catalog, extensions;

revoke execute on function public.es_admin() from anon;

-- Documentamos que estas tablas son de uso interno: nunca accesibles desde el cliente.
drop policy if exists retos_sin_acceso on public.retos_humanos;
drop policy if exists tokens_sin_acceso on public.tokens_humanos;
create policy retos_sin_acceso on public.retos_humanos
  for all to authenticated using (false) with check (false);
create policy tokens_sin_acceso on public.tokens_humanos
  for all to authenticated using (false) with check (false);
