# Requisitos: Health Connect nos dois sentidos (openGym ↔ Samsung Health)

> Spec `fork-health` · Fase 1 · Base: fork `fork/main` (upstream v1.4.1 + fork-watch M1)
> Status: **aprovado em 2026-10-14**

## 1. Contexto

O Samsung Health não oferece integração direta a apps de terceiros: o SDK é só para parceiros.
O caminho oficial é o **Health Connect**, o repositório de dados de saúde do Android, onde os dois
apps leem e gravam.

O que já se verificou no celular do dono (S25 Ultra, Android 17, One UI 9, em 2026-10-14):
- O upstream já **grava** no Health Connect os treinos finalizados (`ExerciseSessionRecord`) e
  as pesagens (`WeightRecord`), via `HealthConnectPlugin.java` e `lib/health-sync.js`.
- O Samsung Health tem `READ_EXERCISE` e `READ_WEIGHT` concedidas: ele lê o que o openGym
  grava.
- O Samsung Health tem `WRITE_WEIGHT`, `WRITE_BODY_FAT` e `WRITE_HEART_RATE` concedidas: o peso e
  a gordura da balança ou da bioimpedância do Galaxy Watch, e a FC do relógio, chegam ao Health
  Connect. **Não** grava massa magra, água nem massa óssea.

## 2. Histórias e critérios (EARS)

### HW: openGym → Health Connect (gravação; já existe no upstream)

**HW-1 (P1, já implementado em `e0d237c8`).** Como dono, não quero que o histórico importado de
outro app vá parar no Samsung Health.
- **HW-1.1** QUANDO a gravação no Health Connect for ligada, ENTÃO o app DEVE guardar o momento
  (`since`) e gravar **só treinos iniciados a partir dele**.
- **HW-1.2** As pesagens DEVEM continuar sendo gravadas todas, como no upstream.
- **HW-1.3** SE a opção for desligada e religada, ENTÃO o `since` original DEVE ser mantido, para
  o histórico antigo nunca ser enviado.

**HW-2 (P1).** Como dono, quero ver no Samsung Health o treino que fiz no openGym.
- **HW-2.1** QUANDO um treino for finalizado no openGym com a gravação ligada, ENTÃO ele DEVE
  aparecer no histórico de exercícios do Samsung Health como musculação, com início, fim e nome.
  Será verificado no aparelho real. Se o Samsung Health não mostrar sessões vindas do Health
  Connect, registrar isso como limitação do Samsung Health no `FORK.md`. Não há como contornar
  sem o SDK de parceiro.

### HR: Health Connect → openGym (leitura; novo)

**HR-1 (P1).** Como dono, quero que o peso medido pelo Samsung Health apareça no openGym.
- **HR-1.1** QUANDO a leitura estiver ligada e o app abrir ou voltar ao primeiro plano, ENTÃO o
  app DEVE ler os registros de peso novos do Health Connect e criar pesagens no openGym.
- **HR-1.2** O app NÃO DEVE ler de volta o que ele mesmo gravou: ignora registros cuja origem é
  `com.cezar.opengym` ou cujo `clientRecordId` começa com `opengym-`.
- **HR-1.3** SE houver vários registros de peso no mesmo dia, ENTÃO vale o **último do dia**.
- **HR-1.4** SE o dia já tiver uma pesagem feita no openGym, ENTÃO a do openGym DEVE ser mantida
  (o lançamento manual vence).

**HR-2 (P1).** Como dono, quero a gordura corporal (bioimpedância do relógio) nas Medidas.
- **HR-2.1** QUANDO houver registros novos de gordura corporal, ENTÃO o app DEVE preencher
  `bodyFat` na medição daquele dia. Sem medição no dia, cria uma só com `bodyFat`.
- **HR-2.2** SE a medição do dia já tiver `bodyFat` lançado no openGym, ENTÃO o valor do openGym
  DEVE ser mantido.

**HR-3 (P1).** Como dono, quero escolher o que é lido e poder desligar.
- **HR-3.1** DEVE existir em Configurações → Dados e backup um interruptor "Ler do Conexão
  Saúde" (separado do "Gravar"), desligado por padrão.
- **HR-3.2** QUANDO ligado, ENTÃO o app DEVE pedir só as permissões de leitura necessárias
  (`READ_WEIGHT`, `READ_BODY_FAT` e, no P2, `READ_HEART_RATE`) e mais `READ_HEALTH_DATA_HISTORY`
  para trazer o histórico anterior.
- **HR-3.3** Na primeira leitura, o app DEVE trazer o **histórico disponível de peso e gordura**
  (tudo o que o Health Connect liberar). Depois, só o que for novo.
- **HR-3.4** QUANDO desligado, ENTÃO o app DEVE parar de ler. Os dados já trazidos ficam no
  openGym como dados do dono.
- **HR-3.5** O card DEVE mostrar quando foi a última leitura e quantos registros vieram.

**HR-4 (P2).** Como dono, quero a frequência cardíaca do relógio nos meus treinos.
- **HR-4.1** QUANDO um treino finalizado tiver início e fim, ENTÃO o app PODE ler a FC do Health
  Connect nesse intervalo e guardar `hr {avg, max}` no treino.
- **HR-4.2** Isso só pode ser feito depois do **mesmo portão de compatibilidade** do fork-watch
  (M5.1, D-4): um backup com `hr` precisa importar sem erro no app oficial. Sem isso, a FC não é
  salva.
- **HR-4.3** O resumo da FC aparece no detalhe do treino no openGym (UI mínima).

### HQ: Qualidade
- **HQ-1** A lógica de mesclar (o que entra, o que é ignorado, quem vence) DEVE ser funções puras
  com teste vitest.
- **HQ-2** A suíte do upstream continua com o mesmo resultado (só as 2 falhas conhecidas).
- **HQ-3** Teste manual no S25: pesagem e bioimpedância no Samsung Health → aparecem no openGym.
  Treino no openGym → aparece no Samsung Health.

## 3. Fora de escopo
- Ler **treinos** do Samsung Health para o histórico do openGym. Eles não têm séries, carga e reps;
  o dono pediu para não trazer treinos antigos.
- Passos, sono, calorias, SpO2, ECG, pressão.
- Massa magra, água e massa óssea (o Samsung Health não grava no Health Connect).
- Sincronização em tempo real durante o treino. O relógio do fork-watch cobre o ao vivo.
- Escrever no Samsung Health outra coisa além do que o upstream já grava.

## 4. Decisões propostas (o dono aprova junto com o documento)

| # | Tema | Proposta |
|---|---|---|
| HD-1 | Treinos antigos no Samsung Health | Não enviar (decidido pelo dono em 2026-10-14; HW-1). |
| HD-2 | Conflito de peso no mesmo dia | O lançamento do openGym vence; entre registros do Health Connect, vale o último do dia. |
| HD-3 | Histórico na primeira leitura | Trazer todo o histórico de peso e gordura que o Health Connect liberar (`READ_HEALTH_DATA_HISTORY`). |
| HD-4 | Onde fica o código | Plugin nativo novo do fork (`HealthReadPlugin`) no módulo `:fork-health`, sem mexer no `HealthConnectPlugin.java` do upstream. A lógica de mesclar fica em `frontend/src/fork/health-read.js`. |
| HD-5 | Ordem no roteiro | Fazer **antes do M2 do relógio**: o celular já está conectado e a spec é pequena. |
