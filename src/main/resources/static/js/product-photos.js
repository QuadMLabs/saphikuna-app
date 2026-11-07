/*
 * Fotos del producto: vista previa, acumulación, ★ principal y ✕ quitar.
 *
 * Sin JS, un <input type="file"> reemplaza la selección anterior y no muestra
 * miniaturas. Este script (lo único de JS del formulario) guarda las fotos que
 * se van eligiendo, las muestra y las mantiene en el input para el envío normal.
 *
 * Foto principal: todos los radios se llaman "primary" con valor
 * "existing:<id>" (guardadas) o "new:<n>" (nuevas, n = posición en la selección).
 */
(function () {
    'use strict';

    var input = document.getElementById('newImages');
    var box = document.getElementById('photoPreviews');
    var addTile = document.getElementById('photoAdd');
    var message = document.getElementById('photoMessage');

    if (!input || !box || typeof DataTransfer === 'undefined') {
        return; // el input nativo sigue funcionando
    }

    var NS = 'http://www.w3.org/2000/svg';
    var max = parseInt(input.dataset.max, 10) || 4;
    var store = new DataTransfer();
    var primaryFile = null; // File elegido como principal entre las nuevas

    function svg(children) {
        var el = document.createElementNS(NS, 'svg');
        el.setAttribute('viewBox', '0 0 24 24');
        el.setAttribute('aria-hidden', 'true');
        children.forEach(function (c) {
            var node = document.createElementNS(NS, c.tag);
            Object.keys(c.attrs).forEach(function (k) { node.setAttribute(k, c.attrs[k]); });
            el.appendChild(node);
        });
        return el;
    }

    function starIcon() {
        return svg([{ tag: 'polygon', attrs: {
            points: '12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2'
        } }]);
    }

    function xIcon() {
        return svg([
            { tag: 'path', attrs: { d: 'M18 6 6 18' } },
            { tag: 'path', attrs: { d: 'm6 6 12 12' } }
        ]);
    }

    function existingCards() {
        return Array.prototype.slice.call(document.querySelectorAll('.photo-card[data-existing]'));
    }

    function isRemoved(card) {
        var box = card.querySelector('.photo-x input');
        return !!(box && box.checked);
    }

    function keptExisting() {
        return existingCards().filter(function (c) { return !isRemoved(c); }).length;
    }

    function sameFile(a, b) {
        return a.name === b.name && a.size === b.size && a.lastModified === b.lastModified;
    }

    function removeAt(index) {
        var removed = store.files[index];
        var next = new DataTransfer();
        Array.prototype.forEach.call(store.files, function (file, i) {
            if (i !== index) {
                next.items.add(file);
            }
        });
        store = next;
        if (removed === primaryFile) {
            primaryFile = null;
        }
        render();
    }

    /** Garantiza que exactamente una foto visible quede con la estrella. */
    function normalizePrimary() {
        var radios = Array.prototype.slice.call(document.querySelectorAll('.photo-star input[name=primary]'))
            .filter(function (r) {
                var card = r.closest('.photo-card');
                return card && !(card.hasAttribute('data-existing') && isRemoved(card));
            });

        var checked = radios.filter(function (r) { return r.checked; });

        if (checked.length === 0 && radios.length > 0) {
            radios[0].checked = true;
        }
        // Las quitadas no deben quedar como principal
        document.querySelectorAll('.photo-card[data-existing]').forEach(function (card) {
            if (isRemoved(card)) {
                var r = card.querySelector('.photo-star input');
                if (r) { r.checked = false; }
            }
        });
    }

    function render() {
        box.textContent = '';

        Array.prototype.forEach.call(store.files, function (file, i) {
            var card = document.createElement('div');
            card.className = 'photo-card is-new';

            var img = document.createElement('img');
            img.alt = '';
            img.src = URL.createObjectURL(file);
            img.onload = function () { URL.revokeObjectURL(img.src); };
            card.appendChild(img);

            // ★ principal
            var star = document.createElement('label');
            star.className = 'photo-btn photo-star';
            star.title = box.dataset.primaryLabel || '';
            var radio = document.createElement('input');
            radio.type = 'radio';
            radio.name = 'primary';
            radio.value = 'new:' + i;
            radio.className = 'visually-hidden';
            radio.checked = (file === primaryFile);
            radio.setAttribute('aria-label', box.dataset.primaryLabel || '');
            radio.addEventListener('change', function () { primaryFile = file; });
            star.appendChild(radio);
            star.appendChild(starIcon());
            card.appendChild(star);

            // ✕ quitar (en las nuevas es inmediato)
            var remove = document.createElement('button');
            remove.type = 'button';
            remove.className = 'photo-btn photo-x';
            remove.title = box.dataset.removeLabel || '';
            remove.setAttribute('aria-label', box.dataset.removeLabel || '');
            remove.appendChild(xIcon());
            remove.addEventListener('click', function () { removeAt(i); });
            card.appendChild(remove);

            box.appendChild(card);
        });

        input.files = store.files;
        normalizePrimary();

        // Si la principal quedó en una nueva por defecto, recordarla
        var picked = box.querySelector('input[name=primary]:checked');
        if (picked) {
            primaryFile = store.files[parseInt(picked.value.split(':')[1], 10)] || null;
        }

        if (addTile) {
            addTile.hidden = keptExisting() + store.files.length >= max;
        }
    }

    input.addEventListener('change', function () {
        var room = max - keptExisting() - store.files.length;
        var tooMany = false;

        Array.prototype.forEach.call(input.files, function (file) {
            if (!file.type || file.type.indexOf('image/') !== 0) {
                return;
            }
            var duplicated = Array.prototype.some.call(store.files, function (f) {
                return sameFile(f, file);
            });
            if (duplicated) {
                return;
            }
            if (room <= 0) {
                tooMany = true;
                return;
            }
            store.items.add(file);
            room--;
        });

        if (message) {
            message.textContent = tooMany ? (input.dataset.maxMessage || '') : '';
        }
        render();
    });

    // Quitar/restaurar una foto guardada libera o consume un cupo y puede mover la estrella
    document.addEventListener('change', function (event) {
        var card = event.target.closest && event.target.closest('.photo-card[data-existing]');
        if (card && event.target.closest('.photo-x')) {
            render();
        }
    });

    render();
}());
