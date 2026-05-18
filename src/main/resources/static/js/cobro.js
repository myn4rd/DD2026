// Evento que se ejecuta cuando el DOM ha cargado completamente
document.addEventListener('DOMContentLoaded', function() {
    // Carga las tarjetas disponibles al iniciar la página
    cargarTarjetas();
    // Carga las alertas de límite al iniciar la página
    cargarAlertas();

    // Captura el evento de envío del formulario de cobro
    document.getElementById('form-cobro').addEventListener('submit', function(e) {
        // Previene el comportamiento por defecto del formulario (recargar página)
        e.preventDefault();
        // Ejecuta la función para simular el cobro
        simularCobro();
    });

    // Event listener para cerrar el modal del comprobante
    document.getElementById('cerrar-modal').addEventListener('click', function() {
        document.getElementById('modal-comprobante').style.display = 'none';
    });

    // Cerrar modal al hacer clic fuera del contenido
    document.getElementById('modal-comprobante').addEventListener('click', function(e) {
        if (e.target === this) {
            this.style.display = 'none';
        }
    });
});

// ========================================
// COLORES PARA LAS TARJETAS - Estilo Corporativo Bancario
// ========================================
const coloresTarjetas = [
    'linear-gradient(135deg, #072146 0%, #0a4d8c 100%)', // Azul marino profundo → Azul royal
    'linear-gradient(135deg, #1e3a5f 0%, #2d5a8f 100%)', // Azul medianoche → Azul corporativo
    'linear-gradient(135deg, #0f4c75 0%, #3282b8 100%)', // Azul oscuro → Azul medio
    'linear-gradient(135deg, #1b4965 0%, #5fa8d3 100%)', // Azul petróleo → Azul cielo
    'linear-gradient(135deg, #034078 0%, #1282a2 100%)', // Azul índigo → Azul brillante
    'linear-gradient(135deg, #023e7d 0%, #0077b6 100%)', // Azul profundo → Azul eléctrico
    'linear-gradient(135deg, #1d3557 0%, #457b9d 100%)', // Azul noche → Azul acero
    'linear-gradient(135deg, #003049 0%, #0582ca 100%)', // Azul oscuro → Azul vibrante
    'linear-gradient(135deg, #062f4f 0%, #2e86ab 100%)', // Azul marino → Azul caribe
    'linear-gradient(135deg, #042a3d 0%, #006494 100%)'  // Azul abismo → Azul intenso
];

// ========================================
// FUNCIÓN: Cargar tarjetas desde el servidor
// ========================================
function cargarTarjetas() {
    // Realiza una petición GET al endpoint de tarjetas
    fetch('/api/cobros/tarjetas')
        // Convierte la respuesta a formato JSON
        .then(r => r.json())
        // Procesa el array de tarjetas recibido
        .then(tarjetas => {
            // Muestra las tarjetas en la sección de tarjetas disponibles
            mostrarTarjetas(tarjetas);
            // Llena el select (dropdown) con las opciones de tarjetas
            llenarSelect(tarjetas);
        });
}

