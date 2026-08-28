document.addEventListener('DOMContentLoaded', () => {
    /* Sidebar */
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

    /* Topbar user menu */
    const userButton = document.querySelector('.topbar-user-button');
    const userMenu = document.querySelector('.topbar-user-menu');

    if (userButton && userMenu) {
        userButton.addEventListener('click', () => {
            userMenu.classList.toggle('is-open');
        });
    }

    /* Table */
    const table = document.querySelector('.data-table');
    const tableSearch = document.querySelector('#tableSearch');
    const pageSize = document.querySelector('#pageSize');
    const emptyState = document.querySelector('#tableEmptyState');
    const emptyMessage = document.querySelector('#tableEmptyMessage');
    let multiFilters = [];

    if (table && tableSearch && pageSize) {
        const rows = Array.from(table.querySelectorAll('tbody tr:not(#tableEmptyState)'));
        const visibleCount = document.querySelector('#tableVisibleCount');
        const totalCount = document.querySelector('#tableTotalCount');
        const paginationPages = document.querySelector('#paginationPages');
        const previousButton = document.querySelector('[data-page="previous"]');
        const nextButton = document.querySelector('[data-page="next"]');
        const tableFilters = Array.from(
            document.querySelectorAll('[data-filter-column]')
        );
        const clearFiltersButton = document.querySelector('#clearTableFilters');
        let currentPage = 1;

        multiFilters = tableFilters.filter(
            filter => filter.dataset.filterMatch === 'multi'
        );

        function getSelectedValues(filter) {
            return Array.from(
                filter.querySelectorAll('input[type="checkbox"]:checked')
            ).map(input => input.value.toLowerCase().trim());
        }

        function updateMultiFilterLabel(filter) {
            const label = filter.querySelector('.table-multi-filter-label');

            if (!label) {
                return;
            }

            const selectedValues = Array.from(
                filter.querySelectorAll('input[type="checkbox"]:checked')
            ).map(input => input.value);

            label.textContent = selectedValues.length
                ? selectedValues.join(', ')
                : 'Todas';
        }

        function getFilteredRows() {
            const searchTerm = tableSearch.value.toLowerCase().trim();

            return rows.filter(row => {
                if (!row.textContent.toLowerCase().includes(searchTerm)) {
                    return false;
                }

                return tableFilters.every(filter => {
                    const columnType = filter.dataset.filterColumn;
                    const filterMatch = filter.dataset.filterMatch || 'exact';
                    const cell = row.querySelector(
                        `[data-column-type="${columnType}"]`
                    );

                    if (filterMatch === 'multi') {
                        const selectedValues = getSelectedValues(filter);

                        if (!selectedValues.length) {
                            return true;
                        }

                        if (!cell) {
                            return false;
                        }

                        const cellValues = Array.from(
                            cell.querySelectorAll('.table-tag')
                        ).map(tag => tag.textContent.toLowerCase().trim());

                        return selectedValues.some(value =>
                            cellValues.includes(value)
                        );
                    }

                    const selectedValue = (filter.value || '')
                        .toLowerCase()
                        .trim();

                    if (!selectedValue) {
                        return true;
                    }

                    return cell
                        ? cell.textContent.toLowerCase().trim() === selectedValue
                        : false;
                });
            });
        }

        function renderPagination(totalPages) {
            paginationPages.innerHTML = '';

            for (let page = 1; page <= totalPages; page++) {
                const button = document.createElement('button');

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

            previousButton.disabled = currentPage === 1;
            nextButton.disabled = currentPage === totalPages;
        }

        function updateTable() {
            const filteredRows = getFilteredRows();
            const hasRows = rows.length > 0;
            const limit = parseInt(pageSize.value, 10);
            const totalRows = filteredRows.length;
            const totalPages = Math.max(1, Math.ceil(totalRows / limit));

            if (currentPage > totalPages) {
                currentPage = totalPages;
            }

            const start = (currentPage - 1) * limit;
            const end = start + limit;

            rows.forEach(row => {
                row.style.display = 'none';
            });

            filteredRows.slice(start, end).forEach(row => {
                row.style.display = '';
            });

            visibleCount.textContent = Math.min(
                limit,
                Math.max(0, totalRows - start)
            );
            totalCount.textContent = totalRows;

            if (emptyState && emptyMessage) {
                if (totalRows === 0) {
                    emptyState.style.display = '';
                    emptyMessage.textContent = hasRows ? table.dataset.emptyFilteredMessage : table.dataset.emptyMessage;
                } else {
                    emptyState.style.display = 'none';
                }
            }
            renderPagination(totalPages);
        }

        /* Exact filters */
        tableFilters
            .filter(filter => (filter.dataset.filterMatch || 'exact') === 'exact')
            .forEach(filter => {
                filter.addEventListener('change', () => {
                    currentPage = 1;
                    updateTable();
                });
            });

        /* Multi-value filters */
        multiFilters.forEach(filter => {
            const button = filter.querySelector('.table-multi-filter-button');
            const checkboxes = filter.querySelectorAll('input[type="checkbox"]');

            if (button) {
                button.addEventListener('click', () => {
                    multiFilters.forEach(otherFilter => {
                        if (otherFilter !== filter) {
                            otherFilter.classList.remove('is-open');
                        }
                    });

                    filter.classList.toggle('is-open');
                });
            }

            checkboxes.forEach(checkbox => {
                checkbox.addEventListener('change', () => {
                    updateMultiFilterLabel(filter);
                    currentPage = 1;
                    updateTable();
                });
            });
        });

        tableSearch.addEventListener('input', () => {
            currentPage = 1;
            updateTable();
        });

        pageSize.addEventListener('change', () => {
            currentPage = 1;
            updateTable();
        });

        if (clearFiltersButton) {
            clearFiltersButton.addEventListener('click', () => {
                tableFilters.forEach(filter => {
                    if (filter.dataset.filterMatch === 'multi') {
                        filter
                            .querySelectorAll('input[type="checkbox"]')
                            .forEach(checkbox => {
                                checkbox.checked = false;
                            });

                        updateMultiFilterLabel(filter);
                        filter.classList.remove('is-open');
                        return;
                    }

                    filter.value = '';
                });

                currentPage = 1;
                updateTable();
            });
        }

        previousButton.addEventListener('click', () => {
            if (currentPage > 1) {
                currentPage--;
                updateTable();
            }
        });

        nextButton.addEventListener('click', () => {
            const totalPages = Math.max(
                1,
                Math.ceil(getFilteredRows().length / parseInt(pageSize.value, 10))
            );

            if (currentPage < totalPages) {
                currentPage++;
                updateTable();
            }
        });

        updateTable();
    }

    /* Table filters panel */
    const filtersButton = document.querySelector('#toggleTableFilters');
    const filterPanel = document.querySelector('.table-filter-panel');

    if (filtersButton && filterPanel) {
        filtersButton.addEventListener('click', () => {
            filterPanel.classList.toggle('is-visible');
        });
    }

    /* Close menus and multi-value filters when clicking outside. */
    document.addEventListener('click', event => {
        if (
            userButton &&
            userMenu &&
            !userButton.contains(event.target) &&
            !userMenu.contains(event.target)
        ) {
            userMenu.classList.remove('is-open');
        }

        multiFilters.forEach(filter => {
            if (!filter.contains(event.target)) {
                filter.classList.remove('is-open');
            }
        });
    });

    /* Image modal */
    const imageModal = document.querySelector('#imageModal');
    const imageModalImage = document.querySelector('#imageModalImage');
    const imageModalClose = document.querySelector('#imageModalClose');
    const thumbnails = document.querySelectorAll('.table-thumbnail-button');

    if (imageModal && imageModalImage && imageModalClose) {
        function closeImageModal() {
            imageModal.classList.remove('is-visible');
            imageModalImage.src = '';
        }

        thumbnails.forEach(button => {
            button.addEventListener('click', () => {
                const image = button.querySelector('.table-thumbnail');

                if (!image) {
                    return;
                }

                imageModalImage.src = image.src;
                imageModalImage.alt = image.alt;
                imageModal.classList.add('is-visible');
            });
        });

        imageModalClose.addEventListener('click', closeImageModal);

        imageModal.addEventListener('click', event => {
            if (event.target === imageModal) {
                closeImageModal();
            }
        });

        document.addEventListener('keydown', event => {
            if (
                event.key === 'Escape' &&
                imageModal.classList.contains('is-visible')
            ) {
                closeImageModal();
            }
        });
    }
});
