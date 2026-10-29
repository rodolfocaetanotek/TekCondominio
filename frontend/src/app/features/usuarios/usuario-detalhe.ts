import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { Observable, filter } from 'rxjs';
import { paraProblema } from '../../core/api/api.models';
import { AuthService } from '../../core/auth/auth.service';
import { NotificacaoService } from '../../core/notificacao/notificacao.service';
import { MensagemErroCampo } from '../../comum/formularios/erro-campo';
import { ConfirmacaoDialog, DadosConfirmacaoDialog } from '../../comum/ui/confirmacao-dialog';
import { EstadoVazio } from '../../comum/ui/estado-vazio';
import { CabecalhoPagina } from '../../comum/ui/cabecalho-pagina';
import { CamposPapel } from './campos-papel';
import { Atribuicao, UsuarioDetalhe } from './usuario.models';
import { UsuarioService } from './usuario.service';

@Component({
  selector: 'tc-usuario-detalhe',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatProgressBarModule,
    MatTooltipModule,
    MensagemErroCampo,
    CabecalhoPagina,
    EstadoVazio,
    CamposPapel,
  ],
  templateUrl: './usuario-detalhe.html',
  styleUrl: './usuario-detalhe.scss',
})
export class UsuarioDetalhePage implements OnInit {
  private readonly service = inject(UsuarioService);
  private readonly dialog = inject(MatDialog);
  private readonly notificacao = inject(NotificacaoService);
  protected readonly auth = inject(AuthService);

  readonly id = input.required<string>();

  protected readonly usuario = signal<UsuarioDetalhe | null>(null);
  protected readonly carregando = signal(true);
  protected readonly ocupado = signal(false);
  protected readonly erroCarga = signal<string | null>(null);
  protected readonly erroAcao = signal<string | null>(null);

  protected readonly podeGerenciar = computed(() => this.auth.pode('USER_MANAGE'));
  protected readonly ehProprio = computed(() => this.usuario()?.id === this.auth.usuario()?.id);

  protected readonly nome = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(150)] });
  protected readonly codigoPapel = new FormControl('', { nonNullable: true, validators: [Validators.required] });
  protected readonly idCondominio = new FormControl<string | null>(null);

  ngOnInit(): void {
    this.service.obter(this.id()).subscribe({
      next: (u) => {
        this.aplicar(u);
        this.carregando.set(false);
      },
      error: (err: unknown) => {
        this.erroCarga.set(paraProblema(err).detail);
        this.carregando.set(false);
      },
    });
  }

  salvarNome(): void {
    const u = this.usuario();
    this.nome.markAsTouched();
    if (!u || this.nome.invalid) return;
    this.executar(this.service.renomear(u.id, this.nome.value.trim(), u.versao), 'Nome atualizado.');
  }

  adicionarAtribuicao(): void {
    const u = this.usuario();
    this.codigoPapel.markAsTouched();
    this.idCondominio.markAsTouched();
    if (!u || this.codigoPapel.invalid || this.idCondominio.invalid) return;
    this.executar(
      this.service.adicionarAtribuicao(u.id, { codigoPapel: this.codigoPapel.value, idCondominio: this.idCondominio.value }),
      'Papel atribuído.',
      () => {
        this.codigoPapel.reset('');
        this.idCondominio.reset(null);
      },
    );
  }

  removerAtribuicao(a: Atribuicao): void {
    const u = this.usuario();
    if (!u) return;
    this.confirmar({
      titulo: 'Remover papel',
      mensagem: `Remover "${a.nomePapel}"${a.nomeCondominio ? ' em ' + a.nomeCondominio : ''} de ${u.nome}? O acesso correspondente é retirado imediatamente.`,
      rotuloConfirmar: 'Remover',
      perigo: true,
    }).subscribe(() => this.executar(this.service.removerAtribuicao(u.id, a.id), 'Papel removido.'));
  }

  alternarAtivo(): void {
    const u = this.usuario();
    if (!u) return;
    const desativando = u.ativo;
    this.confirmar(
      desativando
        ? { titulo: 'Desativar usuário', mensagem: `${u.nome} perderá o acesso imediatamente e todas as sessões serão encerradas.`, rotuloConfirmar: 'Desativar', perigo: true }
        : { titulo: 'Reativar usuário', mensagem: `Reativar o acesso de ${u.nome}?`, rotuloConfirmar: 'Reativar' },
    ).subscribe(() =>
      this.executar(desativando ? this.service.desativar(u.id) : this.service.ativar(u.id), desativando ? 'Usuário desativado.' : 'Usuário reativado.'),
    );
  }

  reenviarConvite(): void {
    const u = this.usuario();
    if (!u) return;
    this.ocupado.set(true);
    this.erroAcao.set(null);
    this.service.reenviarConvite(u.id).subscribe({
      next: () => {
        this.ocupado.set(false);
        this.notificacao.sucesso(`Novo convite enviado para ${u.email}. O convite anterior deixou de valer.`);
      },
      error: (err: unknown) => this.falhar(err),
    });
  }

  private executar(chamada$: Observable<UsuarioDetalhe>, sucesso: string, depois?: () => void): void {
    this.ocupado.set(true);
    this.erroAcao.set(null);
    chamada$.subscribe({
      next: (u) => {
        this.aplicar(u);
        this.ocupado.set(false);
        this.notificacao.sucesso(sucesso);
        depois?.();
      },
      error: (err: unknown) => this.falhar(err),
    });
  }

  private falhar(err: unknown): void {
    this.ocupado.set(false);
    const problema = paraProblema(err);
    if (problema.status !== 403 && problema.status !== 0 && problema.status < 500) {
      this.erroAcao.set(problema.codigo === 'VALIDATION_ERROR' ? (problema.erros ?? []).map((e) => e.mensagem).join(' ') : problema.detail);
    }
  }

  private confirmar(data: DadosConfirmacaoDialog): Observable<true> {
    return this.dialog
      .open<ConfirmacaoDialog, DadosConfirmacaoDialog, string | true>(ConfirmacaoDialog, { data, width: '440px' })
      .afterClosed()
      .pipe(filter((r): r is true => r === true));
  }

  private aplicar(u: UsuarioDetalhe): void {
    this.usuario.set(u);
    this.nome.reset(u.nome);
    if (this.podeGerenciar()) {
      this.nome.enable();
    } else {
      this.nome.disable();
    }
  }
}
