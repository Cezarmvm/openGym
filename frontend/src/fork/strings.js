// FORK: the fork's own UI strings, kept out of upstream's locale catalogues so a merge never
// conflicts there. English is the key; Portuguese covers both pt and pt-BR profiles.
const PT = {
  'Read from Health Connect': 'Ler do Conexão Saúde',
  'Weight and body fat from Samsung Health and your scale come into openGym. What you type here always wins.':
    'Peso e gordura corporal do Samsung Health e da balança vêm para o openGym. O que você lança aqui sempre vale.',
  'Weight': 'Peso',
  'Body fat': 'Gordura corporal',
  'Last read: {0} · {1} new': 'Última leitura: {0} · {1} novos',
  'Not read yet': 'Ainda não lido',
  'Read now': 'Ler agora',
  'Reading from Health Connect': 'Lendo do Conexão Saúde',
  'Health Connect permission not granted': 'Permissão do Conexão Saúde não concedida',
  'Could not read from Health Connect': 'Não foi possível ler do Conexão Saúde',
  'Permission withdrawn. Tap to allow again': 'Permissão retirada. Toque para permitir de novo',
  'Only the last 30 days: Health Connect did not allow the history.': 'Só os últimos 30 dias: o Conexão Saúde não liberou o histórico.',
}

export function ft(key, lang, ...args) {
  const s = String(lang || '').startsWith('pt') ? (PT[key] || key) : key
  return args.reduce((out, a, i) => out.replace('{' + i + '}', a), s)
}
