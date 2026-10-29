import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { Router, RouterLink } from '@angular/router';
import { Observable, filter, switchMap } from 'rxjs';
import { paraProblema } from '../../core/api/api.models';
import { AuthService } from '../../core/auth/auth.service';
import { CondominioContexto } from '../../core/contexto/condominio-contexto';
import { NotificacaoService } from '../../core/notificacao/notificacao.service';
import { formatarCnpj, formatarTelefone, formatarCep } from '../../comum/formato/formato-br';
import { MascaraDirective } from '../../comum/formato/mascara.directive';
import { MensagemErroCampo } from '../../comum/formularios/erro-campo';
import { aplicarErrosServidor } from '../../comum/formularios/erros-servidor';
import { ConfirmacaoDialog, DadosConfirmacaoDialog } from '../../comum/ui/confirmacao-dialog';
import { EstadoVazio } from '../../comum/ui/estado-vazio';
import { CabecalhoPagina } from '../../comum/ui/cabecalho-pagina';
import {
  UFS,
  ROTULOS_SITUACAO_CONDOMINIO,
  ROTULOS_TIPO_CONDOMINIO,
  Condominio,
  CondominioRequest,
  TipoCondominio,
} from './condominio.models';
import { CondominioService } from './condominio.service';

const vazio = (v: string | null | undefined): string | null => (v && v.trim() ? v.trim() : null);

@Component({
  selector: 'tc-condominio-form',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    DatePipe,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MascaraDirective,
    MensagemErroCampo,
    CabecalhoPagina,
    EstadoVazio,
  ],
  templateUrl: './condominio-form.html',
  styleUrl: './condominio-form.scss',
})
export class CondominioForm implements OnInit {
  private readonly service = inject(CondominioService);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly notificacao = inject(NotificacaoService);
  protected readonly auth = inject(AuthService);
  private readonly contexto = inject(CondominioContexto);

  /** Parâmetro de rota; ausente em /condominios/novo. */
  readonly id = input<string>();

  protected readonly tipos = Object.entries(ROTULOS_TIPO_CONDOMINIO) as [TipoCondominio, string][];
  protected readonly ufs = UFS;
  protected readonly rotulosSituacao = ROTULOS_SITUACAO_CONDOMINIO;

  protected readonly condominio = signal<Condominio | null>(null);
  protected readonly carregando = signal(false);
  protected readonly salvando = signal(false);
  protected readonly erroCarga = signal<string | null>(null);
  protected readonly erroForm = signal<string | null>(null);
  protected readonly desatualizado = signal(false);

  protected readonly novo = computed(() => !this.id());
  protected readonly inativo = computed(() => this.condominio()?.situacao === 'INACTIVE');
  protected readonly podeEditar = computed(() => {
    const id = this.id();
    if (!id) return this.auth.pode('CONDOMINIUM_CREATE');
    return this.auth.podeEm('CONDOMINIUM_EDIT', id) && !this.inativo();
  });
  protected readonly podeAlterarSituacao = computed(() => {
    const id = this.id();
    return !!id && this.auth.podeEm('CONDOMINIUM_INACTIVATE', id);
  });

  private readonly fb = inject(FormBuilder).nonNullable;
  protected readonly form = this.fb.group({
    nome: ['', [Validators.required, Validators.maxLength(150)]],
    razaoSocial: ['', [Validators.maxLength(200)]],
    // Apenas formato; dígitos verificadores são validados pelo backend.
    cnpj: ['', [Validators.pattern(/^[0-9A-Z]{2}\.[0-9A-Z]{3}\.[0-9A-Z]{3}\/[0-9A-Z]{4}-\d{2}$/)]],
    tipo: ['RESIDENTIAL' as TipoCondominio, [Validators.required]],
    email: ['', [Validators.email, Validators.maxLength(254)]],
    telefone: ['', [Validators.pattern(/^\(\d{2}\) \d{4,5}-\d{4}$/)]],
    endereco: this.fb.group({
      cep: ['', [Validators.pattern(/^\d{5}-\d{3}$/)]],
      logradouro: ['', [Validators.maxLength(200)]],
      numero: ['', [Validators.maxLength(20)]],
      complemento: ['', [Validators.maxLength(100)]],
      bairro: ['', [Validators.maxLength(100)]],
      cidade: ['', [Validators.maxLength(100)]],
      uf: [''],
    }),
  });

  ngOnInit(): void {
    const id = this.id();
    if (id) {
      this.form.disable();
      this.carregar(id);
    }
  }

  carregar(id: string): void {
    this.carregando.set(true);
    this.erroCarga.set(null);
    this.service.obter(id).subscribe({
      next: (c) => {
        this.aplicar(c);
        this.carregando.set(false);
      },
      error: (err: unknown) => {
        this.erroCarga.set(paraProblema(err).detail);
        this.carregando.set(false);
      },
    });
  }

