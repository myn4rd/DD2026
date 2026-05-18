# Ameribank — Catálogo de Vulnerabilidades

> Inventario de las vulnerabilidades identificadas en el aplicativo, la base de datos y la cadena de defensa (Nginx, Snort). Cada entrada incluye severidad, ubicación, impacto, estado actual y mitigación recomendada o aplicada.
>
> El alcance es **el código y las configuraciones**, no la infraestructura física.

---

## Tabla de Contenidos

- [Metodología y clasificación](#metodología-y-clasificación)
- [Resumen ejecutivo](#resumen-ejecutivo)
- [Vulnerabilidades críticas](#vulnerabilidades-críticas)
- [Vulnerabilidades altas](#vulnerabilidades-altas)
- [Vulnerabilidades medias](#vulnerabilidades-medias)
- [Vulnerabilidades bajas y observaciones](#vulnerabilidades-bajas-y-observaciones)
- [Resumen de estado por vulnerabilidad](#resumen-de-estado-por-vulnerabilidad)
- [Recomendaciones priorizadas](#recomendaciones-priorizadas)

---

## Metodología y clasificación

Las vulnerabilidades fueron identificadas mediante revisión manual del código fuente (`src/main/java/`), las configuraciones (`scripts/nginx-ameribank.conf`, `scripts/snort-local.rules`), el esquema de base de datos (`ameribank_full_db.sql`) y los datos de prueba.

**Niveles de severidad:**

| Severidad | Criterio |
|-----------|----------|
| **Crítica** | Compromiso directo de credenciales, datos sensibles o disponibilidad total del servicio sin requerir privilegios |
| **Alta** | Permite escalación, denegación de servicio significativa, o exposición de información con esfuerzo bajo |
| **Media** | Requiere condiciones específicas o acceso parcial; impacto limitado |
| **Baja** | Higiene de código, defensa en profundidad, mejor práctica no cumplida |

**Estados:**

- ✅ **Mitigado** — corregido durante el hardening, no requiere acción adicional
- 🟡 **Parcial** — mitigado parcialmente, queda riesgo residual
- ❌ **Pendiente** — no corregido, requiere trabajo posterior
- 📌 **Por diseño** — comportamiento intencional para la demo, debe arreglarse antes de producción

---

## Resumen ejecutivo

Se identificaron **17 vulnerabilidades** distribuidas de la siguiente forma:

| Severidad | Cantidad | Mitigadas | Parciales | Pendientes |
|-----------|---------:|----------:|----------:|-----------:|
| Crítica | 4 | 2 | 0 | 2 |
| Alta | 6 | 2 | 2 | 2 |
| Media | 5 | 0 | 0 | 5 |
| Baja | 2 | 0 | 1 | 1 |
| **Total** | **17** | **4** | **3** | **10** |

Las vulnerabilidades más graves no resueltas pertenecen a la familia de **almacenamiento y manejo de credenciales** (passwords en texto plano en la base de datos, 2FA débil) y son inherentes al diseño actual del esquema de autenticación. Su corrección requiere refactor del flujo de login, no solo cambios de configuración.

---

## Vulnerabilidades críticas

### V-001 — Contraseñas almacenadas en texto plano

**Severidad:** Crítica
**Estado:** ❌ Pendiente
**Ubicación:** `ameribank_full_db.sql` líneas 17-22, 491-494

**Descripción:**
La tabla `usuarios` define la columna `pwd VARCHAR(255)` sin restricción de formato. Los datos de prueba insertan contraseñas en texto plano: `admin/admin123`, `cliente1/cliente123`, `cliente2/cliente456`. El stored procedure `login` compara directamente:

```sql
WHERE usr = p_usr AND pwd = p_pwd
```

**Impacto:**
Cualquier persona con acceso a la base de datos (DBA, backups, logs de queries) ve todas las contraseñas. El `general_log` habilitado en MySQL **escribe las queries con las contraseñas** al log que va a Splunk.

**Mitigación recomendada:**
- Almacenar `pwd` como hash con `bcrypt` (cost ≥ 12) o `argon2id`.
- Mover la verificación a la capa de aplicación (`BCrypt.checkpw()`).
- Eliminar `general_log` o filtrar queries de login antes de enviar a Splunk.

---

### V-002 — Códigos 2FA y secretos TOTP en texto plano

**Severidad:** Crítica
**Estado:** ❌ Pendiente
**Ubicación:** `ameribank_full_db.sql` líneas 92-101 (tabla `autenticacion_2fa`), 104-113 (tabla `blacklisted_totps`)

**Descripción:**
La columna `codigo_secreto VARCHAR(255)` guarda el secreto TOTP del usuario sin cifrado. La tabla `blacklisted_totps` mantiene los códigos usados también en plano.

**Impacto:**
Comprometer la base de datos revela el factor secundario completo, anulando el propósito del 2FA. Los códigos en blacklist permitirían a un atacante reconstruir el patrón de uso del usuario.

**Mitigación recomendada:**
- Cifrar el `codigo_secreto` con una clave maestra del aplicativo (KMS/HSM o llave derivada).
- Almacenar solo hash de los TOTP en `blacklisted_totps` (no se necesita el código original, solo verificar si ya se usó).

---

### V-003 — Inicialización estática de `ConexionDB` con `System.exit`

**Severidad:** Crítica (era)
**Estado:** ✅ Mitigado
**Ubicación previa:** `src/main/java/org/amerike/ameribank/config/ConexionDB.java` y `security.java`

**Descripción original:**
Los campos `static final URL/USER/PASS` se inicializaban al cargar la clase, llamando 3 veces consecutivas a `obtenerCredenciales()` (lectura + descifrado RSA). Cualquier fallo invocaba `System.exit(1)`, terminando la JVM sin posibilidad de recuperación. Si un DAO se referenciaba durante el bootstrap de Spring antes de `security.init()`, el proceso moría.

**Mitigación aplicada:**
- `ConexionDB` reescrito con `HikariDataSource` lazy-initialized (doble-checked locking) que descifra credenciales **una sola vez**.
- `obtenerUrl()`, `obtenerUsuario()`, `obtenerPassword()` ahora lanzan `IllegalStateException` en lugar de `System.exit(1)`.

---

### V-004 — Sin pool de conexiones (amplificador de DDoS)

**Severidad:** Crítica (era)
**Estado:** ✅ Mitigado
**Ubicación previa:** `src/main/java/org/amerike/ameribank/config/ConexionDB.java`

**Descripción original:**
Cada llamada DAO ejecutaba `DriverManager.getConnection()`, abriendo una conexión TCP nueva, autenticando contra MySQL, y cerrándola al terminar. Bajo carga moderada:

- Los file descriptors del proceso Java se agotaban.
- MySQL alcanzaba `max_connections=151` rápidamente.
- Cada handshake añadía latencia al ataque, ayudándolo.

**Mitigación aplicada:**
HikariCP con `maximumPoolSize=30`, `minimumIdle=5`, validación, y caché de prepared statements. Detalles en `HARDENING.md`.

---

## Vulnerabilidades altas

### V-005 — Sin timeouts en Tomcat (susceptible a slowloris)

**Severidad:** Alta (era)
**Estado:** ✅ Mitigado
**Ubicación previa:** `src/main/resources/application.properties`

**Descripción original:**
Tomcat embebido usaba los defaults: `connection-timeout=20s`, sin límite explícito de `max-connections`, sin tope de `keep-alive`. Un atacante con `slowhttptest` o `slowloris.py` agotaba los 200 threads en segundos.

**Mitigación aplicada:**
- `server.tomcat.connection-timeout=5000`
- `server.tomcat.keep-alive-timeout=10000`
- `server.tomcat.max-connections=400` con `accept-count=100`
- Límites de POST body a 2MB

---

### V-006 — Reglas de Snort solo en modo `alert`

**Severidad:** Alta (era)
**Estado:** ✅ Mitigado
**Ubicación previa:** `INFRAESTRUCTURA.md` sección Snort, regla original `sid:1000002`

**Descripción original:**
La regla de HTTP flood usaba `alert tcp ...`, lo que solo escribe al log de Snort sin descartar el paquete. El backup quedaba sin protección efectiva aunque la regla "disparara". Adicionalmente, la única regla `drop` (sid:1000003) solo aplicaba a paquetes con bandera SYN, inútil contra HTTP flood post-handshake.

**Mitigación aplicada:**
Archivo `scripts/snort-local.rules` con reglas `drop` específicas para:
- SYN flood (sid:1000010)
- HTTP GET flood (sid:1000003, rev:2)
- HTTP POST flood (sid:1000004)
- Slowloris (sid:1000005)

---

### V-007 — JDBC URL hardcodeado dentro del blob cifrado

**Severidad:** Alta
**Estado:** ❌ Pendiente
**Ubicación:** `src/main/java/org/amerike/ameribank/config/security.java` línea 71

**Descripción:**
El método `cifrador()` construye el payload con la IP de MySQL hardcodeada:

```java
String datos = String.format(
    "\"jdbc:mysql://40.0.4.12:3306/%s\"|\"%s\"|\"%s\"",
    NombreBaseDeDatos, User, Password);
```

**Impacto:**
- Cambiar el host de MySQL requiere recompilar el código.
- Regenerar las credenciales cifradas borra y rehace el blob completo, perdiendo la configuración previa.
- En el entorno actual (Docker o cambio de IPs), bloquea movilidad.

**Mitigación recomendada:**
- Cifrar únicamente `user|pass`, leer la URL desde `application.properties` o variable de entorno.
- Soportar variable de entorno `AMERIBANK_DB_URL` como fallback.

---

### V-008 — Llave privada RSA sin passphrase

**Severidad:** Alta
**Estado:** 🟡 Parcial
**Ubicación:** `src/main/java/org/amerike/ameribank/config/security.java` método `guardarClavePEM`

**Descripción:**
La llave privada `~/.config/ameribank/secrets/private_key.pem` se guarda en formato PKCS#8 sin encriptación. Cualquiera con acceso al filesystem del proceso puede descifrar el blob de credenciales.

**Estado de mitigación parcial:**
El script de deploy aplica `chmod 600` a los archivos y `chmod 700` al directorio padre, pero esto solo protege contra otros usuarios del mismo host, no contra escalación a root, snapshots de disco, backups, o el propio proceso si es comprometido.

**Mitigación recomendada:**
- Encriptar la llave privada con passphrase derivada de una variable de entorno o secret manager.
- Considerar mover el cifrado de credenciales a HashiCorp Vault, AWS KMS, o equivalente.

---

### V-009 — Setup de credenciales requiere stdin interactivo

**Severidad:** Alta
**Estado:** 🟡 Parcial
**Ubicación:** `src/main/java/org/amerike/ameribank/config/security.java` método `escribirCredenciales`

**Descripción:**
La primera ejecución del aplicativo usa `Scanner(System.in)` para pedir credenciales. Esto es incompatible con:
- Arranque como servicio systemd (sin TTY).
- Despliegue automatizado (CI/CD, Ansible, Docker).
- Reemplazo programático de credenciales (rotación periódica).

**Estado de mitigación parcial:**
El script de deploy (`scripts/deploy_ameribank.sh`) tiene un modo no-interactivo que pipea las credenciales por stdin, espera a que el archivo cifrado aparezca, y mata el proceso. Funciona pero es frágil (depende de polling de filesystem).

**Mitigación recomendada:**
- Agregar un modo CLI: `java -jar app.jar setup-credentials --db-name X --db-user Y --db-pass Z` que solo cifre y salga.
- O leer credenciales desde variables de entorno (`AMERIBANK_DB_*`).

---

### V-010 — `general_log` de MySQL captura passwords del login

**Severidad:** Alta
**Estado:** ❌ Pendiente
**Ubicación:** `scripts/install_mysql.sh` (habilita `general_log = 1`)

**Descripción:**
El log general de MySQL registra todas las queries ejecutadas, incluyendo las llamadas a `CALL login('admin', 'admin123')`. Este log se envía vía NFS a Splunk, donde quedan indexadas las contraseñas en plano.

**Impacto:**
Cualquier persona con acceso al SIEM (que típicamente tiene un círculo amplio de visualización) puede buscar `index=ameribank sourcetype=mysql:general "CALL login"` y obtener credenciales.

**Mitigación recomendada:**
- Deshabilitar `general_log` en producción (solo activar bajo demanda para troubleshooting).
- Alternativa: usar `filter_audit_log` plugin para excluir queries con datos sensibles.

---

## Vulnerabilidades medias

### V-011 — Sin transacción explícita en `RegistrarTransferencia`

**Severidad:** Media
**Estado:** ❌ Pendiente
**Ubicación:** `ameribank_full_db.sql` líneas 240-262

**Descripción:**
El stored procedure ejecuta:

```sql
UPDATE cuentas SET saldo = saldo - p_monto WHERE numero_cuenta = p_cuenta_origen;
UPDATE cuentas SET saldo = saldo + p_monto WHERE numero_cuenta = p_cuenta_destino;
INSERT INTO movimientos ...;
```

Sin `START TRANSACTION ... COMMIT/ROLLBACK` explícito. Bajo concurrencia o si el segundo UPDATE falla, el dinero "desaparece" de la cuenta origen sin llegar al destino.

**Mitigación recomendada:**
- Envolver el procedimiento en `START TRANSACTION` y manejar errores con `DECLARE EXIT HANDLER FOR SQLEXCEPTION ROLLBACK`.
- Verificar atomicidad bajo carga concurrente con pruebas de stress.

---

### V-012 — `event_scheduler` con intervalo agresivo (cada 5s)

**Severidad:** Media
**Estado:** ❌ Pendiente
**Ubicación:** `ameribank_full_db.sql` líneas 468-484

**Descripción:**
El evento `ev_expire_2fa_codes` se ejecuta cada 5 segundos, haciendo un JOIN entre `autenticacion_2fa` y `blacklisted_totps` sin índice compuesto óptimo. Bajo carga, este job:

- Compite por locks con los logins.
- La tabla `blacklisted_totps` crece sin límite (no hay purge), degradando el JOIN con el tiempo.

**Mitigación recomendada:**
- Subir el intervalo a `EVERY 30 SECOND` (los códigos 2FA se invalidan en 30s, no necesita verificación tan frecuente).
- Agregar índice compuesto `(usuario_id, codigo_secreto)` en `autenticacion_2fa`.
- Implementar purge: borrar registros de `blacklisted_totps` con `blacklisted_at < NOW() - INTERVAL 1 HOUR`.

---

### V-013 — Sin rate limiting de aplicación contra brute force al login

**Severidad:** Media
**Estado:** ❌ Pendiente
**Ubicación:** Toda la cadena de login (`loginController`, `loginDAO`, `sp login`)

**Descripción:**
El proceso de login no implementa:
- Conteo de intentos fallidos por usuario.
- Bloqueo temporal tras N intentos.
- CAPTCHA o desafío adicional.

Combinado con V-001 (passwords sin hash) y el bajo costo de validación, un atacante puede probar miles de combinaciones por segundo.

**Mitigación parcial:** El `limit_req` de Nginx en producción (rate=30r/s por IP) acota el ritmo, pero no impide enumeración por múltiples IPs.

**Mitigación recomendada:**
- Tabla `login_intentos (usuario_id, ip, fecha, exitoso)` con índice por usuario.
- Bloqueo de 15 minutos tras 5 fallos consecutivos.
- CAPTCHA después de 3 fallos.

---

### V-014 — Nginx como punto único de falla

**Severidad:** Media
**Estado:** ❌ Pendiente (arquitectónica)
**Ubicación:** Topología completa

**Descripción:**
Toda la cadena de defensa depende de un único nodo Nginx (40.0.4.1). Si Nginx cae (por bug, OOM, ataque dirigido al puerto 80), ambos upstream se vuelven inalcanzables, anulando el valor del failover.

**Mitigación recomendada:**
- Dos instancias Nginx detrás de un balanceador L4 (HAProxy, IPVS) o VIP con keepalived.
- En el contexto Docker actual: dos contenedores Nginx con `docker-compose` y un balanceador delante.

---

### V-015 — Convenciones de nombres y clases inconsistentes

**Severidad:** Media
**Estado:** ❌ Pendiente
**Ubicación:** Múltiples archivos en `src/main/java/`

**Descripción:**
Clases con nombres en minúscula (`security`, `login`, `cliente`, `productos_financieros`) en violación de la convención Java. Mezcla de español/inglés en métodos. Servicios viven en paquetes `controller/` (`TwoFactorService`, `ProductoFinancieroService`). El modelo `TwoFactorRestController` está en el paquete `model/`.

**Impacto:**
- Riesgo de bugs sutiles por shadowing (`security` puede colisionar con clases del JDK).
- Dificulta el mantenimiento y la auditoría.
- IDEs marcan warnings constantemente, ocultando alertas reales.

**Mitigación recomendada:**
Refactor de nombres usando `Refactor → Rename` de IntelliJ. Es invasivo pero cosmético; no rompe funcionalidad.

---

## Vulnerabilidades bajas y observaciones

### V-016 — `.gitignore` malformado deja `application.properties` en el repo

**Severidad:** Baja
**Estado:** ❌ Pendiente
**Ubicación:** `.gitignore` líneas 34-37

**Descripción:**
Las líneas que deberían ignorar `application.properties` están escritas con espacios entre caracteres (probable error de codificación al pegar):

```
s r c / m a i n / r e s o u r c e s / a p p l i c a t i o n . p r o p e r t i e s
```

Git no las interpreta como patrón válido, y el archivo se incluye en el repositorio. El `README.md` afirma que `application.properties` está en `.gitignore`, pero no lo está.

**Mitigación recomendada:**
- Corregir las líneas a: `src/main/resources/application.properties`
- Hacer `git rm --cached src/main/resources/application.properties` y commitear.
- Mantener un `application.properties.example` con valores placeholder.

---

### V-017 — Versión hardcodeada de JAR en systemd unit

**Severidad:** Baja
**Estado:** 🟡 Parcial
**Ubicación:** `scripts/deploy_ameribank.sh` (genera `/etc/systemd/system/ameribank.service`)

**Descripción:**
La unit de systemd referencia el path completo del JAR con versión: `Ameribank-0.0.1-SNAPSHOT.jar`. Cuando se actualice la versión en `pom.xml`, el systemd unit queda apuntando al JAR viejo (que ya no existe tras `mvn clean`).

**Estado de mitigación:**
El script de deploy resuelve la ruta dinámicamente con un glob (`Ameribank-*.jar`) y regenera la unit en cada deploy. Funciona si se ejecuta el script completo, pero alguien que ejecute `systemctl restart ameribank` después de un build manual rompe el flujo.

**Mitigación recomendada:**
Crear un symlink estable `target/ameribank.jar -> Ameribank-X.Y.Z.jar` y apuntar la unit ahí.

---

## Resumen de estado por vulnerabilidad

| ID | Vulnerabilidad | Severidad | Estado |
|----|----------------|-----------|--------|
| V-001 | Passwords en texto plano | Crítica | ❌ Pendiente |
| V-002 | TOTP/2FA en texto plano | Crítica | ❌ Pendiente |
| V-003 | `ConexionDB` con `System.exit` | Crítica | ✅ Mitigado |
| V-004 | Sin pool de conexiones | Crítica | ✅ Mitigado |
| V-005 | Tomcat sin timeouts (slowloris) | Alta | ✅ Mitigado |
| V-006 | Reglas Snort solo `alert` | Alta | ✅ Mitigado |
| V-007 | JDBC URL hardcodeado | Alta | ❌ Pendiente |
| V-008 | Llave privada sin passphrase | Alta | 🟡 Parcial |
| V-009 | Setup de credenciales requiere stdin | Alta | 🟡 Parcial |
| V-010 | `general_log` captura passwords | Alta | ❌ Pendiente |
| V-011 | Transferencias sin transacción | Media | ❌ Pendiente |
| V-012 | Event scheduler agresivo | Media | ❌ Pendiente |
| V-013 | Sin protección contra brute force | Media | ❌ Pendiente |
| V-014 | Nginx como SPOF | Media | ❌ Pendiente |
| V-015 | Convenciones de código rotas | Media | ❌ Pendiente |
| V-016 | `.gitignore` malformado | Baja | ❌ Pendiente |
| V-017 | JAR versionado en systemd | Baja | 🟡 Parcial |

---

## Recomendaciones priorizadas

### Antes de la demo (impacto: éxito de la presentación)

1. **Verificar que las reglas de Snort estén cargadas y en modo `drop`** — sin esto, el guion del paso 8 ("Snort filtra trafico malicioso") no se cumple.
2. **Validar el deploy del aplicativo con el script** — el modo no-interactivo es frágil; correr una pasada completa antes del día D.
3. **Cargar dashboards de Splunk** que correlacionen los eventos de Nginx, Snort y aplicativo en una sola vista temporal.

### Antes de cualquier exposición a usuarios reales (V-001 a V-010)

4. **Hashear contraseñas con bcrypt** (V-001) — cambio de schema + lógica de login.
5. **Cifrar secretos 2FA** (V-002) — cambio de schema + manejo de clave maestra.
6. **Eliminar `general_log` de MySQL** o agregarle filtro de queries sensibles (V-010).
7. **Soportar JDBC URL configurable** (V-007) — habilita rotación y movilidad.
8. **Encriptar la llave privada con passphrase** (V-008).
9. **Modo CLI no-interactivo nativo para setup de credenciales** (V-009).

### Mejoras de calidad (V-011 a V-015)

10. **Envolver `RegistrarTransferencia` en transacción explícita** (V-011) — riesgo financiero real.
11. **Implementar bloqueo por intentos fallidos en login** (V-013).
12. **Refactor de nombres de clases** (V-015) — cosmético pero importante para mantenimiento.
13. **Redundancia de Nginx** (V-014) — solo si el sistema va a producción real.

### Higiene (V-016 a V-017)

14. **Corregir `.gitignore`** (V-016) — 5 minutos de trabajo, evita filtrar configuración futura.
15. **Symlink estable para el JAR** (V-017).
