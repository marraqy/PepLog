# Validação — PepLog 0.6.5

10/09/2026. Pacote `app.peptides.journal`, nome **PepLog**, `versionCode = 14`.

Os caminhos em `artifacts/` referem-se a arquivos locais da validação histórica e não estão incluídos no repositório.

## Entrega

`artifacts/PepLog-0.6.5.apk`, 10.969.118 bytes. Mesma assinatura das versões anteriores, verificada com `apksigner`; nome, pacote e versão conferidos com `aapt`.

SHA-256: `825684F882B56065F064254F22B6C69FF99E5C38805B682BCAC1FEA4443B9728`.

- Protocolos sem registros podem ser excluídos com confirmação. A agenda é removida; cálculos salvos são preservados.
- Protocolos com realizações ou itens ignorados podem ser arquivados. A exclusão é bloqueada também na transação do banco, que relê o protocolo antes de apagar.
- Arquivados ficam disponíveis em **Mostrar arquivados**, preservam todos os registros e não geram lembretes.
- Desarquivar retorna o protocolo como pausado, sem reativação automática.
- Room permanece v3. O JSON v3 inclui o campo opcional `archived`; documentos anteriores são lidos como não arquivados. Versões antigas veem o protocolo arquivado como concluído e podem perder a marca de arquivamento ao reexportar, mas mantêm os registros.

## Verificação executada

- **23 testes JVM passaram**, incluindo preservação do protocolo arquivado no backup, leitura de documentos antigos, rejeição de arquivado ativo e duplicação sem herdar arquivamento.
- **Lint debug: 0 erros, 17 avisos**. Compilação de release e lint vital passaram.
- **9 testes Android passaram no APK de release**, no emulador Android 15/API 35, em 89,475 segundos. `artifacts/release-device-tests.txt`.
- O teste novo verifica cancelamento e confirmação da exclusão, preservação dos cálculos, bloqueio de exclusão com registros, cancelamento e confirmação do arquivamento, ocultação na lista, ausência de lembretes, preservação no backup, consulta de arquivados e desarquivamento como pausado.
- Os testes existentes também passaram: histórico e datas, protocolos e calendário, persistência, migração, catálogo, blends e alarme real em segundo plano.

A automação nova inicialmente tentou rolar um botão fora de uma área rolável. O auxiliar de teste foi corrigido para rolar somente quando necessário; a execução completa seguinte passou. Não foi necessário alterar o aplicativo por essa falha de teste.

## Limites

Não houve validação em celular físico nesta entrega. Para atualizar, instale sobre a versão anterior, sem desinstalar.
