# AmarradosRevive

Plugin para **Paper 1.21.11** que reemplaza a **PlayerRevive**. Cuando un jugador recibe un golpe
mortal no muere: queda **derribado**, arrastrándose por el suelo, y sus compañeros pueden revivirlo.

## Qué hace

- **Tiempo para morir en pantalla:** el jugador derribado ve una barra de jefe roja arriba
  con la cuenta atrás (`☠ Te desangras: 0:45`) que se va vaciando.
- **Ver quién te revive:** cuando alguien le está reviviendo aparece una segunda barra verde
  (`✚ Pepe te esta reviviendo... 60%`) y un mensaje en la barra de acción. La barra roja se pone
  amarilla porque el tiempo se pausa mientras te reviven.
- El que revive también ve su progreso: `✚ Reviviendo a Juan [▮▮▮▮▮▮▯▯▯▯] 60%`.
- Los jugadores cercanos ven un aviso de que hay alguien derribado y cuánto le queda.
- **Revivir:** agáchate (shift) a menos de 2 bloques del derribado durante 5 segundos.
- **Derribado no puede:** poner bloques (ni cubos, barcos, soportes, cuadros...), romper bloques,
  usar/abrir cosas, atacar, comer, disparar (ni perlas), montar, planear con élitros, saltar,
  ni usar `/spawn`, `/home`, `/tpa`... (todo configurable).
- Se arrastra (pose de nadar), va lento, los mobs le ignoran y no recibe daño mientras espera.
- `/rendirse` para morir ya.

## Arreglo del bug de "inmortal"

Lo típico en plugins de revivir es que el jugador se quede con la marca de invulnerable o con
un estado "derribado" que nunca se borra. Este plugin:

- **Nunca** usa la marca de invulnerable: la protección es solo cancelar el daño mientras está
  en la lista de derribados, que se revisa cada tick.
- Si muere por cualquier causa, se desconecta, le cambian a creativo, o se apaga el servidor,
  deja de estar derribado.
- Lentitud y salto van con modificadores temporales que no se guardan en disco.
- Hay un límite absoluto de tiempo derribado: aunque alguien pause la cuenta sin parar, muere igual.
- `/kill`, caer al vacío y el borde del mundo matan directamente.
- Al entrar, si un jugador en supervivencia tiene la marca de invulnerable (por el PlayerRevive viejo)
  se le quita automáticamente (`corregir-jugadores-inmortales`).

## Compatibilidad con tus plugins

- **AmarradosCadenas**: funciona igual que con PlayerRevive. El golpe mortal se cancela,
  así que con *vida compartida* el resto del equipo no muere cuando uno cae derribado.
  Si el derribado **muere de verdad**, AmarradosCadenas mata al resto con 1000 de daño y ese golpe
  (`dano-muerte-directa: 1000`) les mata directamente, como antes ("si uno muere, mueren todos").
  La cadena puede arrastrar al derribado y sujetarlo si cae (en el aire no se fuerza la pose).
  Ojo: agacharse para anclar la cadena junto a un compañero derribado también lo revive.
- LuckPerms, TAB, ChatEx, Vault, Simple Voice Chat, ProtocolLib, Chunky y spark: sin conflictos.
  No usa ProtocolLib ni resource pack.
- **PlayerRevive**: hay que **quitarlo**. Si están los dos, el plugin avisa en consola.

## Comandos y permisos

| Comando | Qué hace | Permiso |
|---|---|---|
| `/rendirse` | Morir si estás derribado | `amarradosrevive.rendirse` (todos) |
| `/revive revivir <jugador>` | Levantar a un derribado | `amarradosrevive.admin` |
| `/revive matar <jugador>` | Matar a un derribado | `amarradosrevive.admin` |
| `/revive derribar <jugador>` | Derribar a alguien (para probar) | `amarradosrevive.admin` |
| `/revive lista` | Ver quién está derribado | `amarradosrevive.admin` |
| `/revive recargar` | Recargar `config.yml` | `amarradosrevive.admin` |

- `amarradosrevive.revivir` (todos): poder revivir a otros.
- `amarradosrevive.exento` (nadie por defecto): nunca queda derribado, muere normal.

Todo se ajusta en `plugins/AmarradosRevive/config.yml` (tiempos, distancia, vida, mensajes...).

## Instalarlo en HolyHosting

1. Descarga `AmarradosRevive-1.0.0.jar` desde la pestaña **Actions** (artefacto) o **Releases**.
2. **Para el servidor** (Stop).
3. En **Files** → `plugins/`, borra el `.jar` de **PlayerRevive** (la carpeta `PlayerRevive` también puedes borrarla).
4. Sube `AmarradosRevive-1.0.0.jar` a `plugins/`.
5. **Arranca** el servidor. No uses `/reload`.
6. En la consola debe salir `AmarradosRevive activado`. Prueba con `/revive derribar <tu nombre>`.

## Compilar

```
mvn package
```

Necesitas Java 21. El jar queda en `target/`. GitHub Actions lo compila solo en cada push.
