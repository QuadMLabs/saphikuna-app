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

        const visibleCount = document.querySelector(
            '#tableVisibleCount'
        );

        const totalCount = document.querySelector(
            '#tableTotalCount'
        );

        const paginationPages = document.querySelector(
            '#paginationPages'
        );

        const previousButton = document.querySelector(
            '[data-page="previous"]'
        );

        const nextButton = document.querySelector(
            '[data-page="next"]'
        );

        let currentPage = 1;

        function getFilteredRows() {

            const searchTerm =
                tableSearch.value.toLowerCase().trim();

            return rows.filter(row => {

                const rowText =
                    row.textContent.toLowerCase();

                return rowText.includes(searchTerm);

            });
        }

        function renderPagination(totalPages) {

            paginationPages.innerHTML = '';

            for (let page = 1; page <= totalPages; page++) {

                const button =
                    document.createElement('button');

                button.type = 'button';
                button.className = 'pagination-page';

                button.textContent = page;

                if (page === currentPage) {
                    button.classList.add('is-active');
                }

                button.addEventListener('click', () => {

                    currentPage = page;

                    updateTable();

                });

                paginationPages.appendChild(button);
            }

            previousButton.disabled =
                currentPage === 1;

            nextButton.disabled =
                currentPage === totalPages;
        }

        function updateTable() {

            const filteredRows =
                getFilteredRows();

            const limit =
                parseInt(pageSize.value, 10);

            const totalRows =
                filteredRows.length;

            const totalPages =
                Math.max(1, Math.ceil(totalRows / limit));

            if (currentPage > totalPages) {
                currentPage = totalPages;
            }

            const start =
                (currentPage - 1) * limit;

            const end =
                start + limit;

            rows.forEach(row => {
                row.style.display = 'none';
            });

            filteredRows
                .slice(start, end)
                .forEach(row => {
                    row.style.display = '';
                });

            const currentVisibleCount =
                Math.min(
                    limit,
                    Math.max(0, totalRows - start)
                );

            visibleCount.textContent =
                currentVisibleCount;

            totalCount.textContent =
                totalRows;

            renderPagination(totalPages);
        }

        tableSearch.addEventListener(
            'input',
            () => {

                currentPage = 1;

                updateTable();

            }
        );

        pageSize.addEventListener(
            'change',
            () => {

                currentPage = 1;

                updateTable();

            }
        );

        previousButton.addEventListener(
            'click',
            () => {

                if (currentPage > 1) {

                    currentPage--;

                    updateTable();

                }
            }
        );

        nextButton.addEventListener(
            'click',
            () => {

                const totalRows =
                    getFilteredRows().length;

                const limit =
                    parseInt(pageSize.value, 10);

                const totalPages =
                    Math.ceil(totalRows / limit);

                if (currentPage < totalPages) {

                    currentPage++;

                    updateTable();

                }
            }
        );

        updateTable();
    }

});