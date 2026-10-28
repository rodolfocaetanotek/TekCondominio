import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ViaCepAddress, ViaCepService } from './via-cep';

describe('ViaCepService', () => {
  let service: ViaCepService;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(ViaCepService);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('converte a resposta do ViaCEP', () => {
    let result: ViaCepAddress | null = null;
    service.find('01310-100').subscribe((r) => (result = r));
    backend
      .expectOne('https://viacep.com.br/ws/01310100/json/')
      .flush({ logradouro: 'Avenida Paulista', bairro: 'Bela Vista', localidade: 'São Paulo', uf: 'SP' });
    expect(result).toEqual({ street: 'Avenida Paulista', district: 'Bela Vista', city: 'São Paulo', state: 'SP' });
  });

  it('retorna null para CEP inexistente ou falha', () => {
    const results: (ViaCepAddress | null)[] = [];
    service.find('99999999').subscribe((r) => results.push(r));
    backend.expectOne('https://viacep.com.br/ws/99999999/json/').flush({ erro: 'true' });
    service.find('88888888').subscribe((r) => results.push(r));
    backend.expectOne('https://viacep.com.br/ws/88888888/json/').error(new ProgressEvent('error'));
    expect(results).toEqual([null, null]);
  });

  it('não consulta CEP incompleto', () => {
    let result: ViaCepAddress | null | undefined;
    service.find('0131').subscribe((r) => (result = r));
    expect(result).toBeNull();
  });
});
