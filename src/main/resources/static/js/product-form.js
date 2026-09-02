document.addEventListener('DOMContentLoaded', () => {

    const productSaveButton = document.getElementById('productSaveButton');
    //productSaveButton.disabled = true;
    const productName = document.getElementById('name');
    const productPrice = document.getElementById('price');
    const productStatus = document.getElementById('status');
    const productDescription = document.getElementById('description');
    const productCategory = document.getElementById('category');
    const productCollection = document.getElementById('collection');
    const productMessages = document.getElementById('productMessages');
    const productCancelButton = document.getElementById('productCancelButton');

    /* =========================================================
     * MESSAGES
     * ========================================================= */

    const variantRequiredMessage = productMessages?.dataset.variantRequiredMessage || '';
    const savedMessage = productMessages?.dataset.savedMessage || '';
    const saveErrorMessage = productMessages?.dataset.saveErrorMessage || '';

    /* =========================================================
     * PRODUCT IMAGES
     * ========================================================= */

    const imagesContainer = document.querySelector('.product-images');
    const imageInput = document.querySelector('#product-image-input');
    const addImageButton = document.querySelector('.product-image-add');
    const imagesPrimaryLabel = imagesContainer?.dataset.primaryLabel || '';

    let primaryImageItem = null;

    if (imagesContainer && imageInput && addImageButton) {
        /*
         * Open file selector
         */
        addImageButton.addEventListener(
            'click',
            () => {
                imageInput.click();
            }
        );
        /*
         * Add selected images
         */
        imageInput.addEventListener(
            'change',
            (event) => {
                const files =
                    Array.from(event.target.files);

                files.forEach(file => {
                    if (!file.type.startsWith('image/')) {
                        return;
                    }
                    addImage(file);
                });
                /*
                 * Allows selecting the same file again
                 */
                imageInput.value = '';
            }
        );
        /*
         * Create image card
         */
        function addImage(file) {
            const imageUrl = URL.createObjectURL(file);
            const imageItem = document.createElement('div');

            imageItem.className = 'product-image-item';
            imageItem.file = file;

            imageItem.innerHTML = `
                <div class="product-image-preview">
                    <img
                        src="${imageUrl}"
                        alt="Imagen del producto">
                </div>
                <div class="product-image-actions">
                    <button
                        type="button"
                        class="product-image-primary"
                        title="Establecer como principal">
                        <i data-lucide="star"></i>
                    </button>
                    <button
                        type="button"
                        class="product-image-remove"
                        title="Eliminar imagen">
                        <i data-lucide="trash-2"></i>
                    </button>
                </div>
            `;
            /*
             * Insert before "Agregar imagen"
             */
            imagesContainer.insertBefore(
                imageItem,
                addImageButton
            );
            /*
             * Activate Lucide icons
             */
            lucide.createIcons();
            /*
             * Bind actions
             */
            bindImageActions(imageItem);
            /*
             * If this is the first image,
             * automatically make it primary.
             */
            ensurePrimaryImage();
        }
        /*
         * Bind image actions
         */
        function bindImageActions(imageItem) {
            const primaryButton =
                imageItem.querySelector(
                    '.product-image-primary'
                );

            const removeButton =
                imageItem.querySelector(
                    '.product-image-remove'
                );

            /*
             * Set as primary
             */
            primaryButton?.addEventListener(
                'click',
                () => {
                    setPrimaryImage(imageItem);
                }
            );
            /*
             * Remove image
             */
            removeButton?.addEventListener('click', () => {

                const image = imageItem.querySelector('img');

                if (image && image.src.startsWith('blob:')) {
                    URL.revokeObjectURL(image.src);
                }

                const wasPrimary = imageItem === primaryImageItem;

                imageItem.remove();

                if (wasPrimary) {
                    primaryImageItem = null;
                    ensurePrimaryImage();
                }
            }
            );
        }
        /*
         * Set image as primary
         */
        function setPrimaryImage(selectedItem) {

            const imageItems = [
                ...imagesContainer.querySelectorAll(
                    '.product-image-item'
                )
            ];

            if (!imageItems.includes(selectedItem)) {
                return;
            }
            /*
             * Remove primary state from all images
             */
            imageItems.forEach(item => {

                item.classList.remove('is-primary');

                const label = item.querySelector('.product-image-primary-label');

                if (label) {
                    label.remove();
                }
            });
            /*
             * Store selected image as primary
             */
            primaryImageItem = selectedItem;
            /*
             * Add primary state
             */
            selectedItem.classList.add('is-primary');
            /*
             * Add primary label
             */
            const label = document.createElement('span');

            label.className = 'product-image-primary-label';

            label.textContent = imagesPrimaryLabel;

            selectedItem.appendChild(label);
        }
        /*
         * Make sure there is always a primary image
         */
        function ensurePrimaryImage() {

            const imageItems = [
                ...imagesContainer.querySelectorAll(
                    '.product-image-item'
                )
            ];

            if (imageItems.length === 0) {
                primaryImageItem = null;
                return;
            }
            /*
             * Keep current primary if it still exists
             */
            if (primaryImageItem && imageItems.includes(primaryImageItem)) {
                return;
            }
            /*
             * Check if HTML already contains
             * a primary image.
             */
            const existingPrimary =
                imageItems.find(item =>
                    item.classList.contains(
                        'is-primary'
                    )
                );

            if (existingPrimary) {
                primaryImageItem = existingPrimary;
                return;
            }
            /*
             * No primary image exists.
             * Select the first one.
             */
            setPrimaryImage(imageItems[0]);
        }
        /*
         * Bind actions for images already in HTML
         */
        const existingImages =
            imagesContainer.querySelectorAll(
                '.product-image-item'
            );

        existingImages.forEach(imageItem => {
            bindImageActions(imageItem);
        }
        );
        /*
         * Make sure an existing image is primary
         */
        ensurePrimaryImage();
        /*
         * Get primary image
         */
        function getPrimaryImage() {

            const imageItems = [
                ...imagesContainer.querySelectorAll(
                    '.product-image-item'
                )
            ];

            if (imageItems.length === 0) {
                return null;
            }

            if (primaryImageItem && imageItems.includes(primaryImageItem)) {
                return primaryImageItem;
            }

            return imageItems[0];
        }
    }
    /* =========================================================
     * VARIANT MODAL
     * ========================================================= */

    const variantModal = document.getElementById('variantModal');
    const variantModalClose = document.getElementById('variantModalClose');
    const variantModalCancel = document.getElementById('variantModalCancel');
    const variantDifferentPrice = document.getElementById('variantDifferentPrice');
    const variantPriceField = document.getElementById('variantPriceField');
    const variantModalSave = document.getElementById('variantModalSave');
    const variantColor = document.getElementById('variantColor');
    const variantStock = document.getElementById('variantStock');
    const variantPrice = document.getElementById('variantPrice');
    const variantSizeOptions = document.querySelectorAll('.variant-size-option');

    /* =========================================================
     * VARIANTS TABLE
     * ========================================================= */

    const variantTableContainer =
        document.querySelector(
            '.variant-table-container'
        );

    const variantTableBody =
        document.getElementById(
            'variantTableBody'
        );

    const variantEmptyState =
        document.querySelector(
            '.variant-empty-state'
        );


    const variantBasePriceLabel =
        variantTableContainer?.dataset.basePriceLabel || '';

    const variantEditTitle =
        variantTableContainer?.dataset.editTitle || '';

    const variantDeleteTitle =
        variantTableContainer?.dataset.deleteTitle || '';

    const variantAddTitle =
        variantTableContainer?.dataset.addTitle || '';

    const variantSaveChangesTitle =
        variantTableContainer?.dataset.saveChangesTitle || '';


    /*
     * Variants stored in memory
     */
    const variants = [];

    /*
 * =========================================================
 * SAVE BUTTON STATE
 * =========================================================
 */

    function setSavingState(isSaving) {

        if (!productSaveButton) {
            return;
        }

        if (isSaving) {

            productSaveButton.disabled = true;

            if (productCancelButton) {
                productCancelButton.disabled = true;
            }

            productSaveButton.dataset.originalContent =
                productSaveButton.innerHTML;

            productSaveButton.innerHTML = `
            <span class="button-spinner"></span>
            <span>Guardando...</span>
        `;

        } else {

            productSaveButton.disabled = false;

            if (productCancelButton) {
                productCancelButton.disabled = false;
            }

            const originalContent =
                productSaveButton.dataset.originalContent;

            if (originalContent) {
                productSaveButton.innerHTML =
                    originalContent;
            }
        }
    }

    /*
 * =========================================================
 * RESET PRODUCT FORM
 * =========================================================
 */

    function resetProductForm() {

        /*
         * Product fields
         */
        if (productName) {
            productName.value = '';
        }

        if (productPrice) {
            productPrice.value = '';
        }

        if (productDescription) {
            productDescription.value = '';
        }

        if (productCategory) {
            productCategory.value = '';
        }

        if (productCollection) {
            productCollection.value = '';
        }

        if (productStatus) {
            productStatus.checked = true;
        }

        /*
         * Remove product validation errors
         */
        const productFields =
            document.querySelectorAll(
                '.form-field.has-error'
            );

        productFields.forEach(field => {
            field.classList.remove('has-error');
            removeFieldError(field);
        });

        /*
         * Remove images
         */
        if (imagesContainer) {

            const imageItems =
                imagesContainer.querySelectorAll(
                    '.product-image-item'
                );

            imageItems.forEach(imageItem => {

                const image =
                    imageItem.querySelector('img');

                if (
                    image &&
                    image.src.startsWith('blob:')
                ) {
                    URL.revokeObjectURL(image.src);
                }

                imageItem.remove();
            });
        }

        primaryImageItem = null;

        /*
         * Remove variants
         */
        variants.length = 0;

        renderVariants();

        /*
         * Reset image input
         */
        if (imageInput) {
            imageInput.value = '';
        }

        /*
         * Reset variant modal
         */
        editingVariantIndex = null;

        resetVariantModal();
        updateVariantModalMode();
    }

    /* =========================================================
     * PRODUCT VALIDATION / SAVE
     * ========================================================= */

    productSaveButton?.addEventListener(
        'click',
        async () => {

            /*
             * Product fields
             */
            const nameField =
                productName?.closest('.form-field');

            const priceField =
                productPrice?.closest('.form-field');


            /*
             * Clear previous errors
             */
            nameField?.classList.remove(
                'has-error'
            );

            priceField?.classList.remove(
                'has-error'
            );

            removeFieldError(nameField);
            removeFieldError(priceField);


            let isValid = true;


            /*
             * -----------------------------------------------------
             * NAME
             * -----------------------------------------------------
             */

            if (!productName?.value.trim()) {

                nameField?.classList.add(
                    'has-error'
                );

                showFieldError(nameField);

                isValid = false;
            }


            /*
             * -----------------------------------------------------
             * PRICE
             * -----------------------------------------------------
             */

            if (
                productPrice?.value === '' ||
                Number(productPrice.value) < 0
            ) {

                priceField?.classList.add(
                    'has-error'
                );

                showFieldError(priceField);

                isValid = false;
            }


            /*
             * Stop if product fields are invalid
             */
            if (!isValid) {

                if (!productName?.value.trim()) {
                    productName?.focus();
                } else {
                    productPrice?.focus();
                }

                return;
            }


            /*
             * A product must have at least one variant
             */
            if (variants.length === 0) {

                Toast.error(
                    variantRequiredMessage
                );

                return;
            }


            /*
             * =====================================================
             * BUILD PRODUCT REQUEST
             * =====================================================
             */

            const collectionId =
                productCollection?.value || null;


            const productRequest = {

                name:
                    productName.value.trim(),

                generalPrice:
                    Number(productPrice.value),

                description:
                    productDescription?.value.trim() || null,

                categoryId:
                    productCategory?.value || null,

                status:
                    productStatus?.checked
                        ? 'Activo'
                        : 'Inactivo',

                collectionIds:
                    collectionId
                        ? [collectionId]
                        : [],

                variants:
                    variants.map(variant => ({

                        colorId:
                            variant.colorId,

                        sizeIds:
                            variant.sizeIds,

                        stock:
                            variant.stock,

                        differentPrice:
                            variant.differentPrice,

                        price:
                            variant.differentPrice
                                ? variant.price
                                : null
                    }))
            };


            /*
             * =====================================================
             * BUILD FORM DATA
             * =====================================================
             */

            const formData =
                new FormData();


            /*
             * Product JSON
             */
            formData.append(
                'product',
                new Blob(
                    [
                        JSON.stringify(
                            productRequest
                        )
                    ],
                    {
                        type: 'application/json'
                    }
                )
            );


            /*
             * Product images
             */
            const imageItems = imagesContainer
                ? [
                    ...imagesContainer.querySelectorAll(
                        '.product-image-item'
                    )
                ]
                : [];


            imageItems.forEach(
                imageItem => {

                    if (!imageItem.file) {
                        return;
                    }

                    formData.append(
                        'images',
                        imageItem.file
                    );
                }
            );


            /*
             * Primary image index
             */
            const primaryImage =
                getPrimaryImageForSave(
                    imageItems
                );


            let primaryImageIndex = 0;


            if (primaryImage) {

                const index =
                    imageItems.indexOf(
                        primaryImage
                    );

                if (index >= 0) {
                    primaryImageIndex = index;
                }
            }


            formData.append(
                'primaryImageIndex',
                String(primaryImageIndex)
            );


            /*
             * =====================================================
             * SEND TO BACKEND
             * =====================================================
             */

            try {

                setSavingState(true);

                const response =
                    await fetch(
                        '/admin/products',
                        {
                            method: 'POST',
                            body: formData
                        }
                    );


                /*
                 * Backend error
                 */
                if (!response.ok) {

                    throw new Error(
                        `HTTP ${response.status}`
                    );
                }


                /*
                 * Product saved successfully
                 */
                if (savedMessage) {

                    Toast.success(
                        savedMessage
                    );
                }

                resetProductForm();


            } catch (error) {

                console.error(
                    'Error al guardar el producto:',
                    error
                );


                /*
                 * Error message comes from
                 * messages.properties
                 */
                if (saveErrorMessage) {

                    Toast.error(
                        saveErrorMessage
                    );
                }


            } finally {

                setSavingState(false);
            }
        }
    );


    /*
     * Get primary image specifically for saving
     */
    function getPrimaryImageForSave(
        imageItems
    ) {

        if (!imageItems.length) {
            return null;
        }


        const primary =
            imageItems.find(item =>
                item.classList.contains(
                    'is-primary'
                )
            );


        return primary || imageItems[0];
    }


    /* =========================================================
     * REMOVE NAME ERROR
     * ========================================================= */

    productName?.addEventListener(
        'input',
        () => {

            if (productName.value.trim()) {

                const field =
                    productName.closest(
                        '.form-field'
                    );

                field?.classList.remove(
                    'has-error'
                );

                removeFieldError(field);
            }
        }
    );


    /* =========================================================
     * REMOVE PRICE ERROR
     * ========================================================= */

    productPrice?.addEventListener(
        'input',
        () => {

            if (
                productPrice.value !== '' &&
                Number(productPrice.value) >= 0
            ) {

                const field =
                    productPrice.closest(
                        '.form-field'
                    );

                field?.classList.remove(
                    'has-error'
                );

                removeFieldError(field);
            }
        }
    );


    /* =========================================================
     * VARIANT EDITING INDEX
     * ========================================================= */

    let editingVariantIndex = null;


    /* =========================================================
     * OPEN VARIANT MODAL
     * ========================================================= */

    const addVariantButton =
        document.querySelector(
            '.variant-add-button'
        );


    addVariantButton?.addEventListener(
        'click',
        () => {

            editingVariantIndex = null;

            resetVariantModal();

            updateVariantModalMode();

            openVariantModal();
        }
    );


    /* =========================================================
     * CLOSE VARIANT MODAL
     * ========================================================= */

    variantModalClose?.addEventListener(
        'click',
        closeVariantModal
    );

    variantModalCancel?.addEventListener(
        'click',
        closeVariantModal
    );


    /*
     * Close clicking outside
     */
    variantModal?.addEventListener(
        'click',
        (event) => {

            if (
                event.target === variantModal
            ) {

                closeVariantModal();
            }
        }
    );


    /* =========================================================
     * DIFFERENT PRICE
     * ========================================================= */

    variantDifferentPrice?.addEventListener(
        'change',
        () => {

            if (
                variantDifferentPrice.checked
            ) {

                variantPriceField?.classList.add(
                    'is-visible'
                );

            } else {

                variantPriceField?.classList.remove(
                    'is-visible'
                );
            }
        }
    );


    /* =========================================================
     * SIZE SELECTION
     * ========================================================= */

    variantSizeOptions.forEach(
        button => {

            button.addEventListener(
                'click',
                () => {

                    button.classList.toggle(
                        'is-selected'
                    );


                    /*
                     * Remove error as soon as
                     * the user selects at least
                     * one size.
                     */
                    const sizesField =
                        button.closest(
                            '.form-field'
                        );


                    const selectedSizes =
                        document.querySelectorAll(
                            '.variant-size-option.is-selected'
                        );


                    if (
                        selectedSizes.length > 0
                    ) {

                        sizesField?.classList.remove(
                            'has-error'
                        );

                        removeFieldError(
                            sizesField
                        );
                    }
                }
            );
        }
    );


    /* =========================================================
     * SAVE VARIANT
     * ========================================================= */

    variantModalSave?.addEventListener(
        'click',
        () => {

            if (
                !variantColor ||
                !variantStock
            ) {
                return;
            }


            const colorField =
                variantColor.closest(
                    '.form-field'
                );

            const stockField =
                variantStock.closest(
                    '.form-field'
                );

            const sizesField =
                variantSizeOptions.length > 0
                    ? variantSizeOptions[0]
                        .closest('.form-field')
                    : null;


            const selectedSizes = [
                ...document.querySelectorAll(
                    '.variant-size-option.is-selected'
                )
            ];


            let isValid = true;


            /*
             * Clear previous errors
             */
            colorField?.classList.remove(
                'has-error'
            );

            stockField?.classList.remove(
                'has-error'
            );

            sizesField?.classList.remove(
                'has-error'
            );

            removeFieldError(colorField);
            removeFieldError(stockField);
            removeFieldError(sizesField);


            /*
             * -----------------------------------------------------
             * COLOR
             * -----------------------------------------------------
             */

            if (!variantColor.value) {

                colorField?.classList.add(
                    'has-error'
                );

                showFieldError(colorField);

                isValid = false;
            }


            /*
             * -----------------------------------------------------
             * SIZE
             * -----------------------------------------------------
             */

            if (
                selectedSizes.length === 0
            ) {

                sizesField?.classList.add(
                    'has-error'
                );

                showFieldError(sizesField);

                isValid = false;
            }


            /*
             * -----------------------------------------------------
             * STOCK
             * -----------------------------------------------------
             */

            if (
                variantStock.value === '' ||
                Number(variantStock.value) < 0
            ) {

                stockField?.classList.add(
                    'has-error'
                );

                showFieldError(stockField);

                isValid = false;
            }


            /*
             * -----------------------------------------------------
             * PRICE
             * -----------------------------------------------------
             */

            if (
                variantDifferentPrice?.checked &&
                (
                    variantPrice?.value === '' ||
                    Number(variantPrice.value) < 0
                )
            ) {

                const priceField =
                    variantPrice?.closest(
                        '.form-field'
                    );

                priceField?.classList.add(
                    'has-error'
                );

                showFieldError(priceField);

                isValid = false;
            }


            /*
             * Stop if invalid
             */
            if (!isValid) {
                return;
            }


            /*
             * -----------------------------------------------------
             * CREATE / UPDATE VARIANT
             * -----------------------------------------------------
             */

            const variant = {

                colorId:
                    variantColor.value,

                colorName:
                    variantColor.options[
                        variantColor.selectedIndex
                    ].textContent.trim(),

                sizeIds:
                    selectedSizes.map(
                        size => size.value
                    ),

                sizeNames:
                    selectedSizes.map(
                        size =>
                            size.textContent.trim()
                    ),

                stock:
                    Number(
                        variantStock.value
                    ),

                differentPrice:
                    variantDifferentPrice?.checked ??
                    false,

                price:
                    variantDifferentPrice?.checked
                        ? Number(
                            variantPrice?.value || 0
                        )
                        : null
            };


            /*
             * Editing existing variant
             */
            if (
                editingVariantIndex !== null &&
                variants[editingVariantIndex]
            ) {

                variants[
                    editingVariantIndex
                ] = variant;

            } else {

                /*
                 * Creating new variant
                 */
                variants.push(variant);
            }


            /*
             * Render table
             */
            renderVariants();


            /*
             * Reset modal
             */
            resetVariantModal();


            /*
             * Return to create mode
             */
            editingVariantIndex = null;

            updateVariantModalMode();


            /*
             * Close modal
             */
            closeVariantModal();
        }
    );


    /* =========================================================
     * RENDER VARIANTS
     * ========================================================= */

    function renderVariants() {

        if (
            !variantTableBody ||
            !variantTableContainer ||
            !variantEmptyState
        ) {
            return;
        }


        /*
         * Clear current table
         */
        variantTableBody.innerHTML = '';


        /*
         * No variants
         */
        if (variants.length === 0) {

            variantTableContainer.classList.remove(
                'is-visible'
            );

            variantEmptyState.classList.remove(
                'is-hidden'
            );

            return;
        }


        /*
         * Show table
         */
        variantTableContainer.classList.add(
            'is-visible'
        );

        variantEmptyState.classList.add(
            'is-hidden'
        );


        /*
         * Create rows
         */
        variants.forEach(
            (variant, index) => {

                const row =
                    document.createElement('tr');


                const priceText =
                    variant.differentPrice
                        ? Number(
                            variant.price
                        ).toFixed(2)
                        : variantBasePriceLabel;


                row.innerHTML = `

                    <td>
                        ${escapeHtml(
                    variant.colorName
                )}
                    </td>

                    <td>
                        ${escapeHtml(
                    variant.sizeNames.join(', ')
                )}
                    </td>

                    <td>
                        ${variant.stock}
                    </td>

                    <td>
                        ${escapeHtml(
                    priceText
                )}
                    </td>

                    <td>

                        <div class="variant-actions">

                            <button
                                type="button"
                                class="variant-action-button variant-edit-button"
                                data-index="${index}"
                                title="${variantEditTitle}">

                                <i data-lucide="pencil"></i>

                            </button>

                            <button
                                type="button"
                                class="variant-action-button variant-delete-button"
                                data-index="${index}"
                                title="${variantDeleteTitle}">

                                <i data-lucide="trash-2"></i>

                            </button>

                        </div>

                    </td>
                `;


                variantTableBody.appendChild(row);
            }
        );


        /*
         * Activate Lucide icons
         */
        lucide.createIcons();


        /*
         * Bind table actions
         */
        bindVariantActions();
    }


    /* =========================================================
     * VARIANT TABLE ACTIONS
     * ========================================================= */

    function bindVariantActions() {

        const editButtons =
            variantTableBody?.querySelectorAll(
                '.variant-edit-button'
            );

        const deleteButtons =
            variantTableBody?.querySelectorAll(
                '.variant-delete-button'
            );


        /*
         * EDIT
         */
        editButtons?.forEach(
            button => {

                button.addEventListener(
                    'click',
                    () => {

                        const index =
                            Number(
                                button.dataset.index
                            );


                        if (
                            !Number.isInteger(index) ||
                            index < 0 ||
                            index >= variants.length
                        ) {
                            return;
                        }


                        editVariant(index);
                    }
                );
            }
        );


        /*
         * DELETE
         */
        deleteButtons?.forEach(
            button => {

                button.addEventListener(
                    'click',
                    () => {

                        const index =
                            Number(
                                button.dataset.index
                            );


                        if (
                            !Number.isInteger(index) ||
                            index < 0 ||
                            index >= variants.length
                        ) {
                            return;
                        }


                        variants.splice(
                            index,
                            1
                        );


                        renderVariants();
                    }
                );
            }
        );
    }


    /* =========================================================
     * EDIT VARIANT
     * ========================================================= */

    function editVariant(index) {

        const variant =
            variants[index];


        if (!variant) {
            return;
        }


        /*
         * Store index being edited
         */
        editingVariantIndex =
            index;


        /*
         * Set color
         */
        if (variantColor) {

            variantColor.value =
                variant.colorId;
        }


        /*
         * Set sizes
         */
        variantSizeOptions.forEach(
            button => {

                button.classList.remove(
                    'is-selected'
                );


                if (
                    variant.sizeIds.includes(
                        button.value
                    )
                ) {

                    button.classList.add(
                        'is-selected'
                    );
                }
            }
        );


        /*
         * Set stock
         */
        if (variantStock) {

            variantStock.value =
                variant.stock;
        }


        /*
         * Set different price
         */
        if (variantDifferentPrice) {

            variantDifferentPrice.checked =
                variant.differentPrice;
        }


        /*
         * Set price
         */
        if (variantPrice) {

            variantPrice.value =
                variant.differentPrice &&
                    variant.price !== null
                    ? variant.price
                    : '';
        }


        /*
         * Show / hide price field
         */
        if (variantPriceField) {

            if (
                variant.differentPrice
            ) {

                variantPriceField.classList.add(
                    'is-visible'
                );

            } else {

                variantPriceField.classList.remove(
                    'is-visible'
                );
            }
        }


        /*
         * Clear validation errors
         */
        const fields =
            variantModal?.querySelectorAll(
                '.form-field'
            );


        fields?.forEach(
            field => {

                field.classList.remove(
                    'has-error'
                );

                removeFieldError(field);
            }
        );


        /*
         * Change modal to edit mode
         */
        updateVariantModalMode();


        /*
         * Open modal
         */
        openVariantModal();
    }


    /* =========================================================
     * UPDATE MODAL MODE
     * ========================================================= */

    function updateVariantModalMode() {

        if (!variantModal) {
            return;
        }


        const title =
            variantModal.querySelector(
                '.variant-modal-header h3'
            );

        const buttonText =
            variantModalSave?.querySelector(
                'span'
            );


        if (
            editingVariantIndex !== null
        ) {

            /*
             * Edit mode
             */
            if (title) {

                title.textContent =
                    variantEditTitle;
            }


            if (buttonText) {

                buttonText.textContent =
                    variantSaveChangesTitle;
            }

        } else {

            /*
             * Create mode
             */
            if (title) {

                title.textContent =
                    variantAddTitle;
            }


            if (buttonText) {

                buttonText.textContent =
                    variantAddTitle;
            }
        }
    }


    /* =========================================================
     * RESET VARIANT MODAL
     * ========================================================= */

    function resetVariantModal() {

        /*
         * Color
         */
        if (variantColor) {

            variantColor.value = '';
        }


        /*
         * Sizes
         */
        variantSizeOptions.forEach(
            button => {

                button.classList.remove(
                    'is-selected'
                );
            }
        );


        /*
         * Stock
         */
        if (variantStock) {

            variantStock.value = '0';
        }


        /*
         * Different price
         */
        if (variantDifferentPrice) {

            variantDifferentPrice.checked =
                false;
        }


        /*
         * Price
         */
        if (variantPrice) {

            variantPrice.value = '';
        }


        /*
         * Hide price field
         */
        variantPriceField?.classList.remove(
            'is-visible'
        );


        /*
         * Remove validation errors
         */
        const fields =
            variantModal?.querySelectorAll(
                '.form-field'
            );


        fields?.forEach(
            field => {

                field.classList.remove(
                    'has-error'
                );

                removeFieldError(field);
            }
        );
    }


    /* =========================================================
     * REMOVE COLOR ERROR
     * ========================================================= */

    variantColor?.addEventListener(
        'change',
        () => {

            if (variantColor.value) {

                const field =
                    variantColor.closest(
                        '.form-field'
                    );

                field?.classList.remove(
                    'has-error'
                );

                removeFieldError(field);
            }
        }
    );


    /* =========================================================
     * REMOVE STOCK ERROR
     * ========================================================= */

    variantStock?.addEventListener(
        'input',
        () => {

            if (
                variantStock.value !== '' &&
                Number(variantStock.value) >= 0
            ) {

                const field =
                    variantStock.closest(
                        '.form-field'
                    );

                field?.classList.remove(
                    'has-error'
                );

                removeFieldError(field);
            }
        }
    );


    /* =========================================================
     * REMOVE PRICE ERROR
     * ========================================================= */

    variantPrice?.addEventListener(
        'input',
        () => {

            if (
                variantPrice.value !== '' &&
                Number(variantPrice.value) >= 0
            ) {

                const field =
                    variantPrice.closest(
                        '.form-field'
                    );

                field?.classList.remove(
                    'has-error'
                );

                removeFieldError(field);
            }
        }
    );


    /* =========================================================
     * ESCAPE KEY
     * ========================================================= */

    document.addEventListener(
        'keydown',
        (event) => {

            if (
                event.key === 'Escape' &&
                variantModal?.classList.contains(
                    'is-visible'
                )
            ) {

                closeVariantModal();
            }
        }
    );


    /* =========================================================
     * MODAL FUNCTIONS
     * ========================================================= */

    function openVariantModal() {

        if (!variantModal) {
            return;
        }


        variantModal.classList.add(
            'is-visible'
        );


        document.body.style.overflow =
            'hidden';
    }


    function closeVariantModal() {

        if (!variantModal) {
            return;
        }


        variantModal.classList.remove(
            'is-visible'
        );


        document.body.style.overflow =
            '';
    }


    /* =========================================================
     * VALIDATION HELPERS
     * ========================================================= */

    function showFieldError(field) {

        if (!field) {
            return;
        }


        /*
         * Avoid duplicated messages
         */
        removeFieldError(field);


        const message =
            field.dataset.errorRequired;


        if (!message) {
            return;
        }


        const error =
            document.createElement('span');


        error.className =
            'form-field-error';


        error.textContent =
            message;


        field.appendChild(error);
    }


    function removeFieldError(field) {

        if (!field) {
            return;
        }


        const error =
            field.querySelector(
                '.form-field-error'
            );


        if (error) {
            error.remove();
        }
    }


    /* =========================================================
     * HTML ESCAPE
     * ========================================================= */

    function escapeHtml(value) {

        return String(value)
            .replace(
                /&/g,
                '&amp;'
            )
            .replace(
                /</g,
                '&lt;'
            )
            .replace(
                />/g,
                '&gt;'
            )
            .replace(
                /"/g,
                '&quot;'
            )
            .replace(
                /'/g,
                '&#039;'
            );
    }

});