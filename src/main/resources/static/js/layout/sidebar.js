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

    /* Sidebar menus */
    document.querySelectorAll('.sidebar-item').forEach(item => {
        item.addEventListener('click', () => {
            const currentGroup = item.closest('.sidebar-menu-group');

            document
                .querySelectorAll('.sidebar-menu-group.is-open')
                .forEach(group => {
                    if (group !== currentGroup) {
                        group.classList.remove('is-open');
                    }
                });

            if (currentGroup) {
                currentGroup.classList.toggle('is-open');
            }
        });
    });
});
