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
}());
