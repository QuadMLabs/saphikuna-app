/*
 * =========================================================
 * GLOBAL TOAST
 * =========================================================
 *
 * Reusable toast notification component.
 *
 * Usage:
 *
 * Toast.show('Message', 'success');
 * Toast.show('Message', 'error');
 * Toast.show('Message', 'warning');
 * Toast.show('Message', 'info');
 *
 * Or using shortcuts:
 *
 * Toast.success('Message');
 * Toast.error('Message');
 * Toast.warning('Message');
 * Toast.info('Message');
 *
 * Optional configuration:
 *
 * Toast.show('Message', 'success', {
 *     duration: 4000
 * });
 *
 * =========================================================
 */

(function (window) {
    'use strict';

    /* =========================================================
     * CONFIGURATION
     * ========================================================= */

    const DEFAULT_DURATION = 4000;

    const TOAST_TYPES = {
        success: {icon: 'check-circle'},
        error: {icon: 'circle-alert'},
        warning: {icon: 'triangle-alert'},
        info: {icon: 'info'}
    };

    /* =========================================================
     * CONTAINER
     * ========================================================= */

    let toastContainer = null;

    /*
     * Create toast container only when necessary.
     */
    function getContainer() {

        if (toastContainer) {
            return toastContainer;
        }

        toastContainer = document.createElement('div');
        toastContainer.className = 'app-toast-container';
        toastContainer.setAttribute('aria-live', 'polite');
        toastContainer.setAttribute('aria-atomic', 'true');
        document.body.appendChild(toastContainer);

        return toastContainer;
    }

    /* =========================================================
     * SHOW TOAST
     * ========================================================= */

    function show(
        message,
        type = 'info',
        options = {}
    ) {
        /*
         * Make sure the requested type exists.
         */
        if (!TOAST_TYPES[type]) {
            type = 'info';
        }

        /*
         * Normalize duration.
         */
        const duration = typeof options.duration === 'number' ? options.duration : DEFAULT_DURATION;
        const container = getContainer();

        /* -----------------------------------------------------
         * TOAST
         * ----------------------------------------------------- */

        const toast = document.createElement('div');
        toast.className = `app-toast app-toast-${type}`;
        toast.setAttribute('role', type === 'error' ? 'alert' : 'status');

        /* -----------------------------------------------------
         * ICON
         * ----------------------------------------------------- */

        const iconContainer = document.createElement('div');
        iconContainer.className = 'app-toast-icon';
        iconContainer.setAttribute('aria-hidden', 'true');

        const icon = document.createElement('i');
        icon.setAttribute('data-lucide', TOAST_TYPES[type].icon);

        iconContainer.appendChild(icon);

        /* -----------------------------------------------------
         * MESSAGE
         * ----------------------------------------------------- */

        const messageElement = document.createElement('div');
        messageElement.className = 'app-toast-message';
        messageElement.textContent = message;

        /* -----------------------------------------------------
         * CLOSE BUTTON
         * ----------------------------------------------------- */

        const closeButton = document.createElement('button');
        closeButton.type = 'button';
        closeButton.className = 'app-toast-close';
        closeButton.setAttribute('aria-label', 'Cerrar');
        closeButton.setAttribute('title', 'Cerrar');

        const closeIcon = document.createElement('i');
        closeIcon.setAttribute('data-lucide', 'x');
        closeButton.appendChild(closeIcon);

        /* -----------------------------------------------------
         * PROGRESS BAR
         * ----------------------------------------------------- */

        const progress = document.createElement('div');
        progress.className = 'app-toast-progress';
        progress.style.animationDuration = `${duration}ms`;

        /* -----------------------------------------------------
         * ASSEMBLE TOAST
         * ----------------------------------------------------- */

        toast.appendChild(iconContainer);
        toast.appendChild(messageElement);
        toast.appendChild(closeButton);
        toast.appendChild(progress);

        container.appendChild(toast);

        /*
         * Activate Lucide icons.
         */
        if (typeof lucide !== 'undefined' && typeof lucide.createIcons === 'function') {
            lucide.createIcons({
                attrs: {
                    'stroke-width': 2
                }
            });
        }

        /* =====================================================
         * CLOSE LOGIC
         * ===================================================== */

        let timeoutId = null;
        let removeTimeoutId = null;

        function removeToast() {

            if (toast.classList.contains('is-removing')) {
                return;
            }

            if (timeoutId) {
                clearTimeout(timeoutId);
            }

            toast.classList.remove('is-visible');
            toast.classList.add('is-removing');

            removeTimeoutId = setTimeout(() => {
                    toast.remove();
                }, 250);
        }

        function closeToast() {
            removeToast();
        }

        closeButton.addEventListener('click', closeToast);

        /* =====================================================
         * SHOW ANIMATION
         * ===================================================== */

        requestAnimationFrame(() => {
            toast.classList.add(
                'is-visible'
            );
        });

        /* =====================================================
         * AUTO CLOSE
         * ===================================================== */

        if (duration > 0) {
            timeoutId =
                setTimeout(() => {
                    removeToast();
                }, duration);
        }

        /* =====================================================
         * RETURN CONTROL
         * ===================================================== */

        return {
            close: closeToast
        };
    }

    /* =========================================================
     * SHORTCUTS
     * ========================================================= */

    function success(message, options = {}) {

        return show(
            message,
            'success',
            options
        );
    }

    function error(message, options = {}) {

        return show(
            message,
            'error',
            options
        );
    }

    function warning(message, options = {}) {

        return show(
            message,
            'warning',
            options
        );
    }

    function info(message, options = {}) {

        return show(
            message,
            'info',
            options
        );
    }

    /* =========================================================
     * PUBLIC API
     * ========================================================= */

    window.Toast = {
        show,
        success,
        error,
        warning,
        info
    };
})(window);