# SMPutilities

Um único mod de gerenciamento para servidores SMP e de roleplay: painel da staff, chat, moderação, vanish,
inventários (visualização, backups e perfis), nicknames de RP, TTS com balões de fala, limitador de portais,
controle de dimensões, comandos de qualidade de vida, brincadeiras da staff e sincronização de mods.

Made by [Arthou](https://discord.com/users/990003604725321808). Todos os direitos reservados (veja [LICENSE](LICENSE)).

*English summary at the end.*

## Situação das versões

Um jar por versão do jogo e loader (`SMPutilities-<versão>-<loader>.jar`), todos do mesmo código:

| Minecraft | Fabric | Forge | NeoForge |
| --- | --- | --- | --- |
| 1.18.2 · 1.19.2 · 1.19.4 · 1.20.1 | ✔ | ✔ | — (não existe; no NeoForge 1.20.1 use o jar Forge 1.20.1) |
| 1.20.4 · 1.20.6 · 1.21.1 · 1.21.3 · 1.21.4 · 1.21.5 · 1.21.8 · 1.21.10 · 1.21.11 | ✔ | ✔ | ✔ |

Cada jar cobre a sua linha de versão (por exemplo o de 1.21.4 aceita só 1.21.4; o de 1.18.2, a 1.18.x).
O CI compila os 35 alvos e sobe um servidor dedicado de cada um (smoke test: carrega todas as classes
que os mixins alteram, roda comandos e desliga). No Forge 1.21.11 o teste usa um servidor Forge instalado
pelo instalador oficial com o jar em `mods/`, porque o ambiente de desenvolvimento do Loom não inicia o
jogo nessa versão. Isso não substitui testar em jogo — veja
[Limitações conhecidas](#limitações-conhecidas).

## Instalação

* **Servidor:** coloque o jar do seu loader/versão em `mods/`. No Fabric é preciso o Fabric API.
* **Clientes:** opcional. Sem o mod no cliente, o jogador entra normalmente e recebe chat, mensagens,
  menus de baú, vanish, nicknames no chat/TAB, sons de menção, etc. O mod no cliente é necessário para o
  painel da staff, nicknames acima da cabeça, balões de fala, TTS e sincronização de mods. Cliente e
  servidor precisam da mesma versão do protocolo do SMPutilities, não do mesmo loader.
* **Integrações opcionais** (o mod funciona sem elas): Simple Voice Chat (voice mute), Curios e Cosmetic
  Armor (inventários/backups), LuckPerms (Permission API do Forge/NeoForge; no Fabric, pelo
  fabric-permissions-api que o LuckPerms traz), Aether (nomes de dimensão/portal).

## Arquivos

| Caminho | Conteúdo |
| --- | --- |
| `config/smputilities/server.json` | Toda a configuração do servidor (criada com valores padrão; `/su reload` recarrega) |
| `config/smputilities/client.json` | Preferências do cliente (balões, TTS, sincronização de mods, credenciais privadas) |
| `config/smputilities/modsync/` | Jars oferecidos pela sincronização de mods |
| `<mundo>/smputilities/players/<uuid>.json` | Dados por jogador (nickname, homes, notas, configurações) |
| `<mundo>/smputilities/backups/<uuid>.dat` | Backups de inventário |
| `<mundo>/smputilities/profiles/<uuid>.dat` | Perfis de inventário |
| `<mundo>/smputilities/punishments.json` | Histórico de punições |
| `<mundo>/smputilities/reports.json` | Denúncias (`/report`) |
| `<mundo>/smputilities/logs/audit-AAAA-MM.jsonl` | Registro administrativo (quem, o quê, em quem, quando) |

Tudo é indexado por UUID; trocar de nome ou de nickname nunca perde dados.

## Sistemas

### Painel da staff — `/su staff [jogador]`
Interface própria (fundo escuro translúcido, barra lateral, destaque verde, animações curtas), com nove
categorias: **Dashboard** (jogadores, TPS, MSPT, memória, uptime, bans/mutes ativos, controles rápidos do
chat), **Players** (busca por nome real ou nickname, online e offline; info, ações, notas/estatísticas e
histórico do jogador), **Chat**, **Inventories** (visualizar/editar, backups com restauração, perfis),
**Punishments** (bans, mutes, denúncias, alertas de x-ray, histórico, log da staff), **World** (dimensões e
portais), **Utilities** (brincadeiras, anúncios, warps, mod sync), **Server** (desempenho, limpeza de lag,
manutenção e whitelist) e **Settings** (toda a config, editável ao vivo).
As ações de um jogador ficam por categoria (Punições, Ferramentas, Inventário, Nome e chat, Brincadeiras);
inventário, baú do End, backups e perfis abertos pelo painel voltam ao painel ao apertar Esc. Textos que não
cabem num botão são cortados com "…" e aparecem inteiros ao passar o mouse.
Ações críticas (banir, expulsar, restaurar, excluir) pedem confirmação. O cliente só pede; o servidor
confere a permissão de cada pedido. Há também uma tecla configurável ("Abrir painel da staff", sem tecla
padrão).

### Chat
O chat vanilla (`<Nome> mensagem`, assinado) é o padrão. Com `chat.customFormat` ligado vale o formato QSMP:
`Nick » Mensagem`, `[+] Nome`, `[-] Nome`, todos configuráveis (`&`-cores e `&#RRGGBB`, marcadores
`{name}`, `{realname}`, `{message}`, `{prefix}`). Também: menções com som enviado pelo servidor (cada
jogador ajusta com `/su mentions on|off|sound <id>|volume <0-1>`), destaque das menções, prefixos de staff e
admin, bloqueio do chat global (`/su chat lock|unlock`), limpar chat, cooldown, proteção contra spam
(taxa, repetição, CAPS), mute individual, entrada/saída desativáveis globalmente ou por jogador
(`/su silentjoin|silentquit`), `/msg` `/tell` `/w` `/reply` `/r` com MPs permitidas ou não durante o
bloqueio, `/ignore` e social spy para a staff. Avisos como "chat bloqueado por <nick>" e bans só mostram
quem fez para a staff; os jogadores veem a versão sem o nome.

**Histórico do chat** (painel → Chat → Histórico, `smputilities.chat.history`): todo o chat público fica em
`<mundo>/smputilities/chat/AAAA-MM-DD.log`, gravado por uma thread separada, sem nada guardado na RAM. No
painel você escolhe o dia, lê do mais novo para o mais antigo carregando 100 mensagens por vez, filtra e
clica numa mensagem para ver inteira. `chat.historyDays` (30) define quantos dias ficam guardados.

### Moderação
`/su ban|mute|voicemute <jogador> [duração] [motivo]`, `/su unban|unmute|voiceunmute`, `/su kick`,
`/su warn`, `/su history`. Durações: `30m`, `2h`, `7d`, `1mo`, `perm`. Banimentos vão também para a ban list
vanilla (com expiração), então o bloqueio no login é o do próprio jogo e `/pardon` continua funcionando.
Staff não pode punir quem tem nível de OP maior.

* **Congelar** — `/su freeze <jogador>` (ou o botão no painel, `smputilities.staff.freeze`): o jogador não anda,
  não quebra nem usa blocos e não se teleporta até ser descongelado. Continua congelado se sair e voltar; a staff
  é avisada quando um jogador congelado sai.
* **Prisão** — `/su jail set <nome>` marca uma prisão onde você está; `/su jail put <jogador> <prisão> [tempo]
  [motivo]` prende (online ou offline), `/su jail release <jogador>` solta, `/su jail list|remove`. O preso não
  passa de `jail.radius` blocos da prisão e não se teleporta; quando o tempo acaba ele volta para onde estava
  (`smputilities.staff.jail`).
* **Contas alternativas** — o mod guarda os últimos IPs de cada jogador (`alts.maxIps`). Quem entra com um IP já
  usado por outra conta gera um aviso para a staff com `smputilities.staff.alts` (vermelho se a outra conta está
  banida); `alts.blockBannedAlts` recusa a entrada nesse caso. `/su alts <jogador>` e a ficha do jogador no
  painel mostram as contas.
* **Filtro do chat** — `chat.filter.words` (separadas por vírgula; `=palavra` só pega a palavra inteira) com
  `chat.filter.mode` `censor` (troca por `***`) ou `block` (não envia). Pega variações com números e acentos
  (`p4l4vr4`). `chat.filter.blockAdvertising` barra IPs e convites do Discord. A staff vê o que foi pego;
  quem tem `smputilities.chat.bypass.filter` passa direto.

### Servidor: desempenho, manutenção e whitelist
A aba **Server** do painel mostra TPS, MSPT e, por dimensão, chunks carregados, entidades, itens no chão, orbes
de XP e mobs hostis, mais os 15 chunks com mais entidades (com o tipo mais comum e botão de teleporte). Há
botões para limpar itens, orbes de XP ou mobs hostis (mobs com nome ou persistentes ficam), com
`smputilities.staff.lag`.

**Manutenção** — `/su maintenance on [mensagem]|off` ou o botão no painel (`smputilities.admin.maintenance`):
só quem tem `smputilities.maintenance.bypass` (OP 2 por padrão) entra, e quem está online sem permissão é
expulso (`maintenance.kickOnline`). A mesma aba liga e desliga a whitelist vanilla e adiciona ou tira nomes
(`smputilities.staff.whitelist`), usando os comandos do próprio jogo.

### Discord
Dois webhooks opcionais (`discord.webhookUrl` e `discord.staffWebhookUrl`; só endereços
`https://discord.com/api/webhooks/…` são aceitos). O público recebe chat, entradas e saídas (quem está em vanish
não aparece), mortes e início/parada do servidor; o da staff recebe ações da staff, denúncias, alertas
suspeitos de x-ray e contas alternativas. Cada tipo liga e desliga em `discord.*`. As mensagens saem por uma
thread separada, juntas a cada 2 segundos, respeitando o limite do Discord, e nunca marcam @everyone nem
cargos.

### Anti-xray (base: AntiXray do DrexHD) e alertas de x-ray — `/su xray`
O anti-xray segue o motor do [AntiXray](https://github.com/DrexHD/AntiXray) (MIT), que por sua vez é o
anti-xray do Paper. Nos chunks enviados aos jogadores, os blocos escondidos que não encostam em nada
transparente (ar, água, vidro…) aparecem como outra coisa, então pacote de textura de x-ray e cliente hack não
acham nada. Os modos são os do AntiXray: `1` troca os minérios escondidos por pedra, ardósia, netherrack ou
pedra do End; `2` enche todo bloco enterrado de minérios falsos aleatórios; `3` faz o mesmo com um bloco por
camada (padrão do Overworld). Ao quebrar ou abrir um bloco, os blocos reais em volta (`updateRadius`) são
enviados de novo. Os padrões são os do AntiXray: Overworld no modo 3 até a altura 256 e Nether no modo 1 até a
128, com lava escondendo. Tudo fica em `antiXray.worlds` (ids de bloco ou `#tags`; `!` na frente tira um da
lista). Com `antiXray.usePermission`, quem tem `smputilities.antixray.bypass` vê os blocos reais.

A diferença para o AntiXray está só na forma de ligar ao jogo. O AntiXray coloca os blocos falsos nas paletas do
próprio mundo e segura os pacotes na conexão. Aqui só o pacote muda: o chunk é lembrado quando o pacote é
criado, e os blocos são escondidos quando a thread de rede escreve o pacote. O mundo salvo nunca é alterado, e a
ordem dos pacotes continua a mesma. Os ganchos (pacote de chunk, envio de chunks, quebra de bloco, `setBlock`)
são os mesmos da 1.18.2 à 1.21.11, só o formato do pacote muda (a partir da 1.21.5 o tamanho do array não vai
mais no pacote). No CI, cada versão monta pacotes de chunk do spawn, esconde os blocos e confere o resultado.

Os alertas de x-ray contam os veios de minérios valiosos que cada jogador acha (`xrayAlerts.thresholds`:
diamante, detritos ancestrais e esmeralda por padrão). A staff com `smputilities.xray.alerts` é avisada a cada
veio e recebe um alerta vermelho, com som, quando alguém passa do limite dentro de `xrayAlerts.windowMinutes`.
Os avisos têm botão de teleporte, e `/su xray` e a aba **X-ray** em Punições listam os últimos 50.

### Registro de blocos e baús (estilo CoreProtect) — `/su log`
Toda mudança de bloco feita enquanto o servidor processa uma ação de um jogador fica registrada com quem fez,
quando e onde. Isso inclui quebrar, colocar, balde, isqueiro, enxada, pá e machado. Explosões entram como
`#explosion`. Mudanças que não trocam o bloco (abrir porta, alavanca, plantação crescendo) ficam de fora. Baús,
barris, fornalhas, funis, caixas de shulker etc. são comparados quando o jogador fecha o que abriu: o que entrou
e o que saiu ficam registrados, item por item. Tudo vai para o disco, em
`<mundo>/smputilities/blocklog/<dimensão>/<região>/<dia>.log`, numa thread separada; nada fica na memória, e os
dias mais velhos que `blockLog.days` (30) são apagados.

* `/su log inspect` liga o inspetor: bater ou clicar com o direito num bloco mostra o histórico dele, sem quebrar
  nem abrir.
* `/su log lookup u:<jogadores> t:<tempo> r:<raio|#world> a:<break|place|explode|container>` busca mudanças (por
  padrão num raio de 10 em volta de quem busca), 10 por página, com botão de teleporte.
* `/su log rollback …` desfaz o que a busca achar (exige `t:`): cada bloco volta a como era antes da primeira
  mudança, com o conteúdo de baús e placas, mas só se ninguém mexeu nele depois. Itens tirados de baús voltam e
  itens colocados saem. É feito aos poucos (`blockLog.changesPerTick` por tick, até `blockLog.maxChanges`).
  `/su log restore …` refaz, e `/su log undo` volta o último rollback.
* Permissões: `smputilities.staff.blocklog` (inspecionar e buscar, OP 2) e `smputilities.staff.rollback` (OP 3).

### Vanish — `/vanish [on|off|<jogador>]`
Feito no servidor: um mixin no rastreamento de entidades impede que o jogador invisível seja enviado a
quem não tem `smputilities.staff.vanish.see` (sem posição, equipamento, sons ou partículas), remoção da
TAB, entrada/saída silenciosas (a staff vê com a etiqueta `[invisível]`), mobs não miram, coleta de itens
opcionalmente bloqueada, indicador na action bar e no HUD, persiste entre logins. Também fica de fora do
`/list`, da contagem e da amostra na lista de servidores, das menções e sugestões de nome, do `/msg`, `/seen`
e TPA; mensagens de morte e de conquistas dele só chegam a quem pode vê-lo, não atrasa a noite e, no Simple
Voice Chat, só é ouvido por quem pode vê-lo.

### Inventários
* `/su inventory <jogador>`, `/su enderchest <jogador>` (e `curios`/`cosmetic`): online é editado ao vivo;
  offline edita só as listas de itens do arquivo do jogador (posição, dimensão e o resto ficam intactos).
  Se o jogador entrar com a edição aberta, a cópia editada é aplicada nele e a janela fecha — sem duplicar
  nem perder itens. Antes de gravar uma edição offline é feito um backup.
* Backups automáticos (entrada, saída, morte, troca de dimensão, periódico) e manuais:
  `/su inventory backup <jogador>`, `/su inventory list <jogador>`, `/su inventory backups <jogador>`
  (navegador em GUI do Inventory Rollback Plus), `/su inventory restore <jogador> <backup> [full|inventory|ender]`.
  Toda restauração cria antes um backup do estado atual.
* **Perfis (Inv Profile integrado):** a Troca Rápida original (`/invprofile` ou `/su profile quick`) com o
  mesmo comportamento, mais perfis nomeados para personagens: `/su profile [create|switch|delete|list|nick]`.
  Guardam inventário, armaduras, Baú do Fim, XP, vida, fome, efeitos e slots de Curios/Cosmetic Armor
  (cada parte configurável, localização opcional). A troca grava o estado de saída antes de aplicar o novo e
  o arquivo do jogador lembra qual perfil ele carrega, então uma queda do servidor no meio da troca é
  corrigida no próximo login. Staff: `/su profile view|switchfor`, e pelo painel.

### Nicknames (base: HiddenNames) — `/hiddennames setName …` e `/su nick <nome>`
O sistema segue o modelo do [HiddenNames](https://github.com/pitbox46/HiddenNames) (MIT): cada jogador tem
um nome com cor e uma **animação da placa de nome** — `none`, `hidden` (placa escondida), `breathe`,
`rainbow`, `cycle` ou `random` (as animações foram portadas do HiddenNames). Os comandos são os mesmos do
HiddenNames, só que o nome usa `&`-cores em vez de JSON (assim quem não tem o mod continua entrando):
* `/hiddennames setName <cor> <animação> <nome>`, `/hiddennames setName name|color|animation|reset …`,
  e para outros `/hiddennames setName <jogadores> …` (`smputilities.nickname.others`).
* `/hiddennames nameplateVisible <jogadores> <true|false>`, `/hiddennames defaultVisible <true|false>`
  (placas visíveis por padrão), `/hiddennames blocksHideName <true|false>` (blocos escondem as placas).
* `/su nick <nome>`, `/su nick clear` e `/su nick set <jogador> <nome>` continuam como atalhos.

Como no HiddenNames, a cor do time substitui a do nick (`nicknames.teamColorOverride`), e cada cliente pode
mostrar a própria placa em terceira pessoa e desligar as animações (`nametags.showOwn`, `nametags.animations`
no `client.json`). O nome aparece no chat, mensagens de morte, TAB (com prefixo/sufixo do time), `/msg`
(configurável) e acima da cabeça; animações, placas escondidas e placas escondidas atrás de blocos são
desenhadas pelo cliente, então valem para quem tem o mod. Unicidade opcional, nome real oculto para quem não
é staff, `/su realnames` mostra os nomes reais só para você; `/su whois`. Comandos aceitam nome real,
nickname ou UUID, e guardam sempre o UUID. Não use o HiddenNames junto: o SMPutilities o substitui (e
importa o `hiddennames/data.json`).

### TTS e balões de fala — `/su tts`
O menu do DuTTS (gênero, variação, idioma). Com uma voz ativa, o que o jogador digita vira fala: o servidor
envia o texto ao cliente dele, cujo app de voz (*SMPutilities Voice*, um jar à parte, embutido no mod e
iniciado só quando necessário) fala num microfone virtual que o voice chat/Discord usa. Como no DuTTS, a
mensagem não aparece no chat; `tts.showInChat` (desligado por padrão) faz ela aparecer também. Todos por
perto com o mod veem o balão de fala do NEXEL/LeafTalk (mesmas texturas e geometria, um balão por mensagem
empilhado acima da cabeça, quase transparente agachado) e, enquanto ele digita, o balão animado "...".

O app de voz tem interface própria, em português ou inglês conforme o sistema: **Visão geral** (medidor de
voz animado, número de falas, vozes, reprodutor, checklist "Pronto para jogar" e um campo para testar a
voz), **Microfone** (quatro passos: criar o microfone virtual, mandar a voz para ele, ouvir também,
escolher no voice chat) e **Atividade** (registro). No Linux ele cria o cabo `SMPutilities_TTS_Cable` e o
microfone **SMPutilities TTS Microphone** (escolha esse no voice chat); no Windows não dá para criar um
microfone virtual sem driver, então instale o VB-Cable, escolha "CABLE Input" como saída da voz no app e
"CABLE Output" como microfone no voice chat. Sem o app, só o TTS fica indisponível.

### Mundo
* `/su portal limit <nether|end|aether|id> <n|unlimited>`, `/su portal count|list|remove|clear`. Portais do
  Nether são contados ao acender; do End quando o último olho completaria a moldura. Portais destruídos são
  detectados (quebra de bloco e verificação periódica) e liberam a vaga. Permissão para ignorar o limite.
* `/su dimension <dimensão> on|off`, `/su dimension list`: bloqueia novas viagens (portais, `/tp`, entidades
  opcionalmente) sem descarregar nada nem tocar em dados/construções. Exceção para quem tem permissão.

### Qualidade de vida (do TPA)
`/tpa`, `/tpahere`, `/tpaccept`, `/tpdeny` (com botões clicáveis), `/tpacancel` (desfaz os pedidos que você
mandou), `/tpatoggle` (recusa pedidos de todos; a staff ainda pode), `/home`, `/sethome`, `/delhome`, `/homes`,
`/setdefaulthome`, `/spawn`, `/setspawn`, `/back`, `/warp`, `/warps`, `/setwarp`, `/delwarp`, `/rtp`,
`/tpall`, `/tplogout`, `/hat`, `/sit`. Aquecimento com cancelamento ao mover/levar dano, cooldowns por
comando, limite de homes (config ou permissão numérica `smputilities.teleport.home.limit`), comandos
bloqueáveis por dimensão.

### SMP: AFK, denúncias, chat da staff, anúncios e reinícios
Tudo funciona com clientes sem o mod e cada parte pode ser desligada em `features`.
* **AFK — `/afk [motivo]`:** também automático depois de `afk.autoMinutes` (5) sem olhar em volta, andar ou
  falar (ser levado por água ou carrinho não conta). Mostra `[AFK]` na TAB, avisa todos (exceto para quem
  está em vanish), avisa quem manda `/msg` para alguém AFK e, com `afk.ignoreForSleep`, quem está AFK fora
  da cama não impede de pular a noite (a porcentagem de jogadores dormindo ignora essas pessoas). Kick
  opcional após `afk.kickMinutes` (desligado por padrão; `smputilities.afk.bypass.kick` fica de fora).
* **Denúncias — `/report <jogador> <motivo>`:** a staff online recebe o aviso com botões para teleportar até
  onde a denúncia foi feita e para fechar; quem entra depois vê quantas estão abertas. `/su reports`,
  `/su reports close <id>`, `/su reports tp <id>`, ou no painel (Punições → Denúncias, com os mesmos
  botões; a ficha de cada jogador mostra quantas denúncias abertas ele tem e se está AFK). Guardadas em
  `<mundo>/smputilities/reports.json`, com intervalo mínimo entre denúncias e registro na auditoria; quem
  denunciou é avisado quando a staff fecha.
* **Chat da staff — `/sc <mensagem>`** (ou `/staffchat`); `/sc` sozinho liga/desliga o modo em que as
  mensagens normais vão só para a staff.
* **Anúncios automáticos:** `announcements.messages` (lista com `&`-cores e `{online}`) a cada
  `announcements.intervalMinutes`, em ordem ou aleatórios; links `https://` viram clicáveis.
* **Reinício agendado — `/su restart <tempo> [motivo]`**, `/su restart cancel`, `/su restart` (status), e
  horários diários em `restart.dailyTimes` (ex.: `["06:00"]`). Avisa no chat e com som nos tempos de
  `restart.warningSeconds` (15 min, 10, 5, 2, 1 min, 30 s, 10…1 s, também na action bar), depois desconecta
  todos com a mensagem e desliga o servidor salvando o mundo. Quem liga de volta é a hospedagem ou o script
  de inicialização, como no `/stop`.
* **Boas-vindas:** anúncio da primeira entrada de cada jogador ("jogador nº N"), MOTD por jogador ao entrar
  (`welcome.motd`, com `{name}`, `{online}`, `{max}`) e `/rules` (`welcome.rules`). Links ficam clicáveis.
* **Informações:** `/playtime [jogador]`, `/seen <jogador>` (respeita o vanish) e, ao morrer, só o jogador
  vê as coordenadas e a dimensão, com um atalho `[/back]`.

### Brincadeiras da staff — `/su troll <ação> <jogador> [valor]`
Seguem a lista do [TrollBoss](https://github.com/minesuchtiiii/troll-boss) (só como referência: ele é GPL-3.0,
então nenhum código dele foi copiado). No painel ficam em cinco grupos (ações do jogador → Brincadeiras, e a
aba Utilidades):
* **Visual:** explosão e raio falsos, creeper inofensivo (explode sem dano), som, susto, abdução alienígena,
  estouro de vacas, chuva de lulas (mobs invulneráveis, somem sozinhos), cabeça de abóbora (o capacete volta
  para o inventário), brilho e os títulos do DUSMP.
* **Efeitos:** náusea, escuridão, tontura, infecção, congelar (preso no lugar), sem minerar, lentidão,
  flutuar, super pulo.
* **Movimento:** lançar, empurrar, girar, virar de costas, teleportes aleatórios, borda do mundo, trocar de
  lugar com outro jogador, céu, enterrar (com Resistência, sem sufocar), gaiola de vidro e teia.
* **Chat e kicks:** mensagens/entradas/saídas falsas, op/deop falsos, spam, chat embaralhado (liga/desliga),
  kick troll, crash falso e reinício falso.
* **Dano:** queimar, raio de verdade, explosão (sem quebrar blocos), machucar (até meio coração), fome,
  palmada, queda livre, bigorna e contagem fatal.

O que coloca blocos só ocupa ar e é desfeito ao terminar, quando o jogador sai e quando o servidor desliga.
As de dano e kicks pedem confirmação no painel. Cada grupo pode ser desligado na config (`troll.harmful`,
`troll.kicks`, `troll.worldChanges`…), e quem tem `smputilities.troll.bypass` só pode ser alvo de si mesmo.
Ficaram de fora as do TrollBoss que apagam ou trocam o inventário (potato troll, drop inventory), as que
dependem de itens especiais (bad apple, arcos, AK-47) e as de mau gosto.

### Sincronização de mods (opcional, desligada por padrão)
Coloque jars em `config/smputilities/modsync/` e/ou liste downloads externos em `modSync.external`. Clientes
com o mod comparam (SHA-512) e veem uma tela com o que falta, de onde vem e o tamanho; só baixam se o
jogador aceitar. Downloads só por HTTPS de hosts permitidos ou pelo host embutido do servidor (que serve
apenas esses arquivos, só a jogadores conectados, com token), verificados antes de entrar em `mods/`,
nunca executados; remoções vão para uma pasta de backup. Credenciais de repositórios privados ficam só no
`client.json` do jogador. Avisa quando é preciso reiniciar e permite cancelar.

### Permissões e registros
Todos os nós (`smputilities.staff.panel`, `smputilities.staff.ban`, `smputilities.staff.inventory.edit`,
`smputilities.staff.vanish`, `smputilities.staff.voice.mute`, `smputilities.staff.realnames`,
`smputilities.chat.manage`, `smputilities.nickname.change`, `smputilities.portal.manage`,
`smputilities.dimension.manage`, … — lista completa em
[`Perm.java`](core/src/main/java/com/arthou/smputilities/core/perm/Perm.java)) são registrados na Permission
API do Forge/NeoForge; no Fabric são consultados pelo fabric-permissions-api (que o LuckPerms instala).
Com um plugin de permissões, ele decide; sem ele valem os níveis de OP padrão, ajustáveis em
`permissions.levelOverrides`, mais concessões por jogador em `permissions.grants`. Toda ação
administrativa vai para o log de auditoria (também visível no painel).

### Dados dos mods antigos
Na primeira inicialização o SMPutilities importa (só lê, nunca altera) os dados de Inv Profile (vira a
Troca Rápida), Inventory Rollback Plus (backups), TPA (homes, warps, spawn), DUSMP (silent join/quit, voz),
DuTTS (vozes) e HiddenNames (nomes e animações das placas). Os comandos antigos `/invprofile`, `/view`,
`/inventoryrollback`, `/dutts` e `/hiddennames` continuam funcionando.

## O que ficou de fora de propósito

* A compatibilidade com o DuCurrency (custo de teleporte) do TPA, que depende de um mod externo que não
  está no repositório de bases.

## Limitações conhecidas

* Nickname, animação e placa escondida acima da cabeça só para clientes com o mod (a placa é desenhada pelo
  cliente; o HiddenNames tem a mesma limitação).
  Sugestões de comandos e a tela de "interações sociais" do jogo ainda mostram nomes de conta.
* Balões de fala: o Forge não tem evento de renderização do mundo a partir da 1.21.3, então no Forge
  1.21.3–1.21.8 eles são desenhados por um mixin e no Forge 1.21.9+ por uma etapa própria do frame graph
  (`AddFramePassEvent`); nos outros loaders/versões, pelo evento do loader. Esses dois caminhos do Forge
  ainda não foram vistos em jogo.
* Limite de portais: portais de destino gerados automaticamente pelo jogo não são contados; portais de
  outros mods só são limitados se o mod disparar o `PortalSpawnEvent` do Forge/NeoForge (no Fabric, só
  portais do Nether). O bloqueio de dimensão funciona para qualquer dimensão.
* Antes da 1.19.4 não existem Text Displays: o `/sit` usa uma nuvem de efeito invisível. Efeitos que não
  existem na versão (escuridão antes da 1.19) são simplesmente pulados nas brincadeiras.
* Backups e perfis guardam o formato da versão em que foram feitos e são atualizados pelos *data fixers*
  do jogo ao subir de versão; voltar para uma versão mais antiga não converte itens.
* Curios/Cosmetic Armor: edição apenas com o jogador online.
* Sincronização de mods: um jar com o mesmo nome e preso pelo Windows é deixado em
  `config/smputilities/modsync-client/pending/` para substituir manualmente.
* **Testado no CI, não em jogo**: os 35 jars compilam e o servidor de cada um sobe, aplica os mixins e roda
  comandos, mas a parte visual (painel, balões, HUD) e o jogo com jogadores reais precisam de teste num
  servidor de testes antes de produção.

## Multi-loader e multi-versão

```
core/                         lógica pura em Java 17 (config, permissões, chat, punições, auditoria,
                              portais, dimensões, teleporte, mod sync), com testes
mod/src/main/java             todo o código do jogo, compartilhado por todos os alvos
  mc/                         as chamadas que mudam entre versões (NBT, itens, textos, ids, efeitos,
                              teleporte, permissões de OP, bans, tab list...) ficam só aqui
  platform/                   Platform (o que cada loader fornece) e CommonEvents (todos os eventos)
  client/gui/                 Gfx e widgets próprios: o painel desenha igual de 1.18 (PoseStack) a
                              1.21.11 (GuiGraphics novo, eventos de input novos)
mod/src/{fabric,forge,neoforge}/   só a ligação com cada loader (eventos, rede, permissões, mixins)
mod/swaps.txt                 renomeações da Mojang aplicadas por versão (ResourceLocation→Identifier...)
buildSrc/                     pré-processador: //#if MC>=1.21.5 && FABRIC ... //#else ... //#endif
gradle/target.gradle          o build de um alvo (Architectury Loom, mapeamentos da Mojang)
versions/<versão>-<loader>/   versões exatas de Minecraft/loader/Fabric API (tools/gen_targets.py)
tts-bridge/                   aplicativo auxiliar de TTS (jar embutido)
tools/                        traduções (tools/lang → genlang.py), CI (erros, smoke test), sondas de API
```

As diferenças entre versões foram levantadas comparando as assinaturas reais das classes do jogo em
cada versão (`tools/probe`, workflow `probe`), não de memória.

## Compilar

```sh
./gradlew -Psu.targets=1.21.1-fabric build             # um alvo; jar em dist/
./gradlew -Psu.targets=1.20.1-forge,1.21.11-neoforge build
./gradlew -Psu.targets=all build                       # os 35 (cada um baixa o seu Minecraft)
./gradlew -Psu.coreOnly=true :core:test                # só a lógica pura, sem baixar o Minecraft
tools/smoke.sh 1.21.1-fabric                           # depois do build: sobe o servidor do alvo (smoke test)
```

Requer JDK 21 (e JDK 17 para rodar servidores de 1.18.2 a 1.20.4). Traduções: edite `tools/lang/*.tsv`
e rode `python3 tools/genlang.py tools/lang mod/src/main/resources/assets/smputilities/lang`.

## Licença

SMPutilities é software proprietário: Copyright (c) 2026 arthou, todos os direitos reservados ([LICENSE](LICENSE)).
Nenhuma permissão é concedida para usar, copiar, modificar, redistribuir ou criar trabalhos derivados sem
autorização prévia e por escrito. Componentes de terceiros mantêm as suas licenças, listadas em
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) (HiddenNames e AntiXray, MIT; JLayer, LGPL). O TrollBoss (GPL-3.0) serviu só
de referência de comportamento: nenhum código dele foi copiado, para não obrigar o mod a ser GPL. Os dois arquivos
vão dentro de cada jar.

---

### English summary

SMPutilities is one mod for SMP/roleplay servers: staff panel GUI with nine categories, vanilla-by-default
chat with an optional QSMP format, mentions, mutes, spam protection, bans synced with the vanilla ban list,
server-side vanish, an anti-xray after DrexHD's AntiXray (MIT; only outgoing chunk packets change, never the world)
with x-ray alerts for staff, a CoreProtect-style block and container log with inspect, lookup, rollback, restore and
undo, freeze and jails, alt detection by IP, a chat word and advertising filter, Discord webhooks, maintenance mode,
a server tab with lag numbers, entity hotspots, clean-ups and the whitelist, live and offline inventory editing, automatic and manual backups with safe restore,
Inv Profile's Quick Change plus crash-safe named profiles, RP nicknames built on HiddenNames (same /hiddennames commands, nameplate
animations, hidden nameplates, blocks hiding nameplates, team color override, data import), TTS with speech bubbles, portal
limits, dimension access control, TPA-style QoL commands (with /tpacancel and /tpatoggle), AFK detection
(tab tag, AFK players ignored by the sleep percentage, optional kick), /report with staff alerts, staff chat,
automatic announcements, scheduled restarts with countdown, first-join welcome, MOTD and /rules,
/playtime, /seen, death coordinates, staff pranks after TrollBoss (no code copied; block changes are undone), opt-in mod sync with
SHA-512 verification, permission nodes for LuckPerms and an audit log. It imports data from the
standalone mods it replaces.

One shared code base builds 35 jars: Fabric and Forge for 1.18.2, 1.19.2, 1.19.4, 1.20.1, 1.20.4, 1.20.6,
1.21.1, 1.21.3, 1.21.4, 1.21.5, 1.21.8, 1.21.10 and 1.21.11, and NeoForge from 1.20.4. Version differences
live in `mod/src/main/java/.../mc` and a small source preprocessor (`//#if MC>=1.21.5`); loader glue lives
in `mod/src/<loader>`. CI compiles every target and boots a dedicated server for each (smoke test); it has
not been played in game yet.

All rights reserved (c) 2026 arthou; see `LICENSE` and `THIRD_PARTY_NOTICES.md`.
