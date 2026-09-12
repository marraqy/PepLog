# Validação — PepLog 0.6.4

Data: 10/09/2026. Pacote `app.peptides.journal`; `versionCode = 13`; nome Android **PepLog**.

Os caminhos em `artifacts/` referem-se a arquivos locais da validação histórica e não estão incluídos no repositório.

## Entrega

APK assinado: `artifacts/PepLog-0.6.4.apk`, 10.952.734 bytes.

SHA-256: `3FA02D7074ADC3A79E3D78531BAC3E0AF613596A83C8DCFA2A32F3AF3EB0F318`.

O certificado foi comparado com o APK 0.6.3 e é o mesmo. O pacote, a versão e o nome foram confirmados com `aapt`; a assinatura foi verificada com `apksigner`. A atualização foi instalada sobre a 0.6.2 do emulador antes da limpeza dos dados de teste.

Calendário mensal e filtros de datas do histórico melhorados. Revisão detalhada: [REVISAO-0.6.4.md](REVISAO-0.6.4.md). Banco Room e backup JSON permanecem na versão 3; não houve mudança de permissões.

## Verificações executadas

- **22 testes JVM passaram**, incluindo estados mistos do calendário, fevereiro bissexto e término da agenda, além dos testes existentes de cálculo, blends, protocolos, lembretes e backup.
- **Lint debug: 0 erros e 17 avisos**. Relatório em `app/build/reports/lint-results-debug.html`.
- **Compilação de release e lint vital passaram**.
- **8 testes Android passaram no APK de release**, em emulador Android 15/API 35 (`emulator-5556`), em 122 segundos. Resultado em `artifacts/release-device-tests.txt`.
- O novo teste do histórico verifica abrir e cancelar o calendário, confirmar datas, exibir a data em português, aplicar os limites inclusivos, limpar o período e pesquisar registros.
- O fluxo de protocolos verifica navegação mensal, retorno para hoje, seleção acessível, estados realizados/ignorados, alterações de agenda, backup e visibilidade integral do aviso no fim do editor.
- A suíte também executou persistência, migração de Room v2, catálogo, blends, edição de registros e um alarme real em segundo plano com abertura da notificação.
- Capturas do calendário e do histórico foram inspecionadas visualmente: `artifacts/screenshots/protocol-calendar-states-en.png` e `artifacts/screenshots/history-pt.png`.

A primeira execução encontrou dois problemas de teste: idioma não restaurado pelo novo teste e uso ambíguo de `onRoot()` no teste do diálogo da versão anterior. O isolamento foi corrigido e a altura visível do texto passou a ser comparada à altura completa do seu layout. A execução completa seguinte passou.

## Limites

Não houve teste em celular físico, em todas as versões Android suportadas, em paisagem, com TalkBack ou em todas as escalas de fonte. Os demais achados da revisão geral são cenários identificados no código e estão documentados como próximos trabalhos, sem alegação de reprodução em aparelho nesta entrega.

## Repetir

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "& .\build.ps1 -Tasks @('-Pkotlin.compiler.execution.strategy=in-process','--no-daemon','testDebugUnitTest','lintDebug','assembleDebugAndroidTest','assembleRelease')"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\test-release.ps1 -Serial emulator-5556
```

O segundo comando requer o emulador local iniciado e limpa somente os dados do aplicativo no emulador informado.
