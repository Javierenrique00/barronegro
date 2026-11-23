-- 0004 · Storage: avatares, adjuntos de reservas y galería del taller
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values
  ('avatares', 'avatares', true, 5242880, array['image/jpeg', 'image/png', 'image/webp']),
  ('reservas-adjuntos', 'reservas-adjuntos', false, 5242880, array['image/jpeg', 'image/png', 'image/webp']),
  ('taller-fotos', 'taller-fotos', true, 10485760, array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do update
  set public = excluded.public,
      file_size_limit = excluded.file_size_limit,
      allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists avatares_lectura on storage.objects;
drop policy if exists avatares_escritura on storage.objects;
drop policy if exists avatares_actualizacion on storage.objects;
drop policy if exists avatares_borrado on storage.objects;
drop policy if exists adjuntos_storage_lectura on storage.objects;
drop policy if exists adjuntos_storage_escritura on storage.objects;
drop policy if exists adjuntos_storage_borrado on storage.objects;
drop policy if exists taller_fotos_lectura on storage.objects;
drop policy if exists taller_fotos_escritura on storage.objects;
drop policy if exists taller_fotos_borrado on storage.objects;

create policy avatares_lectura on storage.objects
  for select to public using (bucket_id = 'avatares');
create policy avatares_escritura on storage.objects
  for insert to authenticated
  with check (bucket_id = 'avatares' and (storage.foldername(name))[1] = auth.uid()::text);
create policy avatares_actualizacion on storage.objects
  for update to authenticated
  using (bucket_id = 'avatares' and (storage.foldername(name))[1] = auth.uid()::text);
create policy avatares_borrado on storage.objects
  for delete to authenticated
  using (bucket_id = 'avatares' and (storage.foldername(name))[1] = auth.uid()::text);

create policy adjuntos_storage_lectura on storage.objects
  for select to authenticated
  using (bucket_id = 'reservas-adjuntos'
         and ((storage.foldername(name))[1] = auth.uid()::text or public.es_admin()));
create policy adjuntos_storage_escritura on storage.objects
  for insert to authenticated
  with check (bucket_id = 'reservas-adjuntos'
              and (storage.foldername(name))[1] = auth.uid()::text);
create policy adjuntos_storage_borrado on storage.objects
  for delete to authenticated
  using (bucket_id = 'reservas-adjuntos'
         and ((storage.foldername(name))[1] = auth.uid()::text or public.es_admin()));

create policy taller_fotos_lectura on storage.objects
  for select to public using (bucket_id = 'taller-fotos');
create policy taller_fotos_escritura on storage.objects
  for insert to authenticated
  with check (bucket_id = 'taller-fotos' and public.es_admin());
create policy taller_fotos_borrado on storage.objects
  for delete to authenticated
  using (bucket_id = 'taller-fotos' and public.es_admin());
