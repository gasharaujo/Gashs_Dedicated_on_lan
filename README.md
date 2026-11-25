# Gash's Dedicated on Lan

Mod de **Gasharaujo** para Forge 1.20.1 e NeoForge 1.21.1 e 26.1.2.

Na seleção de mundos, selecione um save e clique em **Dedicated and connect**. No primeiro uso, aceite a [EULA do Minecraft](https://www.minecraft.net/eula) para baixar e instalar o servidor correspondente à versão do loader da instância. O servidor carrega o save diretamente da pasta `saves` e o cliente entra assim que ele estiver pronto.

Ao desconectar, o servidor continua rodando. Use **Connect to Dedicated** na seleção de mundos ou em **Multijogador** para voltar. Os outros jogadores na rede local podem entrar pela lista de LAN ou usar o IP da máquina que iniciou o servidor e a porta registrada em `server.properties`. O servidor exige autenticação do Minecraft por padrão.

Ao fechar o cliente que iniciou o servidor, o servidor salva o mundo e encerra. Depois disso o save pode ser aberto normalmente no singleplayer. Para mundos que precisam de atualização ou conversão, abra-os primeiro com o Minecraft.

## Configuração

O primeiro uso cria `config/gashs_dedicated_on_lan.properties` na instância. Edite o arquivo antes de iniciar o servidor.

| Opção | Padrão | Como funciona |
| --- | --- | --- |
| `server-memory-mib` | `0` | Usa metade do limite de memória do cliente. Para fixar o limite em 4 GiB, use `4096`. |
| `excluded-mods` | vazio | IDs ou nomes de arquivos separados por vírgulas. Use para mods de cliente que o loader não identifica como exclusivos do cliente. |
| `eula-accepted` | `false` | Registra o aceite da EULA. |

O diretório `gash-dedicated/<loader-versão>/` dentro da instância guarda a instalação e o log `dedicated.log`. A pasta `mods` do servidor é sincronizada a cada início. Para acrescentar JARs exclusivos do servidor, use `server-mods` nesse mesmo diretório. Os arquivos iniciais de configuração são copiados da instância, e os arquivos do servidor podem ser editados de forma independente. O servidor precisa de versões compatíveis dos mods que adicionam conteúdo ao save.

O mod não exige integrações nem bibliotecas adicionais.

## Compilar

Cada versão tem seu próprio Gradle Wrapper. Entre na pasta da versão e execute `gradlew.bat build` no Windows ou `./gradlew build` no Linux. O JAR sai em `build/libs`.

| Pasta | JDK |
| --- | --- |
| `forge-1.20.1` | 17 |
| `neoforge-1.21.1` | 21 |
| `neoforge-26.1.2` | 25 |
