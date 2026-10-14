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
  // specs/fork-watch: Settings → watch (WearSettings.jsx)
  'Watch (Wear OS)': 'Relógio (Wear OS)',
  'Checking…': 'Verificando…',
  'No watch connected to this phone': 'Nenhum relógio conectado a este celular',
  "openGym is not on your watch yet. Install it with the fork's release script.": 'O openGym ainda não está no relógio. Instale pelo script de release do fork.',
  'openGym is on your watch': 'O openGym está no seu relógio',
  'Send workouts to the watch': 'Enviar treinos para o relógio',
  'Show on the watch': 'Mostrar no relógio',
  'Start a workout on the phone and the watch shows it and taps once.': 'Comece um treino no celular e o relógio mostra e vibra uma vez.',
  'On the watch': 'No relógio',
  'Sets you tick on the watch reach this phone when you open openGym.': 'As séries marcadas no relógio entram neste celular quando você abre o openGym.',
  'Last time': 'Última vez',
  'Time so far': 'Tempo até agora',
  'Next exercise during the rest': 'Próximo exercício no descanso',
  'Rest timer': 'Timer de descanso',
  'Ring': 'Anel',
  'Countdown only': 'Só a contagem',
  'The bezel turns first': 'O bezel ajusta primeiro',
  'Reps': 'Repetições',
  'Weight': 'Peso',
  'Tap the wrist when a rest is over': 'Vibrar no pulso no fim do descanso',
}

export function ft(key, lang, ...args) {
  const s = String(lang || '').startsWith('pt') ? (PT[key] || key) : key
  return args.reduce((out, a, i) => out.replace('{' + i + '}', a), s)
}
