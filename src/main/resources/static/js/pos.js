/* POS helpers (SCRUM-44). Plain JavaScript, no libraries.
   A keyboard-wedge barcode scanner types the barcode and presses Enter, so the
   barcode field must always have focus. The page also works without this file. */
(function () {
    'use strict';

    var field = document.querySelector('[data-autofocus]');
    if (!field) { return; }

    field.focus();
    if (field.select) { field.select(); }

    // Clicking empty space gives the focus back to the scan field.
    document.addEventListener('click', function (event) {
        if (!event.target.closest('a, button, input, select, textarea, summary, label')) {
            field.focus();
        }
    });

    // So does coming back to the window or tab.
    window.addEventListener('focus', function () {
        var active = document.activeElement;
        if (!active || active === document.body) { field.focus(); }
    });
}());