// ========================================
// FUNCIÓN: Mostrar tarjetas en la interfaz
// ========================================
function mostrarTarjetas(tarjetas) {
    // Obtiene el contenedor donde se mostrarán las tarjetas
    const contenedor = document.getElementById('tarjetas-lista');
    contenedor.innerHTML = '';

    // Si no hay tarjetas, muestra un mensaje
    if (tarjetas.length === 0) {
        contenedor.innerHTML = '<p style="color: #666; padding: 30px; text-align: center;">No hay tarjetas disponibles</p>';
        return;
    }

    // Itera sobre cada tarjeta del array
    tarjetas.forEach((t, index) => {
        // Calcula el saldo disponible (límite menos lo usado)
        let disponible = t.limiteCredito - t.saldoActual;
        // Calcula el porcentaje de uso con un decimal
        let porcentaje = (t.saldoActual / t.limiteCredito * 100).toFixed(1);

        // Asigna color de forma cíclica según el índice de la tarjeta
        const colorGradiente = coloresTarjetas[index % coloresTarjetas.length];

        // Determinar color de la barra de progreso según el uso
        let colorBarra = '#00cc99';
        if (porcentaje >= 80) colorBarra = '#ff4444';
        else if (porcentaje >= 60) colorBarra = '#ffaa00';

        // Crea el elemento div para la tarjeta
        const card = document.createElement('div');
        card.style.cssText = `
            background: ${colorGradiente};
            color: white;
            padding: 25px;
            border-radius: 15px;
            box-shadow: 0 8px 20px rgba(0,0,0,0.15);
            transition: transform 0.3s, box-shadow 0.3s;
            cursor: pointer;
            position: relative;
            overflow: hidden;
        `;

        // Efectos hover para la tarjeta
        card.onmouseover = () => {
            card.style.transform = 'translateY(-8px)';
            card.style.boxShadow = '0 12px 30px rgba(0,0,0,0.25)';
        };
        card.onmouseout = () => {
            card.style.transform = 'translateY(0)';
            card.style.boxShadow = '0 8px 20px rgba(0,0,0,0.15)';
        };

        // Contenido HTML de la tarjeta
        card.innerHTML = `
            <div style="position: absolute; top: 15px; right: 15px; background: rgba(255,255,255,0.3); padding: 8px 15px; border-radius: 20px; font-size: 13px; font-weight: 600;">
                ID: ${t.id}
            </div>

            <div style="margin-top: 15px; margin-bottom: 20px;">
                <div style="font-size: 12px; opacity: 0.9; margin-bottom: 8px; letter-spacing: 1px;">TARJETA DE CRÉDITO</div>
                <div style="font-size: 22px; letter-spacing: 3px; font-weight: 600;">**** **** **** ${t.numeroTarjeta.slice(-4)}</div>
            </div>

            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 15px; margin-top: 25px;">
                <div>
                    <div style="font-size: 12px; opacity: 0.85; margin-bottom: 5px;">LÍMITE</div>
                    <div style="font-size: 18px; font-weight: 700;">$${t.limiteCredito.toLocaleString('es-MX', {minimumFractionDigits: 2})}</div>
                </div>
                <div>
                    <div style="font-size: 12px; opacity: 0.85; margin-bottom: 5px;">DISPONIBLE</div>
                    <div style="font-size: 18px; font-weight: 700;">$${disponible.toLocaleString('es-MX', {minimumFractionDigits: 2})}</div>
                </div>
            </div>

            <div style="margin-top: 20px;">
                <div style="display: flex; justify-content: space-between; margin-bottom: 8px; font-size: 13px;">
                    <span>USO</span>
                    <span style="font-weight: 700;">${porcentaje}%</span>
                </div>
                <div style="background: rgba(255,255,255,0.3); border-radius: 10px; height: 10px; overflow: hidden;">
                    <div style="background: ${colorBarra}; height: 100%; width: ${porcentaje}%; transition: width 0.5s; border-radius: 10px;"></div>
                </div>
            </div>
        `;

        // Agrega la tarjeta al contenedor
        contenedor.appendChild(card);
    });
}

// ========================================
// FUNCIÓN: Llenar el select con opciones de tarjetas
// ========================================
function llenarSelect(tarjetas) {
    // Obtiene el elemento select del formulario
    let select = document.getElementById('tarjeta-select');
    // Establece la opción por defecto (vacía)
    select.innerHTML = '<option value="">-- Selecciona una tarjeta --</option>';

    // Itera sobre cada tarjeta para crear las opciones
    tarjetas.forEach(t => {
        // Calcula el saldo disponible de la tarjeta
        let disponible = t.limiteCredito - t.saldoActual;

        // Crea un nuevo elemento option
        let option = document.createElement('option');
        // Establece el valor del option (ID de la tarjeta)
        option.value = t.id;
        // Guarda el número completo en un atributo data
        option.setAttribute('data-numero', t.numeroTarjeta);
        // Establece el texto visible del option (últimos 4 dígitos y disponible)
        option.textContent = `**** ${t.numeroTarjeta.slice(-4)} - Disponible: $${disponible.toFixed(2)}`;
        // Agrega el option al select
        select.appendChild(option);
    });
}

