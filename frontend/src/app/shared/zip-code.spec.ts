import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { FormControl, FormGroup } from '@angular/forms';
import { ZipCodeLookup, zipCodeLookup } from './zip-code';

describe('zipCodeLookup', () => {
  let backend: HttpTestingController;
  let address: FormGroup;
  let lookup: ZipCodeLookup;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    backend = TestBed.inject(HttpTestingController);
    address = new FormGroup({
      zipCode: new FormControl(''),
      street: new FormControl('Rua digitada'),
      district: new FormControl(''),
      city: new FormControl(''),
      state: new FormControl(''),
    });
    lookup = TestBed.runInInjectionContext(() => zipCodeLookup(address));
  });

  afterEach(() => {
    backend.verify();
    vi.useRealTimers();
  });

  function type(value: string): void {
    address.controls['zipCode'].markAsDirty();
    address.controls['zipCode'].setValue(value);
    vi.advanceTimersByTime(300);
  }

  it('preenche o endereço sem apagar o que o CEP não traz', () => {
    type('01310-100');
    expect(lookup.searching()).toBe(true);
    backend
      .expectOne('/api/v1/zip-codes/01310100')
      .flush({ zipCode: '01310100', street: '', district: 'Bela Vista', city: 'São Paulo', state: 'SP' });

    expect(lookup.searching()).toBe(false);
    expect(address.getRawValue()).toEqual({
      zipCode: '01310-100',
      street: 'Rua digitada',
      district: 'Bela Vista',
      city: 'São Paulo',
      state: 'SP',
    });
  });

  it('avisa quando o CEP não é encontrado', () => {
    type('99999-999');
    backend.expectOne('/api/v1/zip-codes/99999999').flush(null, { status: 204, statusText: 'No Content' });
    expect(lookup.notFound()).toBe(true);
  });

  it('não consulta ao carregar um registro', () => {
    address.reset({ zipCode: '01310-100' });
    vi.advanceTimersByTime(300);
    backend.expectNone('/api/v1/zip-codes/01310100');
  });
});
