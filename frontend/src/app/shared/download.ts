import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, map } from 'rxjs';

/**
 * Baixa um arquivo da API (com o token de acesso, via interceptor) e oferece ao usuário.
 * Documentos nunca têm URL pública — sempre passam por aqui.
 */
export function downloadFile(http: HttpClient, url: string, params?: Record<string, string>): Observable<void> {
  return http.get(url, { params, responseType: 'blob', observe: 'response' }).pipe(
    map((res: HttpResponse<Blob>) => {
      const disposition = res.headers.get('Content-Disposition') ?? '';
      const match = /filename\*=UTF-8''([^;]+)/.exec(disposition) ?? /filename="?([^";]+)"?/.exec(disposition);
      const name = match ? decodeURIComponent(match[1]) : 'arquivo';
      const href = URL.createObjectURL(res.body!);
      const a = document.createElement('a');
      a.href = href;
      a.download = name;
      a.click();
      setTimeout(() => URL.revokeObjectURL(href), 1000);
    }),
  );
}