// ========================================
// FUNCIÓN: Simular cobro en una tarjeta
// ========================================
function simularCobro() {
    // Obtiene el ID de la tarjeta seleccionada del select
    let tarjetaId = document.getElementById('tarjeta-select').value;
    // Obtiene el nombre del comercio y elimina espacios en blanco
    let comercio = document.getElementById('comercio').value.trim();
    // Obtiene el monto y lo convierte a número decimal
    let monto = parseFloat(document.getElementById('monto').value);

    // Limpia cualquier mensaje anterior del contenedor de resultados
    document.getElementById('resultado').innerHTML = '';

    // Valida que todos los campos estén completos
    if (!tarjetaId || !comercio || !monto) {
        // Muestra mensaje de error si falta algún campo
        mostrarMensaje('Complete todos los campos', false);
        return; // Detiene la ejecución
    }

    // Valida que el monto sea mayor a cero
    if (monto <= 0) {
        mostrarMensaje('El monto debe ser mayor a cero', false);
        return; // Detiene la ejecución
    }

    // Valida que el monto no exceda el límite máximo permitido
    if (monto > 999999) {
        mostrarMensaje('El monto excede el límite permitido', false);
        return; // Detiene la ejecución
    }

    // Obtiene el número de tarjeta del option seleccionado
    let selectElement = document.getElementById('tarjeta-select');
    let numeroTarjeta = selectElement.options[selectElement.selectedIndex].getAttribute('data-numero');

    // Obtiene el botón de submit del formulario
    let boton = document.querySelector('.btn-submit');
    // Guarda el texto original del botón
    let textoOriginal = boton.textContent;
    // Deshabilita el botón para evitar múltiples envíos
    boton.disabled = true;
    // Cambia el texto del botón a "Procesando..."
    boton.textContent = 'Procesando...';

    // Crea el objeto con los datos a enviar al servidor
    let datos = {
        tarjetaId: parseInt(tarjetaId), // Convierte a entero el ID de la tarjeta
        comercio: comercio,              // Nombre del comercio
        monto: monto                     // Monto del cobro
    };

    // Realiza la petición POST al endpoint de simulación
    fetch('/api/cobros/simular', {
        method: 'POST',                              // Método HTTP POST
        headers: {'Content-Type': 'application/json'}, // Indica que enviamos JSON
        body: JSON.stringify(datos)                   // Convierte el objeto a JSON
    })
    // Convierte la respuesta del servidor a JSON
    .then(r => r.json())
    // Procesa la respuesta del servidor
    .then(data => {
        // Habilita nuevamente el botón
        boton.disabled = false;
        // Restaura el texto original del botón
        boton.textContent = textoOriginal;

        // Verifica si el cobro fue exitoso
        if (data.exito) {
            // Muestra el comprobante en el modal
            mostrarComprobante({
                comercio: comercio,
                monto: monto,
                numeroTarjeta: numeroTarjeta,
                fecha: new Date()
            });

            // Limpia todos los campos del formulario
            document.getElementById('form-cobro').reset();

            // Espera 1 segundo y luego recarga los datos
            setTimeout(() => {
                cargarTarjetas();  // Recarga las tarjetas con saldos actualizados
                cargarAlertas();   // Recarga las alertas por si hay nuevas
            }, 1000);
        } else {
            // Muestra el mensaje de error enviado por el servidor
            mostrarMensaje(data.mensaje, false);
        }
    })
    // Maneja errores de conexión o del servidor
    .catch(() => {
        // Habilita nuevamente el botón
        boton.disabled = false;
        // Restaura el texto original del botón
        boton.textContent = textoOriginal;
        // Muestra mensaje de error de conexión
        mostrarMensaje('Error de conexión. Intente nuevamente.', false);
    });
}

