import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { FinanceApi } from './finance/finance-api';
import { ReservationApi } from './reservations/reservation-api';
import { VisitorApi } from './visitors/visitor-api';

/** Contrato HTTP dos módulos de fechamento, conciliação OFX, retorno CNAB, reservas e portaria. */
describe('APIs dos novos módulos', () => {
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('fecha e reabre o período com justificativa', () => {
    const api = TestBed.inject(FinanceApi);
    api.close('c1', '2026-09-30', 'Conferido').subscribe();
    const close = backend.expectOne('/api/v1/condominiums/c1/finance/closing');
    expect(close.request.method).toBe('POST');
    expect(close.request.body).toEqual({ until: '2026-09-30', notes: 'Conferido' });
    close.flush({ history: [] });

    api.reopen('c1', null, 'Estorno solicitado').subscribe();
    const reopen = backend.expectOne('/api/v1/condominiums/c1/finance/closing/reopen');
    expect(reopen.request.body).toEqual({ until: null, reason: 'Estorno solicitado' });
    reopen.flush({ history: [] });
  });

  it('envia o extrato OFX como multipart e permite ignorar a checagem de conta', () => {
    const api = TestBed.inject(FinanceApi);
    const file = new File(['<OFX>'], 'extrato.ofx');
    api.importStatement('c1', 'a1', file, true).subscribe();
    const req = backend.expectOne((r) => r.url === '/api/v1/condominiums/c1/finance/bank-accounts/a1/statement-imports');
    expect(req.request.params.get('ignoreAccountCheck')).toBe('true');
    expect((req.request.body as FormData).get('file')).toBeInstanceOf(File);
    req.flush({});

    api.matchLine('c1', 'l1', 'PAYMENT', 'p1').subscribe();
    const match = backend.expectOne('/api/v1/condominiums/c1/finance/statement-lines/l1/match');
    expect(match.request.body).toEqual({ type: 'PAYMENT', movementId: 'p1' });
    match.flush({});
  });

  it('processa retorno CNAB na conta escolhida', () => {
    const api = TestBed.inject(FinanceApi);
    api.processReturn('c1', 'a1', new File(['x'], 'COB.RET')).subscribe();
    const req = backend.expectOne((r) => r.url === '/api/v1/condominiums/c1/billing/bank-returns');
    expect(req.request.params.get('bankAccountId')).toBe('a1');
    req.flush({});
  });

  it('separa as rotas de reserva da administração e do portal', () => {
    const api = TestBed.inject(ReservationApi);
    api.list('c1', '2026-10-01', '2026-10-31', 'area1').subscribe();
    const list = backend.expectOne((r) => r.url === '/api/v1/condominiums/c1/reservations');
    expect(list.request.params.get('areaId')).toBe('area1');
    list.flush([]);

    api.portalBook('c1', { areaId: 'area1', unitId: 'u1' }).subscribe();
    backend.expectOne('/api/v1/portal/c1/reservations').flush({});
  });

  it('libera entrada pelo código e autoriza pelo portal', () => {
    const api = TestBed.inject(VisitorApi);
    api.checkInByCode('c1', '123456').subscribe();
    const req = backend.expectOne('/api/v1/condominiums/c1/visitors/check-in-code');
    expect(req.request.body).toEqual({ code: '123456' });
    req.flush({});

    api.portalAuthorize('c1', { unitId: 'u1', kind: 'VISITOR', name: 'Ana' }).subscribe();
    backend.expectOne('/api/v1/portal/c1/visitors').flush({});
  });
});
