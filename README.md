# Ameribank - Sistema Bancario
Página home: https://github.com/DaFrik19/ameribank-index

> Proyecto para Amerike Cybersecurity DemoDay 2025

## Tabla de Contenidos
- [Requisitos](#-requisitos)
- [Configuración Inicial](#-configuración-inicial)
- [Estructura del Proyecto](#-estructura-del-proyecto)
- [Workflow de Git](#-workflow-de-git-léanlo-o-los-mato)
- [Convenciones](#-convenciones)
- [Comandos Útiles](#-comandos-útiles)

---

## Requisitos

Antes de empezar, asegúrate de tener instalado:

- **IntelliJ IDEA** (Ultimate o Community) - [Descargar](https://www.jetbrains.com/idea/download/)
  - Si eres estudiante, consigue la licencia Ultimate gratis con tu correo institucional
- **Java 17+** ([Descargar](https://adoptium.net/)) - IntelliJ puede descargarlo automáticamente
- **Maven** (Ya viene integrado en IntelliJ)
- **MySQL 8.0+** ([Descargar](https://dev.mysql.com/downloads/))
- **Git** (IntelliJ lo trae integrado, pero puedes instalar [Git standalone](https://git-scm.com/))

---

##  Configuración Inicial

### 1. Fork y Clone

**Opción A: Desde IntelliJ (Recomendado)**

```
1. Abre IntelliJ IDEA
2. File → New → Project from Version Control
3. URL: https://github.com/TU_USUARIO/DDNOV2025.git
4. Directory: Elige dónde guardar el proyecto
5. Clone
```

IntelliJ detectará automáticamente que es un proyecto Maven y descargará dependencias.

**Opción B: Desde Terminal**

```bash
# 1. Haz FORK del repo original en GitHub (botón arriba a la derecha)

# 2. Clona TU fork (no el original)
git clone git@github.com:TU_USUARIO/DDNOV2025.git
cd DDNOV2025

# 3. Abre en IntelliJ: File → Open → Selecciona la carpeta
```

**Configurar upstream (remoto original):**

En IntelliJ:
```
1. Git → Manage Remotes (Ctrl+Shift+`)
2. Click en "+"
3. Name: upstream
4. URL: https://github.com/DaFrik19/DDNOV2025.git
5. OK
```

O desde terminal:
```bash
git remote add upstream git@github.com:DaFrik19/DDNOV2025.git
git remote -v  # Verificar
```

### 2. Configurar Base de Datos

```sql
-- Ejecuta esto en MySQL Workbench o terminal
CREATE DATABASE ameribank;
```

### 3. Configurar application.properties

**En IntelliJ:**

```
1. Navega a: src/main/resources/
2. Clic derecho en application.properties.example
3. Copy → Paste → Renombra a: application.properties
4. Doble clic para abrir
5. Edita respecto a tu entorno
```

Configura:
```properties
spring.jpa.hibernate.ddl-auto=update
server.port=8080
```

**IMPORTANTE:** 
- El archivo `application.properties` ya está en `.gitignore`
- IntelliJ te lo mostrará en gris (significa que Git lo ignorará)
- Verifica en el panel de Git (Alt+9) que NO aparezca para commit

**Desde terminal:**
```bash
cp src/main/resources/application.properties.example src/main/resources/application.properties
nano src/main/resources/application.properties
```
### 4. Configura Credenciales de Base de datos
Tus credenciales iran en `/src/main/java/org/amerike/ameribank/config/ConexionDB.java`
```java
public class ConexionDB {
    private static final String URL = "jdbc:mysql://localhost/Ameribank";
    private static final String USER = "usuario_java";
    private static final String PASS = "TuContraseniaSegura123";

    public static Connection conectar() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }
}
```
> Esto cambiara una vez implementada la funcionalidad de llaves `.pem`

### 4. Ejecutar el Proyecto

**Desde IntelliJ (Recomendado):**

1. **Espera a que Maven descargue dependencias**
   - Verás una barra de progreso abajo
   - Si no inicia automáticamente: Maven tab (derecha) → Reload
   
2. **Configura el JDK (si es necesario)**
   ```
   File → Project Structure (Ctrl+Alt+Shift+S)
   Project SDK: 17 o superior
   ```
   Si no tienes Java 17:
   ```
   Add SDK → Download JDK → Version 17 → Vendor: Eclipse Temurin
   ```

3. **Ejecutar la aplicación**
   - Opción 1: Abre `AmeribankApplication.java` → Click en ▶️ verde
   - Opción 2: Clic derecho en `AmeribankApplication.java` → Run
   - Opción 3: Shift+F10 (con el archivo abierto)

4. **Accede a la aplicación**
   ```
   http://localhost:8080
   ```

**Desde terminal:**
```bash
mvn spring-boot:run
```

**Detener la aplicación:**
- En IntelliJ: Click en ⏹️ (Stop) o Ctrl+F2
- En terminal: Ctrl+C

---

## Estructura del Proyecto

```
src/main/java/org/amerike/ameribank/
├── config/          # Configuraciones (DB, Security, etc.)
├── controller/      # REST Controllers (Endpoints)
├── dao/             # Data Access Objects (Acceso a BD)
├── model/           # Entidades/POJOs
├── service/         # Lógica de negocio
└── security/        # Autenticación y autorización

src/main/resources/
├── static/          # HTML, CSS, JS
├── templates/       # Templates (si usamos Thymeleaf)
└── application.properties  # Configuración (NO SUBIR)
```

### IMPORTANTE: Convención de Nombres

**EL PAQUETE BASE ES:** `org.amerike.ameribank` (sin 'k' en bank)

**MAL:** `org.amerike.amerikebank`  
**BIEN:** `org.amerike.ameribank`

---

## Workflow de Git (LÉANLO)

### Regla de Oro: NUNCA trabajen directo en `main`

### Flujo Completo:

#### 1. Antes de Empezar CUALQUIER Cosa

**En IntelliJ:**
```
1. Abre el panel de Git (Alt+9 o View → Tool Windows → Git)
2. Asegúrate de estar en rama 'main':
   - Abajo a la derecha verás la rama actual
   - Si no es 'main', click → Checkout
3. Git → Fetch (Ctrl+T)
4. Git → Pull (o Update Project)
   - Selecciona 'upstream' y rama 'main'
5. Git → Push para actualizar tu fork
```

**Desde Terminal:**
```bash
git checkout main
git fetch upstream
git merge upstream/main
git push origin main
```

#### 2. Crear una Rama para tu Feature

**En IntelliJ:**
```
1. Click en el nombre de la rama (abajo a la derecha)
2. New Branch
3. Nombre: feature/nombre-de-tu-modulo
4. Deja marcado "Checkout branch"
5. Create
```

**Desde Terminal:**
```bash
git checkout -b feature/nombre-de-tu-modulo

# Ejemplos:
# git checkout -b feature/modulo-prestamos
# git checkout -b fix/corregir-login
# git checkout -b refactor/optimizar-queries
```

#### 3. Trabaja en tu Rama

**En IntelliJ:**
```
1. Haz tus cambios en el código
2. Abre el panel Commit (Ctrl+K o Alt+0)
3. Selecciona los archivos a incluir
4. Escribe mensaje descriptivo (ver convenciones)
5. Commit
6. Luego Push (Ctrl+Shift+K)
   - Selecciona 'origin' y tu rama
   - Push
```

**Shortcuts útiles de IntelliJ:**
- `Ctrl+K` - Ventana de Commit
- `Ctrl+Shift+K` - Push
- `Ctrl+T` - Update Project (Pull)
- `Alt+9` - Panel de Git
- `Alt+`  - Terminal

**Desde Terminal:**
```bash
# Haz tus cambios...
git add .
git commit -m "feat: Add prestamos module with CRUD operations"
git push origin feature/nombre-de-tu-modulo
```

#### 4. Crear Pull Request

1. IntelliJ te mostrará una notificación con link al PR
2. O ve a **GitHub** → Tu fork manualmente
3. Verás un banner: **"Compare & pull request"**
4. Asegúrate que sea:
   ```
   base: DaFrik19/DDNOV2025:main
   ←
   compare: TU_USUARIO/DDNOV2025:feature/tu-rama
   ```
5. Escribe un buen título y descripción
6. **Espera aprobación** antes de hacer merge

#### 5. Después de que Aprueben tu PR

**En IntelliJ:**
```
1. Cambiar a main: Click en rama → Checkout 'main'
2. Git → Pull → upstream/main
3. Git → Push → origin/main
4. Borrar rama local: Click en rama → Delete
```

**Desde Terminal:**
```bash
git checkout main
git fetch upstream
git merge upstream/main
git push origin main
git branch -D feature/nombre-de-tu-modulo
git push origin --delete feature/nombre-de-tu-modulo
```

---

## Convenciones

### Mensajes de Commit

Usa [Conventional Commits](https://www.conventionalcommits.org/):

```bash
# Nuevas funcionalidades
git commit -m "feat: Add user authentication module"

# Correcciones de bugs
git commit -m "fix: Resolve null pointer in ClienteDAO"

# Refactorización
git commit -m "refactor: Improve database connection logic"

# Documentación
git commit -m "docs: Update API documentation"

# Estilos/formato (sin cambios funcionales)
git commit -m "style: Format code with prettier"

# Tests
git commit -m "test: Add unit tests for prestamos service"

# Tareas de mantenimiento
git commit -m "chore: Update dependencies"
```

### Nombres de Ramas

```bash
feature/nombre-descriptivo    # Nueva funcionalidad
fix/nombre-del-bug           # Corrección
refactor/que-refactorizas    # Refactorización
docs/que-documentas          # Documentación
```

### Nombres de Clases Java

```java
// PascalCase para clases
public class ClienteController { }
public class MovimientoService { }

// camelCase para métodos y variables
public void obtenerCliente() { }
private String nombreCompleto;

// UPPER_CASE para constantes
public static final String API_VERSION = "v1";
```

---

## Qué NO Hacer

**NO hagas push directo a `main`**  
**NO hagas commits con mensaje "update", "changes", "fix"**  
**NO subas `application.properties` con tus credenciales**  
**NO uses paquetes diferentes a `org.amerike.ameribank`**  
**NO hagas merge de PRs sin que alguien los revise**  
**NO trabajes sin actualizar desde `upstream` primero**  
**NO ignores los conflictos de merge, pide ayuda**

---

## Comandos Útiles

### Desde IntelliJ

| Acción | Shortcut | Ubicación |
|--------|----------|-----------|
| Commit | `Ctrl+K` | Git → Commit |
| Push | `Ctrl+Shift+K` | Git → Push |
| Pull/Update | `Ctrl+T` | Git → Pull |
| Panel Git | `Alt+9` | View → Tool Windows → Git |
| Terminal | `Alt+F12` | View → Tool Windows → Terminal |
| Buscar archivo | `Ctrl+Shift+N` | Navigate → File |
| Buscar en archivos | `Ctrl+Shift+F` | Edit → Find → Find in Files |
| Refactor | `Ctrl+Alt+Shift+T` | Refactor → Refactor This |
| Formatear código | `Ctrl+Alt+L` | Code → Reformat Code |
| Organizar imports | `Ctrl+Alt+O` | Code → Optimize Imports |
| Run | `Shift+F10` | Run → Run |
| Debug | `Shift+F9` | Run → Debug |

### Desde Terminal (integrada en IntelliJ con `Alt+F12`)

```bash
# Ver estado de tu repo
git status

# Ver historial de commits
git log --oneline --graph -10

# Ver diferencias antes de commit
git diff

# Ver tus remotos configurados
git remote -v

# Descartar cambios locales (CUIDADO)
git checkout -- archivo.java

# Volver al último commit (CUIDADO)
git reset --hard HEAD

# Ver ramas locales
git branch

# Ver ramas remotas
git branch -r

# Cambiar de rama
git checkout nombre-rama

# Eliminar rama local
git branch -D nombre-rama
```

### Tips de IntelliJ

**Ver cambios antes de commit:**
```
1. Panel Git (Alt+9)
2. Tab "Local Changes"
3. Doble click en archivo para ver diff
```

**Resolver conflictos de merge:**
```
1. IntelliJ detectará conflictos automáticamente
2. Click derecho en archivo → Git → Resolve Conflicts
3. Usa el merge tool visual (3 paneles)
4. Accept Left (tu versión) o Accept Right (versión remota)
```

**Historial de un archivo:**
```
1. Click derecho en archivo
2. Git → Show History
3. Verás todos los commits que modificaron ese archivo
```

**Comparar ramas:**
```
1. Panel Git (Alt+9)
2. Tab "Branches"
3. Click derecho en rama → Compare with Current
```

---

## Problemas Comunes

### "¡Subí cosas a main por error!"

**En IntelliJ:**
```
1. NO hagas nada más
2. Contacta al PM o asesor técnico INMEDIATAMENTE
3. Muestra tu ventana de Git (Alt+9) → Log
```

### "Tengo conflictos al hacer merge"

**En IntelliJ (Recomendado):**
```
1. Git detectará conflictos automáticamente
2. Click en "Resolve Conflicts" en la notificación
3. Se abre ventana con 3 paneles:
   - Izquierda: Tu versión
   - Centro: Resultado final
   - Derecha: Versión remota
4. Usa los botones >> y << para aceptar cambios
5. O edita manualmente el panel central
6. Click en "Apply"
7. Commit y Push
```

**Desde Terminal:**
```bash
# 1. Abre los archivos con conflictos
# 2. Busca las marcas <<<<<, =====, >>>>>
# 3. Decide qué código mantener
# 4. Elimina las marcas
# 5. git add . && git commit
```

### "No puedo hacer push"

```
Error: Updates were rejected because the remote contains work...
```

**Solución:**
```bash
# Probablemente alguien subió cambios antes que tú
git pull origin tu-rama --rebase
git push origin tu-rama
```

### "Maven no descarga dependencias"

**En IntelliJ:**
```
1. Maven tab (derecha) → Click en 🔄 (Reload)
2. O: File → Invalidate Caches → Invalidate and Restart
3. Si persiste: Elimina carpeta .m2 → Reload Maven
```

### "IntelliJ no encuentra clases/imports"

```
1. File → Invalidate Caches → Invalidate and Restart
2. Maven → Reimport
3. Verifica que src/main/java esté marcado como "Sources Root"
   (debe estar en azul en el árbol de proyecto)
```

### "El puerto 8080 ya está en uso"

```
Error: Web server failed to start. Port 8080 was already in use.
```

**Solución:**
```
1. Para la aplicación que esté usando el puerto
2. O cambia el puerto en application.properties:
   server.port=8081
```

### "Rompí todo"

```bash
# Respira profundo
# Pide ayuda al asesor técnico
# NO hagas 'git push -f' a menos que sepas qué haces
# Puedes deshacer commits locales con: git reset --hard HEAD~1
```

### "IntelliJ no reconoce el proyecto Maven"

```
1. Click derecho en pom.xml
2. Add as Maven Project
3. Espera a que indexe
```
---

## Recursos Útiles

- [Git Cheat Sheet](https://education.github.com/git-cheat-sheet-education.pdf)
- [Spring Boot Docs](https://spring.io/projects/spring-boot)
- [Conventional Commits](https://www.conventionalcommits.org/)
- [Maven Guide](https://maven.apache.org/guides/getting-started/)

---

## Quick Start (TL;DR)

```bash
# Setup inicial (una sola vez)
git clone git@github.com:TU_USUARIO/DDNOV2025.git
cd DDNOV2025
git remote add upstream git@github.com:DaFrik19/DDNOV2025.git

# Antes de trabajar (SIEMPRE)
git checkout main
git fetch upstream
git merge upstream/main

# Crear feature
git checkout -b feature/mi-modulo

# Trabajar
# ... código código código ...
git add .
git commit -m "feat: Add mi-modulo with X functionality"
git push origin feature/mi-modulo

# Crear PR en GitHub y esperar aprobación
```
---

> **Tip:** Si tienes dudas, pregunta ANTES de hacer push. Es más fácil prevenir que arreglar.

> **Recuerda:** Este es un proyecto colaborativo. Comunícate con el equipo, sigue el workflow y todo saldrá bien.
