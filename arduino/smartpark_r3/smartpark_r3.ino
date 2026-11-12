/*
 * SmartPark IoT - Nodo Arduino UNO R3
 * -----------------------------------
 * Sensores de plaza (HC-SR04, ultrasónico), barreras (servos) y LEDs de reserva.
 * Se comunica con la app Android por Bluetooth (módulo HC-05 / HC-06, perfil SPP).
 *
 * Conexiones sugeridas:
 *   HC-05  TX  -> pin 10 (RX del Arduino)
 *   HC-05  RX  <- pin 11 (TX del Arduino) usando divisor de voltaje 1k/2k (el HC-05 trabaja a 3.3 V)
 *   HC-05  VCC -> 5V, GND -> GND
 *   Plaza 1: HC-SR04 TRIG -> 2, ECHO -> 3, LED ámbar -> 7
 *   Plaza 2: HC-SR04 TRIG -> 4, ECHO -> 5, LED ámbar -> 8
 *   Servo barrera ENTRADA -> 9
 *   Servo barrera SALIDA  -> 6
 *
 * Protocolo (líneas de texto terminadas en '\n'):
 *   Arduino -> App:  N,<8 hex>       desafío (nonce) de la conexión, respuesta a "H"
 *                    P,<n>,<0|1>     plaza n libre(0)/ocupada(1)   (n=1 => plaza 101 en la app)
 *                    B,<E|S>,<0|1>   barrera cerrada(0)/abierta(1)
 *                    E,AUTH          comando rechazado (firma o contador inválido)
 *                    E,BLOQUEO       demasiados rechazos: comandos bloqueados 60 s
 *   App -> Arduino:  H               pedir un desafío nuevo (sin firma)
 *                    ?               reenviar el estado completo (sin firma, solo lectura)
 *                    A,E | A,S       abrir barrera        } FIRMADOS:
 *                    C,E | C,S       cerrar barrera       }   <comando>|<contador>|<firma>
 *                    R,<n>,<0|1>     LED de reserva       }
 *
 * Autenticación del enlace (desafío-respuesta):
 *   firma = primeros 8 bytes en hex de HMAC-SHA256(CLAVE_ENLACE, "<desafío>|<contador>|<comando>")
 *   - El desafío cambia en cada "H", así un comando grabado en otra conexión no sirve.
 *   - El contador debe crecer en cada comando, así no se puede repetir uno dentro de la misma conexión.
 *   - Tras 5 rechazos seguidos se bloquean los comandos por 60 s (igual que el login de la app).
 *   La clave NO viaja por Bluetooth; en la app se escribe en la pantalla Conexiones.
 */
#include <SoftwareSerial.h>
#include <Servo.h>

SoftwareSerial bt(10, 11); // RX, TX

// Clave compartida con la app (pantalla Conexiones). Cámbiala antes de la demostración (mín. 8 caracteres).
const char CLAVE_ENLACE[] = "SmartPark2026";
const int MAX_RECHAZOS = 5;
const unsigned long BLOQUEO_MS = 60000UL;

const int NUM_PLAZAS = 2;
const int PIN_TRIG[NUM_PLAZAS] = {2, 4};
const int PIN_ECHO[NUM_PLAZAS] = {3, 5};
const int PIN_LED_RESERVA[NUM_PLAZAS] = {7, 8};

const int PIN_SERVO_ENTRADA = 9;
const int PIN_SERVO_SALIDA = 6;
const int ANGULO_CERRADA = 0;
const int ANGULO_ABIERTA = 90;

// Distancia (cm) bajo la cual se considera que hay un vehículo (ajustar a la maqueta)
const float UMBRAL_CM = 10.0;
// Lecturas iguales consecutivas necesarias para confirmar un cambio (filtra ruido)
const int LECTURAS_CONFIRMACION = 3;

Servo servoEntrada;
Servo servoSalida;

bool ocupada[NUM_PLAZAS];
int contadorCambio[NUM_PLAZAS];
bool entradaAbierta = false;
bool salidaAbierta = false;
String buffer = "";
unsigned long ultimaLectura = 0;

