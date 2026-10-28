/** Rótulos em português para os códigos devolvidos pela API. */
export const LABELS: Record<string, Record<string, string>> = {
  entryKind: { INCOME: 'Receita', EXPENSE: 'Despesa' },
  entryStatus: { OPEN: 'Em aberto', PARTIAL: 'Parcial', PAID: 'Pago', CANCELLED: 'Cancelado', RENEGOTIATED: 'Renegociado' },
  approval: { NOT_REQUIRED: '—', PENDING: 'Aguardando aprovação', APPROVED: 'Aprovado', REJECTED: 'Reprovado' },
  paymentMethod: { PIX: 'PIX', BOLETO: 'Boleto', TRANSFER: 'Transferência', CASH: 'Dinheiro', CHECK: 'Cheque', DEBIT: 'Débito em conta', CARD: 'Cartão', OTHER: 'Outro' },
  bankKind: { CHECKING: 'Conta corrente', SAVINGS: 'Poupança', INVESTMENT: 'Aplicação', CASH: 'Caixa' },
  allocationMethod: { IDEAL_FRACTION: 'Fração ideal', EQUAL: 'Igualitário', FIXED: 'Valor fixo por unidade', CONSUMPTION: 'Consumo individual' },
  allocationStatus: { GENERATED: 'Gerado', CANCELLED: 'Cancelado' },
  agreementStatus: { ACTIVE: 'Ativo', COMPLETED: 'Quitado', CANCELLED: 'Cancelado' },
  contactType: { PHONE: 'Telefone', EMAIL: 'E-mail', WHATSAPP: 'WhatsApp', LETTER: 'Carta', IN_PERSON: 'Presencial', LEGAL: 'Jurídico', OTHER: 'Outro' },
  occurrenceCategory: { MAINTENANCE: 'Manutenção', NOISE: 'Barulho', SECURITY: 'Segurança', CLEANING: 'Limpeza', COMPLAINT: 'Reclamação', REQUEST: 'Solicitação', SUGGESTION: 'Sugestão', OTHER: 'Outros' },
  priority: { LOW: 'Baixa', MEDIUM: 'Média', HIGH: 'Alta', URGENT: 'Urgente' },
  occurrenceStatus: { OPEN: 'Aberta', IN_PROGRESS: 'Em atendimento', WAITING: 'Aguardando', RESOLVED: 'Resolvida', CANCELLED: 'Cancelada' },
  audience: { ALL: 'Todos', BUILDING: 'Bloco', UNIT: 'Unidade' },
  assemblyKind: { ORDINARY: 'Ordinária', EXTRAORDINARY: 'Extraordinária' },
  assemblyStatus: { DRAFT: 'Rascunho', CALLED: 'Convocada', IN_PROGRESS: 'Em andamento', CLOSED: 'Encerrada', CANCELLED: 'Cancelada' },
  voteWeight: { UNIT: '1 voto por unidade', IDEAL_FRACTION: 'Por fração ideal' },
  voteChoice: { YES: 'Sim', NO: 'Não', ABSTAIN: 'Abstenção' },
  periodicity: { MONTHLY: 'Mensal', YEARLY: 'Anual', ONE_TIME: 'Único', OTHER: 'Outra' },
  contractStatus: { ACTIVE: 'Vigente', TERMINATED: 'Encerrado' },
  docCategory: { CONVENTION: 'Convenção', INTERNAL_RULES: 'Regulamento interno', MINUTES: 'Ata', CONTRACT: 'Contrato', INVOICE: 'Nota fiscal', RECEIPT: 'Comprovante', FINANCIAL_STATEMENT: 'Demonstrativo financeiro', UNIT: 'Documento de unidade', OCCURRENCE: 'Ocorrência', OTHER: 'Outro' },
  visibility: { INTERNAL: 'Interno', RESIDENTS: 'Moradores' },
};

export function label(group: string, code: string | null | undefined): string {
  if (!code) return '—';
  return LABELS[group]?.[code] ?? code;
}

export function options(group: string): { value: string; label: string }[] {
  return Object.entries(LABELS[group]).map(([value, l]) => ({ value, label: l }));
}
