document.addEventListener('DOMContentLoaded', () => {
    const userButton = document.querySelector('.topbar-user-button');
    const userMenu = document.querySelector('.topbar-user-menu');

    if (userButton && userMenu) {
        userButton.addEventListener('click', () => {
            userMenu.classList.toggle('is-open');
        });
    }
});
