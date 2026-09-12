# Revisão em partes — PepLog 0.6.4

10/09/2026. Revisão do código de telas, calendário, protocolos, cálculos, blends, catálogo, persistência, backup e lembretes. As sugestões abaixo não representam funcionalidades já implementadas.

## Parte 1 — Calendário mensal

A estrutura existente é adequada para um calendário de um único mês. Não há necessidade de outra biblioteca nem de uma grade com paginação. Os dias derivam da mesma agenda usada pelos registros, incluindo versões antigas de itens e dias com estados mistos.

Melhorias implementadas:

- O índice de realizações é construído uma vez por mês, em vez de uma vez por dia. O resultado fica em `remember(protocol, month)` e é recalculado quando os dados ou o mês mudam.
- Hoje recebe contorno; o dia selecionado continua com preenchimento. A seleção e a indicação de hoje também são expostas à acessibilidade.
- Atalho **Ir para hoje** junto ao calendário.
- Altura mínima de 48 dp por dia, sem depender de células quadradas pequenas. A largura ainda é dividida em sete colunas; aparelhos muito estreitos precisam de verificação física.
- Cabeçalhos dos dias centralizados e legenda que pode quebrar em linhas, evitando uma única linha espremida.
- Navegação respeita o intervalo de datas aceito pelos protocolos: 2000 a 2100.

Código: [ProtocolScreen.kt](app/src/main/java/app/peptides/journal/ProtocolScreen.kt), `MonthlyCalendar`; [Protocols.kt](app/src/main/java/app/peptides/journal/Protocols.kt), `monthStatuses`.

## Parte 2 — Datas no histórico

- Os filtros **De** e **Até** abrem um calendário ao tocar no botão inteiro.
- Exibição `DD/MM/AAAA` em português e `MM/DD/AAAA` em inglês. Os valores internos continuam em ISO.
- Cada limite pode ser limpo; **Limpar período** remove os dois sem apagar a pesquisa.
- Ao escolher o início, dias posteriores ao fim selecionado ficam indisponíveis; ao escolher o fim, dias anteriores ao início ficam indisponíveis. Datas iguais são válidas.
- A contagem de resultados ajuda a perceber o efeito do filtro.
- Os limites continuam inclusivos e usam a data local de criação do cálculo. O histórico de cálculos e as realizações de protocolos continuam sendo consultas diferentes.

O componente de data existente foi reutilizado pelos protocolos e pelo histórico. Não houve alteração no banco ou no backup.

Código: [MainActivity.kt](app/src/main/java/app/peptides/journal/MainActivity.kt), `History`; [ProtocolScreen.kt](app/src/main/java/app/peptides/journal/ProtocolScreen.kt), `DatePickerField`.

## Parte 3 — Pontos para próximas correções

São constatações por leitura do código, com cenários a reproduzir em testes dedicados. Não foram corrigidas silenciosamente nesta entrega.

| Prioridade | Ponto e consequência | Correção sugerida / evidência |
|---|---|---|
| Alta | **Virada do dia em Hoje.** A data não é um estado atualizado pelo relógio. Um formulário aberto antes da meia-noite usa outra chamada a `LocalDate.now()` ao confirmar, podendo registrar a ocorrência no dia seguinte ou receber erro de validação. | Atualizar a data ao retomar o app e na meia-noite; guardar a data da ocorrência escolhida no formulário. `TodayScreen.kt`, `TodayScreen` e `TodayLogDialog`. Testar também mudança de fuso. |
| Média | **Erro de lembrete depois de salvar.** A operação grava os dados e chama `Reminders.refresh` dentro do mesmo tratamento de falha; uma falha apenas no agendamento pode aparecer como erro geral de operação. | Separar sucesso da gravação de falha do lembrete, com mensagem que diga o que de fato ocorreu. `MainActivity.kt`, `operation`. |
| Média | **Idioma parcial nos seletores.** Os textos próprios usam a preferência interna, mas o seletor Material e a data do cabeçalho de Hoje dependem da configuração de idioma do Android. | Aplicar o idioma do app de forma consistente aos componentes de plataforma; testar Android em inglês com PepLog em português e o inverso. `DatePickerField`, `TodayScreen` e `LocalLanguage`. |
| Média | **Edição do catálogo pode ser descartada sem aviso.** Voltar ou tocar fora fecha o diálogo de cadastro, perdendo campos preenchidos. | Confirmar descarte quando houver mudanças ou preservar o rascunho. `MainActivity.kt`, `Catalog`. |
| Baixa | **Cores de estado inconsistentes.** O calendário mostra ignorado em vermelho, mas o texto de uma realização ignorada nas agendas usa verde. | Usar a mesma cor por estado, mantendo sempre a identificação por texto. `ProtocolScreen.kt` e `TodayScreen.kt`. |
| Baixa | **Filtros do histórico não acompanham a navegação.** O estado fica dentro da tela; sair para um detalhe e voltar pode reconstruir a consulta. | Guardar a consulta e o período no nível da navegação, se o fluxo de retorno for priorizado. `MainActivity.kt`, `History` e seleção de `screen`. |

## Ideias de evolução, separadas das correções

1. **Corrigir uma realização com trilha de alterações.** Quantidade ou observação podem ser digitadas incorretamente. Preservar o registro original e o motivo da correção; não simplesmente sobrescrever o passado.
2. **Calendário geral dos protocolos.** Complementar a tela Hoje com navegação mensal e filtro por protocolo, usando as mesmas ocorrências existentes.
3. **Atalhos no histórico:** hoje, últimos sete dias e mês atual. Também permitir consultar realizações, identificando claramente sua diferença em relação a cálculos salvos.
4. **Resumo do mês:** totais de planejados, realizados e ignorados, com critérios explícitos para pendências passadas e itens futuros.
5. **Backup mais visível:** informar a data da última exportação concluída e oferecer lembrete opcional de backup. Considerar proteção por senha apenas com um fluxo de recuperação e compatibilidade definido.
6. **Navegação de protocolos longos:** recolher a lista de peptídeos ou posicionar calendário e agenda antes dela, para evitar rolar muitos itens até chegar ao dia desejado.

Ordem sugerida: virada do dia → integridade do backup → clareza de falhas e idioma → correção auditável de realizações → calendário geral.

## Verificação

### Complemento entregue na 0.6.5

A ausência de exclusão de protocolos, apontada depois desta revisão, foi corrigida. Protocolos sem realizações ou itens ignorados podem ser excluídos mediante confirmação. Protocolos com registros podem ser arquivados e consultados em **Mostrar arquivados**, preservando todo o histórico. Desarquivar retorna o protocolo como pausado. Veja [VALIDACAO-0.6.5.md](VALIDACAO-0.6.5.md).

Resultados executados e limitações ficam em [VALIDACAO-0.6.4.md](VALIDACAO-0.6.4.md). A revisão de código não substitui testes em aparelhos físicos, fontes ampliadas e TalkBack.

