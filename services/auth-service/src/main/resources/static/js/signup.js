document.addEventListener('DOMContentLoaded', function() {
    const form = document.getElementById('signup-form');
    const passwordInput = document.getElementById('password');
    const confirmPasswordInput = document.getElementById('confirmPassword');

    // Border highlights on focus
    if (form) {
        const inputs = form.querySelectorAll('input[required]');
        inputs.forEach(input => {
            input.addEventListener('focus', function() {
                this.style.borderColor = 'var(--primary)';
            });

            input.addEventListener('blur', function() {
                this.style.borderColor = 'var(--border)';
            });
        });
    }

    // Password matching validation on form submission
    if (form && passwordInput && confirmPasswordInput) {
        form.addEventListener('submit', function(event) {
            const passwordValue = passwordInput.value;
            const confirmPasswordValue = confirmPasswordInput.value;

            if (passwordValue !== confirmPasswordValue) {
                // Stop the form from submitting
                event.preventDefault();

                // Style the confirm password field to indicate an error
                confirmPasswordInput.style.borderColor = 'var(--error)';
                confirmPasswordInput.focus();

                // Create or show the alert message
                let alertContainer = document.querySelector('.alert-error');
                if (!alertContainer) {
                    alertContainer = document.createElement('div');
                    alertContainer.className = 'alert alert-error';
                    alertContainer.style.marginBottom = '16px';
                    alertContainer.innerHTML = `
                        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                            <circle cx="12" cy="12" r="10"></circle>
                            <line x1="12" y1="8" x2="12" y2="12"></line>
                            <line x1="12" y1="16" x2="12.01" y2="16"></line>
                        </svg>
                        <span>Passwords do not match. Please try again.</span>
                    `;
                    form.insertBefore(alertContainer, form.firstChild);
                }
            }
        });

        // Real-time cleanup when the user fixes the mismatch
        confirmPasswordInput.addEventListener('input', function() {
            if (this.value === passwordInput.value) {
                this.style.borderColor = 'var(--primary)';
                const existingAlert = document.querySelector('.alert-error');
                if (existingAlert) {
                    existingAlert.remove();
                }
            }
        });
    }
});