// Estado de la autenticación
char desafio[9] = "";            // 8 hex + '\0'; vacío = aún no hay sesión
unsigned long ultimoContador = 0;
int rechazos = 0;
unsigned long bloqueadoDesde = 0;
bool bloqueado = false;

// ---------------- SHA-256 y HMAC (FIPS 180-4 / RFC 2104), sin librerías externas ----------------
const uint32_t K256[64] PROGMEM = {
  0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
  0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
  0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
  0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
  0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
  0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
  0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
  0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2
};

struct Sha256 {
  uint32_t h[8];
  uint8_t bloque[64];
  uint8_t usados;
  uint32_t totalBytes;
};

#define ROTR(x, n) (((x) >> (n)) | ((x) << (32 - (n))))

void shaProcesar(Sha256 &c) {
  uint32_t w[16];
  for (int i = 0; i < 16; i++) {
    w[i] = ((uint32_t)c.bloque[i * 4] << 24) | ((uint32_t)c.bloque[i * 4 + 1] << 16) |
           ((uint32_t)c.bloque[i * 4 + 2] << 8) | c.bloque[i * 4 + 3];
  }
  uint32_t a = c.h[0], b = c.h[1], cc = c.h[2], d = c.h[3], e = c.h[4], f = c.h[5], g = c.h[6], hh = c.h[7];
  for (int i = 0; i < 64; i++) {
    uint32_t wi;
    if (i < 16) {
      wi = w[i];
    } else { // ventana circular de 16 palabras: ahorra RAM en el UNO
      uint32_t w15 = w[(i - 15) & 15], w2 = w[(i - 2) & 15];
      uint32_t s0 = ROTR(w15, 7) ^ ROTR(w15, 18) ^ (w15 >> 3);
      uint32_t s1 = ROTR(w2, 17) ^ ROTR(w2, 19) ^ (w2 >> 10);
      wi = w[i & 15] = w[i & 15] + s0 + w[(i - 7) & 15] + s1;
    }
    uint32_t S1 = ROTR(e, 6) ^ ROTR(e, 11) ^ ROTR(e, 25);
    uint32_t ch = (e & f) ^ (~e & g);
    uint32_t t1 = hh + S1 + ch + pgm_read_dword(&K256[i]) + wi;
    uint32_t S0 = ROTR(a, 2) ^ ROTR(a, 13) ^ ROTR(a, 22);
    uint32_t maj = (a & b) ^ (a & cc) ^ (b & cc);
    uint32_t t2 = S0 + maj;
    hh = g; g = f; f = e; e = d + t1; d = cc; cc = b; b = a; a = t1 + t2;
  }
  c.h[0] += a; c.h[1] += b; c.h[2] += cc; c.h[3] += d; c.h[4] += e; c.h[5] += f; c.h[6] += g; c.h[7] += hh;
}

void shaIniciar(Sha256 &c) {
  const uint32_t h0[8] = {0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19};
  for (int i = 0; i < 8; i++) c.h[i] = h0[i];
  c.usados = 0;
  c.totalBytes = 0;
}

void shaAgregar(Sha256 &c, const uint8_t *datos, size_t largo) {
  for (size_t i = 0; i < largo; i++) {
    c.bloque[c.usados++] = datos[i];
    c.totalBytes++;
    if (c.usados == 64) { shaProcesar(c); c.usados = 0; }
  }
}

void shaTerminar(Sha256 &c, uint8_t salida[32]) {
  uint32_t bits = c.totalBytes * 8;
  uint8_t uno = 0x80, cero = 0;
  shaAgregar(c, &uno, 1);
  while (c.usados != 56) shaAgregar(c, &cero, 1);
  uint8_t largo[8] = {0, 0, 0, 0, (uint8_t)(bits >> 24), (uint8_t)(bits >> 16), (uint8_t)(bits >> 8), (uint8_t)bits};
  shaAgregar(c, largo, 8);
  for (int i = 0; i < 8; i++) {
    salida[i * 4] = c.h[i] >> 24; salida[i * 4 + 1] = c.h[i] >> 16;
    salida[i * 4 + 2] = c.h[i] >> 8; salida[i * 4 + 3] = c.h[i];
  }
}

