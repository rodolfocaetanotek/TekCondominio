import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Perfil } from './auth.models';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

const PERFIL: Perfil = {
  id: 'u1',
  nome: 'Ana Síndica',
  email: 'ana@x.com',
  administradorPlataforma: false,
  idAdministradora: 'org1',
  nomeAdministradora: 'Adm',
  permissoesAdministradora: ['AUDIT_VIEW'],
  permissoesCondominio: { c1: ['CONDOMINIUM_VIEW', 'CONDOMINIUM_EDIT'] },
};

describe('AuthService + authInterceptor', () => {
  let auth: AuthService;
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'login', children: [] }]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  function entrar(): void {
    auth.login('ana@x.com', 'segredo123456').subscribe();
    backend.expectOne('/api/v1/auth/login').flush({ accessToken: 'T1', tokenType: 'Bearer', expiraEm: '' });
    backend.expectOne('/api/v1/auth/perfil').flush(PERFIL);
  }

  it('faz login, guarda o token só em memória e carrega o perfil', () => {
    entrar();
    expect(auth.autenticado()).toBe(true);
    expect(auth.accessToken()).toBe('T1');
    expect(localStorage.length).toBe(0);
  });

  it('avalia permissões por organização e por condomínio', () => {
    entrar();
    expect(auth.pode('AUDIT_VIEW')).toBe(true);
    expect(auth.pode('CONDOMINIUM_EDIT')).toBe(false);
    expect(auth.podeEm('CONDOMINIUM_EDIT', 'c1')).toBe(true);
    expect(auth.podeEm('CONDOMINIUM_EDIT', 'c2')).toBe(false);
    expect(auth.podeAlgum('CONDOMINIUM_VIEW')).toBe(true);
  });

  it('anexa o Bearer e, em 401, renova a sessão uma vez e repete a chamada', () => {
    entrar();
    let corpo: unknown;
    http.get('/api/v1/condominios').subscribe((b) => (corpo = b));

    const primeiro = backend.expectOne('/api/v1/condominios');
    expect(primeiro.request.headers.get('Authorization')).toBe('Bearer T1');
    primeiro.flush({}, { status: 401, statusText: 'Unauthorized' });

    const renovar = backend.expectOne('/api/v1/auth/refresh');
    expect(renovar.request.headers.get('X-TekCondo-Client')).toBe('web');
    expect(renovar.request.headers.has('Authorization')).toBe(false);
    renovar.flush({ accessToken: 'T2', tokenType: 'Bearer', expiraEm: '' });

    const tentarNovamente = backend.expectOne('/api/v1/condominios');
    expect(tentarNovamente.request.headers.get('Authorization')).toBe('Bearer T2');
    tentarNovamente.flush({ ok: true });
    expect(corpo).toEqual({ ok: true });
  });

  it('encerra a sessão quando a renovação falha', () => {
    entrar();
    http.get('/api/v1/condominios').subscribe({ error: () => undefined });
    backend.expectOne('/api/v1/condominios').flush({}, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne('/api/v1/auth/refresh').flush({ codigo: 'SESSION_EXPIRED' }, { status: 401, statusText: 'Unauthorized' });

    expect(auth.autenticado()).toBe(false);
    expect(auth.accessToken()).toBeNull();
  });
});
