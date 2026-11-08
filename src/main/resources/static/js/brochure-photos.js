/*
 * Ficha de la prenda: ayuda a elegir las fotos.
 * Solo puede haber UNA foto principal: al marcar otra como "Principal", la que lo era pasa sola a "Apoyo"
 * (si todavía hay lugar para otra de apoyo) o a "No usar". Sin JavaScript el formulario funciona igual:
 * el servidor avisa si hay más de una principal.
 */
(function () {
    'use strict';

    function refresh(form) {
        form.querySelectorAll('.photo-tile').forEach(function (tile) {
            var checked = tile.querySelector('input[type=radio]:checked');
            tile.classList.toggle('is-main', !!checked && checked.value === 'main');
            tile.classList.toggle('is-extra', !!checked && checked.value === 'extra');
        });
    }

    document.addEventListener('change', function (event) {
        var radio = event.target;
        if (!(radio instanceof HTMLInputElement) || radio.type !== 'radio' || radio.name.indexOf('role_') !== 0) {
            return;
        }
        var form = radio.form;

        if (radio.value === 'main') {
            var extras = form.querySelectorAll('input[type=radio][name^="role_"][value="extra"]:checked').length;
            form.querySelectorAll('input[type=radio][name^="role_"][value="main"]:checked').forEach(function (other) {
                if (other !== radio) {
                    var target = extras < 2 ? 'extra' : 'none';
                    form.querySelector('input[name="' + other.name + '"][value="' + target + '"]').checked = true;
                    extras += target === 'extra' ? 1 : 0;
                }
            });
        }
        refresh(form);
    });
})();