  salvar(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.salvando() || !this.podeEditar()) {
      return;
    }
    this.salvando.set(true);
    this.erroForm.set(null);
    this.desatualizado.set(false);
    const atual = this.condominio();
    const solicitacao = this.paraRequest(atual?.versao);
    const chamada$: Observable<Condominio> = atual
      ? this.service.atualizar(atual.id, solicitacao)
      : this.service.cadastrar(solicitacao);
    chamada$.subscribe({
      next: (salvo) => {
        this.salvando.set(false);
        this.notificacao.sucesso(atual ? 'Alterações salvas.' : 'Condomínio cadastrado.');
        this.contexto.recarregar();
        if (atual) {
          this.aplicar(salvo);
        } else {
          void this.router.navigate(['/condominios', salvo.id], { replaceUrl: true });
        }
      },
      error: (err: unknown) => {
        this.salvando.set(false);
        const problema = paraProblema(err);
        if (problema.codigo === 'STALE_VERSION') {
          this.desatualizado.set(true);
        } else if (problema.codigo === 'CONDOMINIUM_CNPJ_TAKEN') {
          this.form.controls.cnpj.setErrors({ server: problema.detail });
        } else if (problema.codigo === 'VALIDATION_ERROR') {
          const restante = aplicarErrosServidor(this.form, problema);
          this.erroForm.set(restante.length ? restante.join(' ') : problema.detail);
        } else if (problema.status !== 403 && problema.status !== 0 && problema.status < 500) {
          this.erroForm.set(problema.detail);
        }
      },
    });
  }

  inativar(): void {
    const c = this.condominio();
    if (!c) return;
    this.dialog
      .open<ConfirmacaoDialog, DadosConfirmacaoDialog, string | true>(ConfirmacaoDialog, {
        data: {
          titulo: 'Inativar condomínio',
          mensagem: `"${c.nome}" deixará de receber novos lançamentos e cobranças. O histórico será preservado.`,
          rotuloConfirmar: 'Inativar',
          perigo: true,
          rotuloMotivo: 'Justificativa',
        },
        width: '480px',
      })
      .afterClosed()
      .pipe(
        filter((motivo): motivo is string => typeof motivo === 'string'),
        switchMap((motivo) => this.service.inativar(c.id, motivo)),
      )
      .subscribe({
        next: (atualizado) => {
          this.aplicar(atualizado);
          this.notificacao.sucesso('Condomínio inativado.');
          this.contexto.recarregar();
        },
        error: (err: unknown) => this.erroForm.set(paraProblema(err).detail),
      });
  }

  ativar(): void {
    const c = this.condominio();
    if (!c) return;
    this.dialog
      .open<ConfirmacaoDialog, DadosConfirmacaoDialog, string | true>(ConfirmacaoDialog, {
        data: { titulo: 'Reativar condomínio', mensagem: `Reativar "${c.nome}"?`, rotuloConfirmar: 'Reativar' },
        width: '420px',
      })
      .afterClosed()
      .pipe(
        filter((ok) => ok === true),
        switchMap(() => this.service.ativar(c.id)),
      )
      .subscribe({
        next: (atualizado) => {
          this.aplicar(atualizado);
          this.notificacao.sucesso('Condomínio reativado.');
          this.contexto.recarregar();
        },
        error: (err: unknown) => this.erroForm.set(paraProblema(err).detail),
      });
  }

  recarregar(): void {
    const id = this.id();
    if (id) {
      this.desatualizado.set(false);
      this.carregar(id);
    }
  }

  private aplicar(c: Condominio): void {
    this.condominio.set(c);
    this.form.reset({
      nome: c.nome,
      razaoSocial: c.razaoSocial ?? '',
      cnpj: formatarCnpj(c.cnpj),
      tipo: c.tipo,
      email: c.email ?? '',
      telefone: formatarTelefone(c.telefone),
      endereco: {
        cep: formatarCep(c.endereco?.cep),
        logradouro: c.endereco?.logradouro ?? '',
        numero: c.endereco?.numero ?? '',
        complemento: c.endereco?.complemento ?? '',
        bairro: c.endereco?.bairro ?? '',
        cidade: c.endereco?.cidade ?? '',
        uf: c.endereco?.uf ?? '',
      },
    });
    if (this.podeEditar()) {
      this.form.enable();
    } else {
      this.form.disable();
    }
  }

  private paraRequest(versao: number | undefined): CondominioRequest {
    const v = this.form.getRawValue();
    const a = v.endereco;
    const temEndereco = Object.values(a).some((x) => !!x?.trim());
    return {
      nome: v.nome.trim(),
      razaoSocial: vazio(v.razaoSocial),
      cnpj: vazio(v.cnpj),
      tipo: v.tipo,
      email: vazio(v.email),
      telefone: vazio(v.telefone),
      endereco: temEndereco
        ? {
            cep: vazio(a.cep),
            logradouro: vazio(a.logradouro),
            numero: vazio(a.numero),
            complemento: vazio(a.complemento),
            bairro: vazio(a.bairro),
            cidade: vazio(a.cidade),
            uf: vazio(a.uf),
          }
        : null,
      versao,
    };
  }
}
