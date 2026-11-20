/*
 * Campo de video de los formularios: revisa el archivo apenas se elige, para avisar antes de subirlo
 * (el servidor vuelve a revisarlo). Sin esto el formulario también funciona.
 */
(function () {
    'use strict';

    var input = document.getElementById('newVideo');
    var message = document.getElementById('videoMessage');
    if (!input) {
        return;
    }
    var maxBytes = (parseInt(input.dataset.maxMb, 10) || 15) * 1024 * 1024;

    input.addEventListener('change', function () {
        var file = input.files && input.files[0];
        message.textContent = '';
        if (!file) {
            return;
        }
        var isMp4 = file.type === 'video/mp4' || /\.mp4$/i.test(file.name);
        if (!isMp4) {
            message.textContent = input.dataset.notMp4 || '';
            input.value = '';
        } else if (file.size > maxBytes) {
            message.textContent = input.dataset.tooBig || '';
            input.value = '';
        }
    });
})();