// ========================================
// FUNCIÓN: Mostrar comprobante en modal
// ========================================
function mostrarComprobante(datos) {
    // Genera un número de referencia único
    const referencia = generarReferencia();

    // Formatea la fecha y hora
    const fecha = datos.fecha;
    const fechaFormateada = fecha.toLocaleDateString('es-MX', {
        year: 'numeric',
        month: 'long',
        day: 'numeric'
    });
    const horaFormateada = fecha.toLocaleTimeString('es-MX', {
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
    });

    // HTML del comprobante
    const comprobanteHTML = `
        <div style="padding: 40px; text-align: center;">
            <!-- Encabezado con logo y marca -->
            <div style="background: linear-gradient(135deg, #072146 0%, #0a4d8c 100%); padding: 30px; border-radius: 15px 15px 0 0; margin: -40px -40px 0 -40px;">
                <div style="color: white; font-size: 32px; font-weight: 700; letter-spacing: 2px;">AMERIBANK</div>
                <div style="color: rgba(255,255,255,0.9); font-size: 14px; margin-top: 8px;">Tu banco de confianza</div>
            </div>

            <!-- Ícono de éxito -->
            <div style="margin: 40px 0 30px 0;">
                <div style="width: 80px; height: 80px; background: #00cc66; border-radius: 50%; margin: 0 auto; display: flex; align-items: center; justify-content: center; box-shadow: 0 4px 15px rgba(0,204,102,0.3);">
                    <svg width="45" height="45" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
                        <polyline points="20 6 9 17 4 12"></polyline>
                    </svg>
                </div>
            </div>

            <!-- Título -->
            <h2 style="color: #003366; font-size: 28px; margin: 0 0 10px 0; font-weight: 700;">¡Pago Exitoso!</h2>
            <p style="color: #666; font-size: 16px; margin: 0 0 40px 0;">Tu transacción se ha procesado correctamente</p>

            <!-- Monto destacado -->
            <div style="background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%); padding: 25px; border-radius: 12px; margin-bottom: 30px;">
                <div style="color: #888; font-size: 14px; margin-bottom: 8px; text-transform: uppercase; letter-spacing: 1px;">Monto cargado</div>
                <div style="color: #003366; font-size: 48px; font-weight: 700;">$${datos.monto.toLocaleString('es-MX', {minimumFractionDigits: 2})}</div>
            </div>

            <!-- Detalles de la transacción -->
            <div style="text-align: left; background: #f8f9fa; padding: 25px; border-radius: 12px; margin-bottom: 30px;">
                <h3 style="color: #003366; font-size: 18px; margin: 0 0 20px 0; font-weight: 600;">Detalles de la Transacción</h3>

                <div style="margin-bottom: 18px; padding-bottom: 18px; border-bottom: 1px solid #ddd;">
                    <div style="color: #888; font-size: 13px; margin-bottom: 5px;">Comercio</div>
                    <div style="color: #333; font-size: 16px; font-weight: 600;">${datos.comercio}</div>
                </div>

                <div style="margin-bottom: 18px; padding-bottom: 18px; border-bottom: 1px solid #ddd;">
                    <div style="color: #888; font-size: 13px; margin-bottom: 5px;">Tarjeta</div>
                    <div style="color: #333; font-size: 16px; font-weight: 600;">**** **** **** ${datos.numeroTarjeta.slice(-4)}</div>
                </div>

                <div style="margin-bottom: 18px; padding-bottom: 18px; border-bottom: 1px solid #ddd;">
                    <div style="color: #888; font-size: 13px; margin-bottom: 5px;">Número de Referencia</div>
                    <div style="color: #003366; font-size: 18px; font-weight: 700; letter-spacing: 1px;">${referencia}</div>
                </div>

                <div style="margin-bottom: 18px; padding-bottom: 18px; border-bottom: 1px solid #ddd;">
                    <div style="color: #888; font-size: 13px; margin-bottom: 5px;">Fecha</div>
                    <div style="color: #333; font-size: 15px; font-weight: 500;">${fechaFormateada}</div>
                </div>

                <div style="margin-bottom: 0;">
                    <div style="color: #888; font-size: 13px; margin-bottom: 5px;">Hora</div>
                    <div style="color: #333; font-size: 15px; font-weight: 500;">${horaFormateada}</div>
                </div>
            </div>

            <!-- Nota informativa -->
            <div style="background: #e7f3ff; border-left: 4px solid #0077b6; padding: 15px; border-radius: 8px; text-align: left; margin-bottom: 25px;">
                <p style="margin: 0; color: #014f86; font-size: 14px; line-height: 1.6;">
                    <strong>Nota:</strong> Este comprobante es válido como prueba de tu transacción. Guarda el número de referencia para cualquier aclaración.
                </p>
            </div>

            <!-- Botón para cerrar -->
            <button onclick="document.getElementById('modal-comprobante').style.display='none'" style="background: linear-gradient(135deg, #072146 0%, #0a4d8c 100%); color: white; border: none; padding: 15px 40px; font-size: 16px; font-weight: 600; border-radius: 8px; cursor: pointer; transition: transform 0.2s, box-shadow 0.2s; width: 100%;">
                Cerrar Comprobante
            </button>
        </div>
    `;

    // Inserta el HTML en el modal
    document.getElementById('comprobante-contenido').innerHTML = comprobanteHTML;

    // Muestra el modal con flexbox
    const modal = document.getElementById('modal-comprobante');
    modal.style.display = 'flex';
}

