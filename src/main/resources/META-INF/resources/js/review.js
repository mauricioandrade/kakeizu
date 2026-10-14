// Copies a suggested correction into its field. The person still compares it with the photo, can edit it,
// and confirms; the server checks it again like anything typed.
document.addEventListener('click', (event) => {
    const button = event.target.closest('[data-fill]');
    const input = button && document.getElementById(button.dataset.fill);
    if (!input || input.disabled) {
        return;
    }
    input.value = button.dataset.value;
    input.focus();
});