// HMAC-SHA256 con clave corta (< 64 bytes), suficiente para CLAVE_ENLACE
void hmacSha256(const char *clave, const char *mensaje, uint8_t salida[32]) {
  uint8_t pad[64];
  size_t largoClave = strlen(clave);
  Sha256 c;
  for (int i = 0; i < 64; i++) pad[i] = (i < (int)largoClave ? clave[i] : 0) ^ 0x36;
  shaIniciar(c); shaAgregar(c, pad, 64); shaAgregar(c, (const uint8_t *)mensaje, strlen(mensaje));
  uint8_t interno[32];
  shaTerminar(c, interno);
  for (int i = 0; i < 64; i++) pad[i] = (i < (int)largoClave ? clave[i] : 0) ^ 0x5c;
  shaIniciar(c); shaAgregar(c, pad, 64); shaAgregar(c, interno, 32);
  shaTerminar(c, salida);
}

// ---------------- Autenticación de comandos ----------------
void nuevoDesafio() {
  // Se mezcla el generador con el instante exacto de la petición (micros), distinto en cada conexión
  randomSeed(random(0x7FFFFFFFL) ^ micros());
  for (int i = 0; i < 8; i++) desafio[i] = "0123456789abcdef"[random(16)];
  desafio[8] = '\0';
  ultimoContador = 0;
  bt.print("N,"); bt.println(desafio);
}

void rechazar() {
  rechazos++;
  if (rechazos >= MAX_RECHAZOS) {
    bloqueado = true;
    bloqueadoDesde = millis();
    bt.println("E,BLOQUEO");
    Serial.println("Comandos BLOQUEADOS por 60 s");
  } else {
    bt.println("E,AUTH");
  }
}

// Recibe "<comando>|<contador>|<firma>" y deja en 'comando' solo el comando si la firma es válida
bool verificarFirma(const String &linea, String &comando) {
  if (bloqueado && millis() - bloqueadoDesde < BLOQUEO_MS) { bt.println("E,BLOQUEO"); return false; }
  if (bloqueado) { bloqueado = false; rechazos = 0; }
  int s1 = linea.indexOf('|');
  int s2 = linea.indexOf('|', s1 + 1);
  if (desafio[0] == '\0' || s1 < 0 || s2 < 0) { rechazar(); return false; }
  comando = linea.substring(0, s1);
  String textoContador = linea.substring(s1 + 1, s2);
  String firma = linea.substring(s2 + 1);
  unsigned long contador = strtoul(textoContador.c_str(), NULL, 10);
  if (contador <= ultimoContador || firma.length() != 16) { rechazar(); return false; }

  String mensaje = String(desafio) + "|" + textoContador + "|" + comando;
  uint8_t mac[32];
  hmacSha256(CLAVE_ENLACE, mensaje.c_str(), mac);
  const char *hex = "0123456789abcdef";
  uint8_t diferencia = 0; // comparación en tiempo constante
  for (int i = 0; i < 8; i++) {
    diferencia |= firma.charAt(i * 2) ^ hex[mac[i] >> 4];
    diferencia |= firma.charAt(i * 2 + 1) ^ hex[mac[i] & 0x0f];
  }
  if (diferencia != 0) { rechazar(); return false; }
  ultimoContador = contador;
  rechazos = 0;
  return true;
}

float medirDistanciaCm(int trig, int echo) {
  digitalWrite(trig, LOW);
  delayMicroseconds(2);
  digitalWrite(trig, HIGH);
  delayMicroseconds(10);
  digitalWrite(trig, LOW);
  unsigned long duracion = pulseIn(echo, HIGH, 25000UL); // máx ~4 m
  if (duracion == 0) return 999.0;                       // sin eco = nada cerca
  return duracion * 0.0343 / 2.0;
}

void enviarPlaza(int i) {
  bt.print("P,"); bt.print(i + 1); bt.print(","); bt.println(ocupada[i] ? 1 : 0);
}