// ========================================
// FUNCIÓN: Generar número de referencia único
// ========================================
function generarReferencia() {
    const timestamp = Date.now();
    const random = Math.floor(Math.random() * 10000);
    return `AMB${timestamp}${random}`.slice(0, 16);
}

// ========================================
// FUNCIÓN: Mostrar mensajes de éxito o error
// ========================================
function mostrarMensaje(mensaje, exito) {
    // Define el color según si es éxito (verde) o error (rojo)
    let color = exito ? 'green' : 'red';
    // Inserta el mensaje en el contenedor de resultados con estilos
    document.getElementById('resultado').innerHTML =
        `<p style="color:${color}; font-weight: bold; padding: 15px; background: ${exito ? '#d4edda' : '#f8d7da'}; border-radius: 8px; margin-top: 20px;">${mensaje}</p>`;
}

// ========================================
// FUNCIÓN: Cargar alertas de límite desde el servidor
// ========================================
function cargarAlertas() {
    // Realiza una petición GET al endpoint de alertas
    fetch('/api/cobros/alertas')
        // Convierte la respuesta a formato JSON
        .then(r => r.json())
        // Procesa el array de alertas recibido
        .then(alertas => mostrarAlertas(alertas));
}

// ========================================
// FUNCIÓN: Mostrar alertas de tarjetas con límite alto
// ========================================
function mostrarAlertas(alertas) {
    // Variable para acumular el HTML de las alertas
    let html = '';

    // Verifica si no hay alertas
    if (alertas.length === 0) {
        // Muestra mensaje indicando que no hay alertas
        html = '<p style="color: #666; text-align: center; padding: 20px;">No hay tarjetas con alerta de límite</p>';
    } else {
        // Itera sobre cada alerta para construir el HTML
        alertas.forEach(a => {
            // Calcula el porcentaje de uso con un decimal
            let porcentaje = (a.saldoActual / a.limiteCredito * 100).toFixed(1);
            // Construye el HTML para mostrar la alerta
            html += `
                <div style="background: linear-gradient(135deg, #fff5f5 0%, #ffe5e5 100%); border-left: 5px solid #ff4444; padding: 20px; margin-bottom: 15px; border-radius: 8px;">
                    <h3 style="color: #cc0000; margin: 0 0 15px 0; font-size: 18px;">⚠️ ALERTA: **** **** **** ${a.numeroTarjeta.slice(-4)}</h3>
                    <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 15px;">
                        <div>
                            <div style="color: #888; font-size: 13px; margin-bottom: 5px;">Límite de Crédito</div>
                            <div style="color: #333; font-size: 16px; font-weight: 600;">$${a.limiteCredito.toFixed(2)}</div>
                        </div>
                        <div>
                            <div style="color: #888; font-size: 13px; margin-bottom: 5px;">Monto Usado</div>
                            <div style="color: #cc0000; font-size: 16px; font-weight: 600;">$${a.saldoActual.toFixed(2)}</div>
                        </div>
                        <div>
                            <div style="color: #888; font-size: 13px; margin-bottom: 5px;">Porcentaje de Uso</div>
                            <div style="color: #cc0000; font-size: 16px; font-weight: 700;">${porcentaje}%</div>
                        </div>
                    </div>
                </div>
            `;
        });
    }

    // Inserta todo el HTML generado en el contenedor de alertas
    document.getElementById('alertas-lista').innerHTML = html;
}