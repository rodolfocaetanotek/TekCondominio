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

  function signIn(): void {
    auth.login('ana@x.com', 'segredo123456').subscribe();
    backend.expectOne('/api/v1/auth/login').flush({ accessToken: 'T1', tokenType: 'Bearer', expiraEm: '' });
    backend.expectOne('/api/v1/auth/perfil').flush(PERFIL);
  }

  it('faz login, guarda o token só em memória e carrega o perfil', () => {
    signIn();
    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.accessToken()).toBe('T1');
    expect(localStorage.length).toBe(0);
  });

  it('avalia permissões por organização e por condomínio', () => {
    signIn();
    expect(auth.can('AUDIT_VIEW')).toBe(true);
    expect(auth.can('CONDOMINIUM_EDIT')).toBe(false);
    expect(auth.canIn('CONDOMINIUM_EDIT', 'c1')).toBe(true);
    expect(auth.canIn('CONDOMINIUM_EDIT', 'c2')).toBe(false);
    expect(auth.canAny('CONDOMINIUM_VIEW')).toBe(true);
  });

  it('anexa o Bearer e, em 401, renova a sessão uma vez e repete a chamada', () => {
    signIn();
    let body: unknown;
    http.get('/api/v1/condominiums').subscribe((b) => (body = b));

    const first = backend.expectOne('/api/v1/condominiums');
    expect(first.request.headers.get('Authorization')).toBe('Bearer T1');
    first.flush({}, { status: 401, statusText: 'Unauthorized' });

    const refresh = backend.expectOne('/api/v1/auth/refresh');
    expect(refresh.request.headers.get('X-TekCondo-Client')).toBe('web');
    expect(refresh.request.headers.has('Authorization')).toBe(false);
    refresh.flush({ accessToken: 'T2', tokenType: 'Bearer', expiraEm: '' });

    const retry = backend.expectOne('/api/v1/condominiums');
    expect(retry.request.headers.get('Authorization')).toBe('Bearer T2');
    retry.flush({ ok: true });
    expect(body).toEqual({ ok: true });
  });

  it('encerra a sessão quando a renovação falha', () => {
    signIn();
    http.get('/api/v1/condominiums').subscribe({ error: () => undefined });
    backend.expectOne('/api/v1/condominiums').flush({}, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne('/api/v1/auth/refresh').flush({ codigo: 'SESSION_EXPIRED' }, { status: 401, statusText: 'Unauthorized' });

    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.accessToken()).toBeNull();
  });
});
