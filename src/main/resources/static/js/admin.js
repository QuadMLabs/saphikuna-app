document.addEventListener('DOMContentLoaded', () => {

    /*
     * Sidebar
     */

    const sidebar = document.querySelector('.admin-sidebar');

    const sidebarToggle =
        document.querySelector('.sidebar-toggle');

    const sidebarLogo =
        document.querySelector('.sidebar-logo');


    /*
     * Collapse
     */

    sidebarToggle.addEventListener('click', () => {

        sidebar.classList.add('is-collapsed');

    });


    /*
     * Expand
     */

    sidebarLogo.addEventListener('click', () => {

        if (sidebar.classList.contains('is-collapsed')) {

            sidebar.classList.remove('is-collapsed');

        }

    });


    /*
     * Menus
     */

    const sidebarItems =
        document.querySelectorAll('.sidebar-item');


    sidebarItems.forEach(item => {

        item.addEventListener('click', () => {

            const currentGroup =
                item.closest('.sidebar-menu-group');


            /*
             * Close all open menu groups
             * except the one being clicked.
             */

            document
                .querySelectorAll('.sidebar-menu-group.is-open')
                .forEach(group => {

                    if (group !== currentGroup) {

                        group.classList.remove('is-open');

                    }

                });


            /*
             * If this item belongs to a
             * menu with children, toggle it.
             */

            if (currentGroup) {

                currentGroup.classList.toggle('is-open');

            }

        });

    });

    /*
 * Topbar user menu
 */
    const userButton =
        document.querySelector('.topbar-user-button');

    const userMenu =
        document.querySelector('.topbar-user-menu');

    if (userButton && userMenu) {

        userButton.addEventListener('click', (event) => {
            event.stopPropagation();

            userMenu.classList.toggle('is-open');
        });

        userMenu.addEventListener('click', (event) => {
            event.stopPropagation();
        });

        document.addEventListener('click', () => {
            userMenu.classList.remove('is-open');
        });
    }

    /*
 * Table
 */
    const tableSearch = document.querySelector('#tableSearch');
    const pageSize = document.querySelector('#pageSize');
    const table = document.querySelector('.data-table');

    if (table && tableSearch && pageSize) {

        const rows = Array.from(
            table.querySelectorAll('tbody tr')
        );

        function updateTable() {

            const searchTerm =
                tableSearch.value.toLowerCase().trim();

            const limit =
                parseInt(pageSize.value, 10);

            let visibleRows = 0;

            rows.forEach(row => {

                const rowText =
                    row.textContent.toLowerCase();

                const matchesSearch =
                    rowText.includes(searchTerm);

                if (matchesSearch && visibleRows < limit) {

                    row.style.display = '';

                    visibleRows++;

                } else {

                    row.style.display = 'none';

                }

            });
        }

        tableSearch.addEventListener(
            'input',
            updateTable
        );

        pageSize.addEventListener(
            'change',
            updateTable
        );

        updateTable();
    }

});