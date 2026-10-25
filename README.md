# SmartPark IoT – Estacionamiento Inteligente

App Android (Kotlin + Jetpack Compose) para el ramo **TI3042 Aplicaciones Móviles para IoT** (Inacap).
Una misma APK se instala en dos teléfonos: uno inicia sesión como **Conductor** y el otro como **Operador**.
Se comunican por **Wi-Fi** o **Bluetooth**, y opcionalmente con una placa **Arduino UNO R3 + HC-05**.

## Compatibilidad

| Componente | Versión | Motivo |
|---|---|---|
| Android Studio | Hedgehog 2023.1 o más nuevo | AGP 8.2 es soportado desde Hedgehog |
| Android Gradle Plugin | 8.2.2 | estable y ampliamente soportado |
| Gradle | 8.7 | funciona con JDK 17 y JDK 21 (el que trae Android Studio) |
| Kotlin | 1.9.22 (Compose Compiler 1.5.10) | sin plugins extra |
| compileSdk / targetSdk | 34 | |
| minSdk | 26 (Android 8.0) | PBKDF2-SHA256 y Base64 nativos |

No se usan Room, KSP, kapt, Hilt ni Firebase: menos plugins = menos problemas de compatibilidad.
Si Android Studio ofrece "actualizar AGP", se puede rechazar sin problema.

## Cómo abrir el proyecto

1. Clona el repositorio o descarga el ZIP y abre la carpeta raíz en Android Studio (la que contiene `settings.gradle.kts`).
2. Si Android Studio es muy nuevo (trae Java 25), elige **Settings > Build Tools > Gradle > Gradle JDK = 17 o 21**.
   Gradle 8.7 no funciona con Java 25.
3. Sincroniza con el elefante (*Sync Project with Gradle Files*) y pulsa **Run**.
4. Si Android Studio ofrece actualizar AGP, recházalo para mantener la compatibilidad con equipos antiguos.

## Cuentas de prueba

| Usuario | Contraseña | Rol |
|---|---|---|
| `operador` | `Operador2026` | Operador |
| `conductor` | `Conductor2026` | Conductor (patente ABCD12) |

También se pueden crear cuentas desde **Crear cuenta**. Para crear un Operador se pide el código de autorización `OT-2026`.

## Estructura (MVVM)

```
app/src/main/java/cl/inacap/estacionamiento/
├── MainActivity.kt            Punto de entrada
├── ServiceLocator.kt          Inyección de dependencias manual
├── data/
│   ├── model/Modelos.kt       Plaza, Usuario, Rol, Barrera, Evento, Sesion
│   ├── local/SmartParkDb.kt   SQLite: usuarios, plazas, eventos
│   └── repository/            AuthRepository (login) y ParkingRepository (lógica del estacionamiento)
├── comm/                      Capa inalámbrica intercambiable
│   ├── ConexionIoT.kt         Interfaz común + reconexión automática
│   ├── WifiConexion.kt        Socket TCP (servidor Operador / cliente Conductor)
│   ├── BluetoothConexion.kt   Bluetooth clásico SPP (teléfono o HC-05)
│   └── Protocolo.kt           Mensajes con el Arduino y entre teléfonos
├── security/
│   ├── PasswordHasher.kt      PBKDF2-HMAC-SHA256 con sal
│   ├── SessionManager.kt      Sesión cifrada AES-256-GCM con llave en Android Keystore
│   ├── CanalSeguro.kt         Cifrado + firma HMAC + anti-replay de mensajes
│   └── Validaciones.kt        Reglas de formularios
└── ui/
    ├── screens/               Login, Registro, Conductor, Operador, Conexión
    ├── viewmodel/             AuthViewModel, ParkingViewModel
    ├── navigation/AppNav.kt   Navegación según rol
    ├── components/            Tarjetas de plaza, KPIs, indicadores
    └── theme/Theme.kt         Material 3 modo oscuro
arduino/smartpark_r3/          Sketch para Arduino UNO R3
```

## Demostración con dos teléfonos (Wi-Fi)

1. Instala la app en ambos teléfonos.
2. Teléfono 2: entra como `operador` → ⚙ Conexión → Wi-Fi → **Iniciar servidor Wi-Fi**. Anota la **IP** y el **PIN**.
3. Teléfono 1: entra como `conductor` → ⚙ Conexión → Wi-Fi → escribe IP y PIN → **Conectar**.
4. El conductor reserva una plaza: el operador la ve en ámbar, baja el aforo y aparece en el log.
5. El conductor pulsa **Abrir barrera de acceso**: el operador ve la barrera ABIERTA y se cierra sola a los 5 s.

> Si la red Wi-Fi de la sede no deja que los teléfonos se vean, activa la **zona Wi-Fi (hotspot)** en el teléfono del
> operador y conecta el otro teléfono a ella (IP habitual del operador: `192.168.43.1`).

Por **Bluetooth** es igual: vincula los teléfonos en Ajustes, el operador pulsa *Esperar conexión Bluetooth* y el
conductor elige al operador de la lista e ingresa el PIN.

## Demostración con Arduino UNO R3

1. Carga `arduino/smartpark_r3/smartpark_r3.ino` con Arduino IDE (librerías incluidas: SoftwareSerial y Servo).
2. Vincula el HC-05 en Ajustes > Bluetooth del teléfono (PIN `1234` o `0000`).
3. En la app (idealmente en el Operador): ⚙ Conexión → *Placa Arduino* → elige el HC-05.
4. Al tapar un sensor HC-SR04, la plaza 101/102 pasa a **Ocupada** en ambos teléfonos.
   Al abrir una barrera desde la app, el servo se mueve.

El Operador puede tener al mismo tiempo el enlace con el Conductor y con el Arduino: actúa como *gateway* y
reenvía todo al Conductor.

## Seguridad (ISO/IEC 27400 y lineamientos OT)

| Control | Implementación |
|---|---|
| Credenciales cifradas | Contraseñas con PBKDF2-HMAC-SHA256 (60.000 iteraciones, sal aleatoria). Nunca en texto plano. |
| Fuerza bruta | 5 intentos fallidos → bloqueo de 60 s. Mensaje de error genérico. |
| Política de contraseñas | Mínimo 8 caracteres con letras y números. |
| Datos en reposo | Sesión cifrada AES-256-GCM, llave en Android Keystore. Respaldo en la nube desactivado. |
| Control de acceso (RBAC) | Conductor solo reserva y abre con reserva vigente. Operador controla barreras y plazas. Crear Operador requiere código. |
| Datos en tránsito | Cada mensaje entre teléfonos va cifrado con AES-256-GCM y firmado con HMAC-SHA256 (llaves derivadas del PIN). |
| Ataques de repetición | Cada mensaje lleva marca de tiempo (vigencia 30 s) y nonce de un solo uso. |
| Auditoría | Log de eventos en SQLite (sensores, reservas, barreras, seguridad, conexiones) con filtros. |
| Fallo seguro (OT) | Al encender, el Arduino deja las barreras cerradas. Las conexiones se reintentan solas. |
| Red | `network_security_config` prohíbe tráfico HTTP sin cifrar. |

## Pruebas

`app/src/test/.../SeguridadTest.kt` prueba el hash de contraseñas, las validaciones, el cifrado y la firma de
mensajes, el rechazo de mensajes alterados, repetidos o vencidos, y el protocolo del Arduino.
Ejecutar desde Android Studio (clic derecho → *Run tests*) o con `gradlew test`.
