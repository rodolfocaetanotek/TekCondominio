import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { FormControl, FormGroup } from '@angular/forms';
import { BuscaCep, buscaCep } from './cep';

describe('buscaCep', () => {
  let backend: HttpTestingController;
  let endereco: FormGroup;
  let busca: BuscaCep;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    backend = TestBed.inject(HttpTestingController);
    endereco = new FormGroup({
      cep: new FormControl(''),
      logradouro: new FormControl('Rua digitada'),
      bairro: new FormControl(''),
      cidade: new FormControl(''),
      uf: new FormControl(''),
    });
    busca = TestBed.runInInjectionContext(() => buscaCep(endereco));
  });

  afterEach(() => {
    backend.verify();
    vi.useRealTimers();
  });

  function digitar(valor: string): void {
    endereco.controls['cep'].markAsDirty();
    endereco.controls['cep'].setValue(valor);
    vi.advanceTimersByTime(300);
  }

  it('preenche o endereço sem apagar o que o CEP não traz', () => {
    digitar('01310-100');
    expect(busca.buscando()).toBe(true);
    backend
      .expectOne('/api/v1/ceps/01310100')
      .flush({ cep: '01310100', logradouro: '', bairro: 'Bela Vista', cidade: 'São Paulo', uf: 'SP' });

    expect(busca.buscando()).toBe(false);
    expect(endereco.getRawValue()).toEqual({
      cep: '01310-100',
      logradouro: 'Rua digitada',
      bairro: 'Bela Vista',
      cidade: 'São Paulo',
      uf: 'SP',
    });
  });

  it('avisa quando o CEP não é encontrado', () => {
    digitar('99999-999');
    backend.expectOne('/api/v1/ceps/99999999').flush(null, { status: 204, statusText: 'No Content' });
    expect(busca.naoEncontrado()).toBe(true);
  });

  it('não consulta ao carregar um registro', () => {
    endereco.reset({ cep: '01310-100' });
    vi.advanceTimersByTime(300);
    backend.expectNone('/api/v1/ceps/01310100');
  });
});