void enviarBarrera(char codigo, bool abierta) {
  bt.print("B,"); bt.print(codigo); bt.print(","); bt.println(abierta ? 1 : 0);
}

void enviarEstadoCompleto() {
  for (int i = 0; i < NUM_PLAZAS; i++) enviarPlaza(i);
  enviarBarrera('E', entradaAbierta);
  enviarBarrera('S', salidaAbierta);
}

void moverBarrera(char codigo, bool abrir) {
  if (codigo == 'E') {
    servoEntrada.write(abrir ? ANGULO_ABIERTA : ANGULO_CERRADA);
    entradaAbierta = abrir;
  } else if (codigo == 'S') {
    servoSalida.write(abrir ? ANGULO_ABIERTA : ANGULO_CERRADA);
    salidaAbierta = abrir;
  } else {
    return;
  }
  enviarBarrera(codigo, abrir);
}

void procesarComando(String linea) {
  linea.trim();
  if (linea.length() == 0) return;
  Serial.print("<- "); Serial.println(linea);

  if (linea == "H") { nuevoDesafio(); return; }
  if (linea == "?") { enviarEstadoCompleto(); return; }

  // Todo lo que mueve algo físico debe venir firmado
  String cmd;
  if (!verificarFirma(linea, cmd)) { Serial.println("   rechazado"); return; }

  if ((cmd.startsWith("A,") || cmd.startsWith("C,")) && cmd.length() >= 3) {
    moverBarrera(cmd.charAt(2), cmd.charAt(0) == 'A');
    return;
  }

  if (cmd.startsWith("R,")) {
    int coma = cmd.indexOf(',', 2);
    if (coma < 0) return;
    int n = cmd.substring(2, coma).toInt();
    bool reservada = cmd.substring(coma + 1).toInt() == 1;
    if (n >= 1 && n <= NUM_PLAZAS) digitalWrite(PIN_LED_RESERVA[n - 1], reservada ? HIGH : LOW);
  }
}

void setup() {
  Serial.begin(9600);   // Monitor serie (depuración)
  bt.begin(9600);       // Velocidad por defecto del HC-05 en modo datos

  for (int i = 0; i < NUM_PLAZAS; i++) {
    pinMode(PIN_TRIG[i], OUTPUT);
    pinMode(PIN_ECHO[i], INPUT);
    pinMode(PIN_LED_RESERVA[i], OUTPUT);
    ocupada[i] = false;
    contadorCambio[i] = 0;
  }
  servoEntrada.attach(PIN_SERVO_ENTRADA);
  servoSalida.attach(PIN_SERVO_SALIDA);
  // Fallo seguro (OT): al encender, las barreras quedan cerradas
  servoEntrada.write(ANGULO_CERRADA);
  servoSalida.write(ANGULO_CERRADA);

  // Semilla inicial: ruido de los pines analógicos A0/A1 sin conectar
  randomSeed(analogRead(A0) ^ ((unsigned long)analogRead(A1) << 10) ^ micros());

  Serial.println("SmartPark R3 listo");
}

void loop() {
  // 1) Leer comandos de la app
  while (bt.available()) {
    char c = bt.read();
    if (c == '\n') { procesarComando(buffer); buffer = ""; }
    else if (c != '\r' && buffer.length() < 48) buffer += c;
  }

  // 2) Leer sensores cada 200 ms y avisar solo cuando hay cambios confirmados
  if (millis() - ultimaLectura >= 200) {
    ultimaLectura = millis();
    for (int i = 0; i < NUM_PLAZAS; i++) {
      bool lectura = medirDistanciaCm(PIN_TRIG[i], PIN_ECHO[i]) < UMBRAL_CM;
      if (lectura != ocupada[i]) {
        if (++contadorCambio[i] >= LECTURAS_CONFIRMACION) {
          ocupada[i] = lectura;
          contadorCambio[i] = 0;
          enviarPlaza(i);
          Serial.print("Plaza "); Serial.print(i + 1); Serial.println(lectura ? " OCUPADA" : " LIBRE");
        }
      } else {
        contadorCambio[i] = 0;
      }
      delay(30); // evita interferencia entre sensores ultrasónicos
    }
  }
}
