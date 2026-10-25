/*
 * SmartPark IoT - Nodo Arduino UNO R3
 * -----------------------------------
 * Sensores de plaza (HC-SR04), barreras (servos) y LEDs de reserva.
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
 *   Arduino -> App:  P,<n>,<0|1>     plaza n libre(0)/ocupada(1)   (n=1 => plaza 101 en la app)
 *                    B,<E|S>,<0|1>   barrera cerrada(0)/abierta(1)
 *   App -> Arduino:  A,E | A,S       abrir barrera
 *                    C,E | C,S       cerrar barrera
 *                    R,<n>,<0|1>     LED de reserva de la plaza n
 *                    ?               reenviar el estado completo
 */
#include <SoftwareSerial.h>
#include <Servo.h>

SoftwareSerial bt(10, 11); // RX, TX

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

void procesarComando(String cmd) {
  cmd.trim();
  if (cmd.length() == 0) return;
  Serial.print("<- "); Serial.println(cmd);

  if (cmd == "?") { enviarEstadoCompleto(); return; }

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

  Serial.println("SmartPark R3 listo");
}

void loop() {
  // 1) Leer comandos de la app
  while (bt.available()) {
    char c = bt.read();
    if (c == '\n') { procesarComando(buffer); buffer = ""; }
    else if (c != '\r' && buffer.length() < 32) buffer += c;
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
