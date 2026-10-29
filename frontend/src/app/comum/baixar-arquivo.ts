import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, map } from 'rxjs';

/**
 * Baixa um arquivo da API (com o token de acesso, via interceptor) e oferece ao usuário.
 * Documentos nunca têm URL pública — sempre passam por aqui.
 */
export function baixarArquivo(http: HttpClient, url: string, parametros?: Record<string, string>): Observable<void> {
  return http.get(url, { params: parametros, responseType: 'blob', observe: 'response' }).pipe(
    map((res: HttpResponse<Blob>) => {
      const disposicao = res.headers.get('Content-Disposition') ?? '';
      const correspondencia = /filename\*=UTF-8''([^;]+)/.exec(disposicao) ?? /filename="?([^";]+)"?/.exec(disposicao);
      const nome = correspondencia ? decodeURIComponent(correspondencia[1]) : 'arquivo';
      const href = URL.createObjectURL(res.body!);
      const a = document.createElement('a');
      a.href = href;
      a.download = nome;
      a.click();
      setTimeout(() => URL.revokeObjectURL(href), 1000);
    }),
  );
}
