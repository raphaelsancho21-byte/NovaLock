# NovaLock 🔐
Protetor de aplicativos Android, sem Android Studio.

## Como gerar o APK
Abra **Actions → Build Android APK → Run workflow**. Depois de concluir, baixe o artefato **NovaLock-debug-apk**. A compilação também inicia a cada push na main.

## Ativação
1. Instale o APK, crie um PIN de no mínimo 4 dígitos.
2. Marque os aplicativos que deseja proteger.
3. Ative manualmente **Configurações → Acessibilidade → NovaLock**.
4. O serviço observa mudanças de aplicativo e mostra uma tela de bloqueio quando necessário.

## Regra do bloqueio
Ao inserir o PIN corretamente pela primeira vez para um app, o acesso é permitido até você sair dele. Depois, uma nova abertura exige esperar 24 horas. O período é configurado por aplicativo. Acesso ao NovaLock para gerenciar os apps requer o PIN após configuração.

## Atualizações
O painel consulta a última versão publicada em **GitHub Releases**. Quando houver atualização, abre a página oficial para você instalar. Por segurança, o Android exige participação do usuário na instalação, e o APK precisa ter a mesma assinatura de versão anterior. Builds debug de GitHub Actions podem ser assinadas com chaves diferentes: use uma keystore estável para releases definitivas.

## Limites
Este é um protótipo experimental. **Não é uma barreira impossível de contornar.** Usuários com controle do aparelho podem desativar a acessibilidade, forçar parada, remover o app ou mudar parâmetros do sistema. O sistema Android não autoriza um aplicativo comum a se tornar impossível de desinstalar. Instalação sem permissão explícita ou recursos ocultos antirremoção não fazem parte do projeto. A função de acessibilidade é usada exclusivamente para detectar abertura de aplicativos selecionados; a distribuição na Play Store pode exigir revisão e declaração de uso.

O PIN é armazenado como hash PBKDF2 com salt. Não compartilhe o PIN. Não use este protótipo como única proteção de dados confidenciais.
