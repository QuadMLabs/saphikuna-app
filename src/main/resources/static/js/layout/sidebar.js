/* Colapsar/expandir el menú lateral en pantallas grandes. */
document.addEventListener('DOMContentLoaded', () => {
    const sidebar = document.querySelector('.admin-sidebar');
    const sidebarToggle = document.querySelector('.sidebar-toggle');
    const sidebarLogo = document.querySelector('.sidebar-logo');

    if (sidebar && sidebarToggle) {
        sidebarToggle.addEventListener('click', () => {
            sidebar.classList.add('is-collapsed');
        });
    }

    if (sidebar && sidebarLogo) {
        sidebarLogo.addEventListener('click', () => {
            sidebar.classList.remove('is-collapsed');
        });
    }
});
