document.addEventListener('DOMContentLoaded', () => {
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
            if (event.key === 'Escape' && imageModal.classList.contains('is-visible')) {
                closeImageModal();
            }
        });
    }
});
