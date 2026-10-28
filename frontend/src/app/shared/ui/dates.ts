/** Data local de hoje no formato ISO (AAAA-MM-DD), sem conversão de fuso. */
export function todayIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** Converte AAAA-MM-DD para DD/MM/AAAA sem passar por Date (evita deslocamento de fuso). */
export function formatIsoDate(iso: string | null | undefined): string {
  if (!iso) return '—';
  const [y, m, d] = iso.slice(0, 10).split('-');
  return `${d}/${m}/${y}`;
}
