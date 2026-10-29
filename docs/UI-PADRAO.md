# Padrão visual (UI) do Tek Condomínio

_28/10/2026. Referência visual: TekPonto (produto da mesma família Tek), mantendo a identidade própria do Tek
Condomínio._ Tudo o que está aqui fica em `frontend/src/styles.scss`. Use as classes globais antes de criar
estilos no componente.

## Identidade

| Elemento | Padrão |
|---|---|
| Cor principal | Verde-petróleo (`--mat-sys-primary`, `#006a6a`): botões principais, links, abas ativas, foco, faixa dos diálogos |
| Cor de destaque | Laranja Tek (`--tc-accent`, `#ee7a0c`): página ativa no menu, contador de notificações, marcadores pontuais. Use com moderação |
| Menu lateral | Grafite azulado (`--tc-sidebar-*`, `#1e2733`), como no TekPonto |
| Fundo e superfícies | Fundo cinza claro (`--tc-bg`), cartões brancos (`--tc-surface`) com borda (`--tc-border`) e sombra suave (`--tc-shadow`) |
| Tipografia | Inter (pacote `@fontsource-variable/inter`, servida pelo próprio sistema, sem Google Fonts). Títulos com peso 600–700, rótulos de seção em caixa alta pequena |
| Tema | Claro/escuro automático; o usuário pode fixar no cabeçalho (ícone de aparência). Todas as cores usam `light-dark()` |
| Densidade | Compacta na área administrativa; o portal do morador usa `.tc-comfortable` (padrão, melhor no toque) |

## Estrutura

- **Menu lateral** (`core/layout/shell.*`):
  - grupos em acordeão: um grupo aberto por vez, e o grupo da página atual abre sozinho;
  - página ativa em laranja, com uma barra na borda direita;
  - no desktop, pode ser recolhido para mostrar só os ícones (com dica ao passar o mouse);
  - em telas menores que 960px, vira uma gaveta que abre por cima da tela;
  - rodapé com o usuário logado.
- **Cabeçalho:** seletor de condomínio, administradora, aparência, notificações e menu do usuário.
- **Página:** `.tc-page` com `<tc-cabecalho-pagina sobretitulo titulo subtitulo>`. As ações da página entram como conteúdo
  do cabeçalho.

## Componentes e classes

| Uso | Classe / componente |
|---|---|
| Cartão | `.tc-card`; cabeçalho interno `.tc-card-head` (`.is-divided` com linha) |
| Títulos | `.tc-section-title` (título do cartão), `.tc-section-label` (rótulo em caixa alta) |
| Indicadores | `.tc-kpis` > `.tc-kpi.tc-card` com `.icon`, `.label`, `.value`, `.sub`; tons `tone-success`, `tone-danger`, `tone-warning`, `tone-info`; `.tc-kpis.inset` dentro de outro cartão |
| Situação | `.tc-status` + `is-active`, `is-success`, `is-pending`, `is-warning`, `is-info`, `is-danger`, `is-inactive` (`.big` maior) |
| Dados rótulo/valor | `.tc-dl` (`.cols` em colunas) |
| Avisos | `.tc-banner.is-warning` / `.is-danger`; erro de formulário `.tc-alert` |
| Filtros | `.filters` (campo largo `.search`); no celular cada campo ocupa a linha |
| Tabelas | `mat-table` dentro de `.tc-table-card`; tabela HTML `table.lines` |
| Formulário | `.tc-grid` + `.span-N` (12 colunas); campos Material `outline` com fundo suave |
| Diálogos | Faixa de título na cor principal (`.is-danger` em vermelho para ações perigosas), `<tc-fechar-dialog />` e rodapé cinza. Formulários simples: `abrirFormulario()`; confirmações: `pedirConfirmacao()` / `pedirMotivo()` |
| Arquivo | `.tc-file-button` |
| Ícone em destaque | `.tc-ico` |
| Painéis expansíveis | `mat-accordion.tc-accordion-cards` |
| Vazio / carregando / erro | `<tc-estado-vazio icone titulo descricao>`; `mat-progress-bar` com a classe `idle` quando parado |

## Celular

- **Tabelas viram cartões** abaixo de 720px: `mat-table` dentro de `.tc-table-card`, ou `table.lines.tc-cards-mobile`.
  - O rótulo de cada célula vem do cabeçalho da tabela: a diretiva `tcTabelasResponsivas`, aplicada no `main` do
    shell, preenche o `data-label` sozinha. Não é preciso repetir o rótulo nos templates.
  - A primeira coluna vira o título do cartão.
  - Tabelas largas de relatório usam `.tc-table-wide` (ou omitem `tc-cards-mobile`) e rolam dentro do próprio
    contêiner.
- **Nada pode rolar para o lado na página.** Isso foi verificado em 390px e 820px em todas as telas.

## Acessibilidade

- O foco aparece em todos os elementos.
- Há um link "Pular para o conteúdo".
- Botões só com ícone têm `aria-label`.
- Grupos do menu usam `aria-expanded`, e a página atual tem `aria-current`.
- O contraste foi conferido nos dois temas.
- Animações são desligadas quando o sistema pede menos movimento (`prefers-reduced-motion`).
