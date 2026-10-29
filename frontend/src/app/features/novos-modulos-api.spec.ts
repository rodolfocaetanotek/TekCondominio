import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { FinanceiroApi } from './financeiro/financeiro-api';
import { ReservaApi } from './reservas/reserva-api';
import { PortariaApi } from './portaria/portaria-api';

/** Contrato HTTP dos módulos de fechamento, conciliação OFX, retorno CNAB, reservas e portaria. */
describe('APIs dos novos módulos', () => {
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('fecha e reabre o período com justificativa', () => {
    const api = TestBed.inject(FinanceiroApi);
    api.fechar('c1', '2026-09-30', 'Conferido').subscribe();
    const fechamento = backend.expectOne('/api/v1/condominios/c1/financeiro/fechamento');
    expect(fechamento.request.method).toBe('POST');
    expect(fechamento.request.body).toEqual({ ate: '2026-09-30', observacoes: 'Conferido' });
    fechamento.flush({ historico: [] });

    api.reabrir('c1', null, 'Estorno solicitado').subscribe();
    const reabrir = backend.expectOne('/api/v1/condominios/c1/financeiro/fechamento/reabrir');
    expect(reabrir.request.body).toEqual({ ate: null, motivo: 'Estorno solicitado' });
    reabrir.flush({ historico: [] });
  });

  it('envia o extrato OFX como multipart e permite ignorar a checagem de conta', () => {
    const api = TestBed.inject(FinanceiroApi);
    const arquivo = new File(['<OFX>'], 'extrato.ofx');
    api.importarExtrato('c1', 'a1', arquivo, true).subscribe();
    const req = backend.expectOne((r) => r.url === '/api/v1/condominios/c1/financeiro/contas-bancarias/a1/extratos');
    expect(req.request.params.get('ignorarConferenciaConta')).toBe('true');
    expect((req.request.body as FormData).get('arquivo')).toBeInstanceOf(File);
    req.flush({});

    api.conciliarLinha('c1', 'l1', 'PAYMENT', 'p1').subscribe();
    const conciliar = backend.expectOne('/api/v1/condominios/c1/financeiro/linhas-extrato/l1/conciliar');
    expect(conciliar.request.body).toEqual({ tipo: 'PAYMENT', idMovimentacao: 'p1' });
    conciliar.flush({});
  });

  it('processa retorno CNAB na conta escolhida', () => {
    const api = TestBed.inject(FinanceiroApi);
    api.processarRetorno('c1', 'a1', new File(['x'], 'COB.RET')).subscribe();
    const req = backend.expectOne((r) => r.url === '/api/v1/condominios/c1/cobranca/retornos-bancarios');
    expect(req.request.params.get('idContaBancaria')).toBe('a1');
    expect((req.request.body as FormData).has('arquivo')).toBe(true);
    req.flush({});
  });

  it('separa as rotas de reserva da administração e do portal', () => {
    const api = TestBed.inject(ReservaApi);
    api.listar('c1', '2026-10-01', '2026-10-31', 'area1').subscribe();
    const listar = backend.expectOne((r) => r.url === '/api/v1/condominios/c1/reservas');
    expect(listar.request.params.get('idArea')).toBe('area1');
    listar.flush([]);

    api.portalReservar('c1', { idArea: 'area1', idUnidade: 'u1' }).subscribe();
    backend.expectOne('/api/v1/portal/c1/reservas').flush({});
  });

  it('libera entrada pelo código e autoriza pelo portal', () => {
    const api = TestBed.inject(PortariaApi);
    api.registrarEntradaPorCodigo('c1', '123456').subscribe();
    const req = backend.expectOne('/api/v1/condominios/c1/portaria/entrada-por-codigo');
    expect(req.request.body).toEqual({ codigo: '123456' });
    req.flush({});

    api.portalAutorizar('c1', { idUnidade: 'u1', tipo: 'VISITOR', nome: 'Ana' }).subscribe();
    backend.expectOne('/api/v1/portal/c1/visitantes').flush({});
  });
});
