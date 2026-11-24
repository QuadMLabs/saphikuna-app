/*
 * Indicador de "Guardando..." para formularios con el atributo data-saving.
 *
 * Al enviar: el botón marcado con data-saving-button muestra un spinner y el
 * texto de data-saving-text, y se bloquea un segundo envío (evita productos
 * duplicados si se toca dos veces mientras se suben las fotos).
 */
(function () {
    'use strict';

    function start(form) {
        form.dataset.submitting = '1';
        form.classList.add('is-saving');

        var button = form.querySelector('[data-saving-button]');
        if (button) {
            button.dataset.originalText = button.textContent;
            button.textContent = button.dataset.savingText || '...';
            button.classList.add('is-loading');
            button.setAttribute('aria-busy', 'true');
        }
    }

    function reset(form) {
        delete form.dataset.submitting;
        form.classList.remove('is-saving');

        var button = form.querySelector('[data-saving-button]');
        if (button && button.dataset.originalText !== undefined) {
            button.textContent = button.dataset.originalText;
            button.classList.remove('is-loading');
            button.removeAttribute('aria-busy');
        }
    }

    document.addEventListener('submit', function (event) {
        var form = event.target;
        if (!form.matches || !form.matches('form[data-saving]')) {
            return;
        }
        if (form.dataset.submitting === '1') {
            event.preventDefault();
            return;
        }
        start(form);
    });

    // Si el usuario vuelve atrás (caché del navegador), se restaura el botón
    window.addEventListener('pageshow', function (event) {
        if (event.persisted) {
            document.querySelectorAll('form[data-saving]').forEach(reset);
        }
    });

    // Latido: mientras la página está abierta se avisa al servidor cada pocos minutos.
    // El plan gratis lo duerme tras 15 minutos sin visitas, y un formulario que tardaba en llenarse
    // se perdía al guardar. Si el servidor se reinició, la respuesta trae el token de seguridad
    // nuevo y se pone en los formularios abiertos para que guardar siga funcionando.
    var BEAT_EVERY = 4 * 60 * 1000;
    var lastBeat = Date.now();

    function beat() {
        lastBeat = Date.now();
        fetch('/admin/ping', {
            credentials: 'same-origin',
            cache: 'no-store',
            headers: { Accept: 'application/json' }
        }).then(function (response) {
            var type = response.headers.get('content-type') || '';
            return response.ok && type.indexOf('json') !== -1 ? response.json() : null;
        }).then(function (data) {
            if (!data || !data.token || !data.parameter) {
                return;
            }
            document.querySelectorAll('input[type="hidden"][name="' + data.parameter + '"]').forEach(function (input) {
                input.value = data.token;
            });
        }).catch(function () { /* sin conexión: se intenta en el siguiente latido */ });
    }

    setInterval(function () {
        if (!document.hidden) {
            beat();
        }
    }, BEAT_EVERY);

    // Al volver a la pestaña (por ejemplo en el celular) se avisa de una vez si pasó un rato
    document.addEventListener('visibilitychange', function () {
        if (!document.hidden && Date.now() - lastBeat > 2 * 60 * 1000) {
            beat();
        }
    });
}());
